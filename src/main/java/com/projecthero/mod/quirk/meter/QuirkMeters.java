package com.projecthero.mod.quirk.meter;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkPerks;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.fx.QuirkSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Quirk framework: the registered {@link QuirkMeterSpec}s and everything shared about a meter -- its band, the
 * pacing (the user: "i dont want like heat or darkness or whatever to build up too quick"), the push past 100 and the
 * red-zone warning. Kits change their meter through {@link Quirks#addMeterPaced} (natural build and recovery: paced) or
 * {@link Quirks#addMeter} / {@link Quirks#setMeter} (deliberate costs, vents and resets: raw).
 */
public final class QuirkMeters {
	/** Synced (QuirkState vals): the game time the push past 100 ends, 0 = not pushing. */
	public static final String V_BEYOND = "qf_beyond";

	private static final Map<Quirk, QuirkMeterSpec> SPECS = new EnumMap<>(Quirk.class);
	private static final Map<UUID, Boolean> IN_DANGER = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> SELF_HARM = ThreadLocal.withInitial(() -> false);

	private QuirkMeters() {
	}

	public static void register(QuirkMeterSpec spec) {
		SPECS.put(spec.quirk, spec);
	}

	/** The spec of a Quirk, or null (One For All has none: it has its own Output engine). */
	public static @Nullable QuirkMeterSpec spec(@Nullable Quirk q) {
		return q == null ? null : SPECS.get(q);
	}

	public static Map<Quirk, QuirkMeterSpec> all() {
		return java.util.Collections.unmodifiableMap(SPECS);
	}

	public static void clearSessionState() {
		IN_DANGER.clear();
	}

	public static void forget(UUID id) {
		IN_DANGER.remove(id);
	}

	/** True while the framework's own push-past HP cost is being applied (Plus Ultra does not count it as a hit taken). */
	public static boolean selfHarm() {
		return SELF_HARM.get();
	}

	// ---------------------------------------------------------------- pacing

	/**
	 * A natural change on a channel that builds in {@code dir}, paced: toward the danger x {@code buildMult} (x the suit's
	 * {@code suitBuildMult} in the full set), away from it x {@code recoverMult}. No spec: unchanged.
	 */
	public static float pace(@Nullable QuirkMeterSpec spec, @Nullable Player p, float delta) {
		if (spec == null || delta == 0f) {
			return delta;
		}
		boolean towardDanger = spec.channelDirection == QuirkMeterSpec.Direction.BUILDS ? delta > 0f : delta < 0f;
		if (!towardDanger) {
			return delta * spec.recoverMult;
		}
		float m = spec.buildMult;
		if (p != null && spec.suitBuildMult != 1f && QuirkPerks.fullSet(p)) {
			m *= spec.suitBuildMult;
		}
		return delta * m;
	}

	/** {@link #pace} for whatever Quirk {@code p} holds. */
	public static float pace(Player p, float delta) {
		return pace(spec(Quirks.quirk(p)), p, delta);
	}

	// ---------------------------------------------------------------- bands

	/** Server: the band {@code p}'s meter is in (BEYOND while pushed past 100). */
	public static QuirkBand band(ServerPlayer p) {
		QuirkMeterSpec s = spec(Quirks.quirk(p));
		if (s == null) {
			return QuirkBand.SWEET;
		}
		if (beyond(p)) {
			return QuirkBand.BEYOND;
		}
		return s.bandOf(Quirks.meter(p));
	}

	/** Client-safe: the band of the synced meter. */
	public static QuirkBand clientBand(Player p) {
		QuirkMeterSpec s = spec(Quirks.quirk(p));
		if (s == null) {
			return QuirkBand.SWEET;
		}
		if (beyond(p)) {
			return QuirkBand.BEYOND;
		}
		return s.bandOf(Quirks.clientMeter(p));
	}

	/** Server: the shared damage multiplier of {@code p}'s current band (1 without a spec). */
	public static float damageMult(ServerPlayer p) {
		QuirkMeterSpec s = spec(Quirks.quirk(p));
		return s == null ? 1f : s.damageFor(band(p));
	}

	// ---------------------------------------------------------------- push past 100

	/** Client-safe: pushed past 100 right now. */
	public static boolean beyond(Player p) {
		QuirkState s = Quirks.peek(p);
		return s != null && s.val(V_BEYOND) > p.level().getGameTime();
	}

	/** Client-safe: 0..1 of the push left (1 = just started). */
	public static float beyondLeft(Player p) {
		QuirkState s = Quirks.peek(p);
		QuirkMeterSpec spec = spec(Quirks.quirk(p));
		if (s == null || spec == null || !spec.pushPast) {
			return 0f;
		}
		long left = s.val(V_BEYOND) - p.level().getGameTime();
		return left <= 0 ? 0f : Math.min(1f, left / (float) Math.max(1, spec.pushTicks));
	}

	/**
	 * The kit's "go beyond": at 100, push past it for the spec's {@code pushTicks} (HP drains, the kit's BEYOND band and its
	 * damage apply), then the crash. False if the spec has no push, the meter is under 100, already pushing, or overloaded.
	 */
	public static boolean pushPast(ServerPlayer p) {
		QuirkMeterSpec s = spec(Quirks.quirk(p));
		if (s == null || !s.pushPast || beyond(p) || Quirks.overloaded(p) || Quirks.meter(p) < 100f) {
			return false;
		}
		long until = p.level().getGameTime() + s.pushTicks;
		Quirks.setVal(p, V_BEYOND, until);
		Quirks.say(p, "message.projecthero.quirk.beyond", ChatFormatting.GOLD);
		p.playNotifySound(QuirkSounds.ULTRA_READY, SoundSource.PLAYERS, 0.8f, 0.7f);
		return true;
	}

	/** Every server tick for a kit-Quirk holder (from {@code QuirkFramework.tick}). */
	public static void tick(ServerPlayer p, Quirk q) {
		QuirkMeterSpec s = spec(q);
		if (s == null) {
			return;
		}
		long now = p.level().getGameTime();
		long until = Quirks.state(p).val(V_BEYOND);
		if (until != 0L) {
			if (now < until) {
				Quirks.setMeter(p, 100f);
				if ((until - now) % 20 == 0 && s.pushHpPerSecond > 0f && p.getHealth() > s.pushHpPerSecond + 1f) {
					SELF_HARM.set(true);
					try {
						p.hurt(p.damageSources().magic(), s.pushHpPerSecond);
					} finally {
						SELF_HARM.set(false);
					}
				}
			} else {
				Quirks.setVal(p, V_BEYOND, 0L);
				if (s.onCrash != null) {
					s.onCrash.accept(p);
				} else {
					Quirks.overload(p, s.crashTicks);
				}
			}
		}
		// the red-zone warning: once, on entering the danger zone
		boolean danger = s.dangerous(s.channel.read(p));
		Boolean was = IN_DANGER.put(p.getUUID(), danger);
		if (danger && !Boolean.TRUE.equals(was) && was != null) {
			p.playNotifySound(QuirkSounds.METER_WARNING, SoundSource.PLAYERS, 0.55f, 1.0f);
		}
	}

	/** Test hook: the push ends now. */
	public static void endPushForTest(ServerPlayer p) {
		Quirks.setVal(p, V_BEYOND, p.level().getGameTime());
	}
}
