package com.projecthero.mod.quirk;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: the Quirk core API -- state, cooldowns, the meter, overload, grant / revoke, the tick. Kits only use this
 * (and their own {@code vals} / {@code vecs}); they never touch {@link QuirkState}'s codec or the attachment.
 *
 * <p><b>The meter:</b> the server keeps the authoritative value in a map ({@link #meter} / {@link #setMeter} /
 * {@link #addMeter}, cheap to call every tick) and the core copies it into the synced state a few times a second, so
 * the client HUD reads {@link #clientMeter}. Its meaning is the kit's (Heat rises with use, Dry Eyes drains...).
 *
 * <p><b>Erased</b> ({@link PowerSuppression}): the kit's {@link QuirkKit#stopAll} runs once and its tick is skipped
 * until the stare breaks; the keys refuse.
 */
public final class Quirks {
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> METER = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> METER_SYNCED_AT = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> WAS_SUPPRESSED = new ConcurrentHashMap<>();

	private Quirks() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		METER.clear();
		METER_SYNCED_AT.clear();
		WAS_SUPPRESSED.clear();
		QuirkAbilityManager.clearSessionState();
		QuirkKits.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static QuirkState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.QUIRK_STATE);
	}

	/** Client-safe read (null if never attached). */
	public static @Nullable QuirkState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.QUIRK_STATE, null);
	}

	public static void save(ServerPlayer player, QuirkState state) {
		player.setAttached(ModAttachments.QUIRK_STATE, state);
	}

	/** Copy, change, save. */
	public static void edit(ServerPlayer player, Consumer<QuirkState> change) {
		QuirkState n = state(player).copy();
		change.accept(n);
		save(player, n);
	}

	/** Client-safe: holds any Quirk. */
	public static boolean hasPower(Player player) {
		QuirkState s = peek(player);
		return s != null && s.hasPower && Quirk.byId(s.quirk) != null;
	}

	/** Client-safe: which Quirk (null without the power). */
	public static @Nullable Quirk quirk(Player player) {
		QuirkState s = peek(player);
		return s == null || !s.hasPower ? null : Quirk.byId(s.quirk);
	}

	/** Client-safe: holds this particular Quirk. */
	public static boolean holds(Player player, Quirk quirk) {
		return quirk(player) == quirk;
	}

	/** Client-safe: holds this Quirk and it is not Erased right now (passives / visuals should check this). */
	public static boolean activeAs(Player player, Quirk quirk) {
		return holds(player, quirk) && !PowerSuppression.isSuppressed(player);
	}

	/** Client-safe: the costume is drawn. */
	public static boolean costumeOn(Player player) {
		QuirkState s = peek(player);
		return s != null && s.hasPower && s.costume;
	}

	/** Client-safe: ticks until a move is ready (0 = ready). */
	public static int cooldownRemaining(Player player, String abilityId) {
		QuirkState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	public static boolean ready(ServerPlayer player, String abilityId) {
		return cooldownRemaining(player, abilityId) <= 0;
	}

	public static void setCooldown(ServerPlayer player, String abilityId, int ticks) {
		long at = player.level().getGameTime() + ticks;
		edit(player, n -> n.abilityReadyAt.put(abilityId, at));
	}

	public static void setAnim(ServerPlayer player, int animId) {
		long now = player.level().getGameTime();
		edit(player, n -> {
			n.animId = animId;
			n.animStart = now;
		});
	}

	public static void setVal(ServerPlayer player, String key, long value) {
		edit(player, n -> {
			if (value == 0L) {
				n.vals.remove(key);
			} else {
				n.vals.put(key, value);
			}
		});
	}

	public static void setVec(ServerPlayer player, String key, @Nullable Vec3 v) {
		edit(player, n -> {
			if (v == null) {
				n.vecs.remove(key);
			} else {
				n.vecs.put(key, List.of(v.x, v.y, v.z));
			}
		});
	}

	/** Client-safe helper: the 3-vector, or null. */
	public static @Nullable Vec3 vec(QuirkState s, String key) {
		List<Double> v = s.vec(key);
		return v.size() < 3 ? null : new Vec3(v.get(0), v.get(1), v.get(2));
	}

	// ---------------------------------------------------------------- the meter / overload

	/** Server: the meter, 0..100. */
	public static float meter(ServerPlayer player) {
		Float m = METER.get(player.getUUID());
		if (m == null) {
			m = state(player).meter;
			METER.put(player.getUUID(), m);
		}
		return m;
	}

	public static void setMeter(ServerPlayer player, float value) {
		METER.put(player.getUUID(), Mth.clamp(value, 0f, 100f));
	}

	/** Adds (or with a negative amount, takes) and returns the new value. */
	public static float addMeter(ServerPlayer player, float amount) {
		float v = Mth.clamp(meter(player) + amount, 0f, 100f);
		METER.put(player.getUUID(), v);
		return v;
	}

	/** Client-safe: the synced meter (a few ticks behind the server). */
	public static float clientMeter(Player player) {
		QuirkState s = peek(player);
		return s == null ? 0f : s.meter;
	}

	/** Overload (overheat / stall / arm strain / berserk / dry eyes / nausea) for {@code ticks}; stops the kit's moves. */
	public static void overload(ServerPlayer player, int ticks) {
		long end = player.level().getGameTime() + ticks;
		QuirkKits.of(quirk(player)).stopAll(player);
		edit(player, n -> n.overloadUntil = Math.max(n.overloadUntil, end));
	}

	/** Client-safe. */
	public static boolean overloaded(Player player) {
		QuirkState s = peek(player);
		return s != null && s.overloadUntil > player.level().getGameTime();
	}

	/** Client-safe: ticks of overload left. */
	public static int overloadLeft(Player player) {
		QuirkState s = peek(player);
		return s == null ? 0 : (int) Math.max(0L, s.overloadUntil - player.level().getGameTime());
	}

	private static void flushMeter(ServerPlayer player, QuirkState s, long now) {
		Float m = METER.get(player.getUUID());
		if (m == null || m == s.meter) {
			return;
		}
		boolean edge = m <= 0f || m >= 100f;
		Long last = METER_SYNCED_AT.get(player.getUUID());
		boolean due = last == null || now - last >= QuirkConfig.METER_SYNC_TICKS || now < last;
		if (edge || (due && Math.abs(m - s.meter) >= QuirkConfig.METER_SYNC_STEP)) {
			METER_SYNCED_AT.put(player.getUUID(), now);
			float v = m;
			edit(player, n -> n.meter = v);
		}
	}

	/** Action-bar message, rate limited. */
	public static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	public static Component quirkName(Quirk q) {
		return Component.translatable("projecthero.quirk." + q.id);
	}

	public static Component heroName(Quirk q) {
		return Component.translatable("projecthero.quirk." + q.id + ".hero");
	}

	// ---------------------------------------------------------------- grant / revoke

	/** A serum / the calling / the command. False if refused (already holds this Quirk, UniquePowers). */
	public static boolean grant(ServerPlayer player, Quirk quirk) {
		QuirkState s = state(player);
		if (s.hasPower && quirk.id.equals(s.quirk)) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, quirk.heroKey())) {
			return false;
		}
		if (s.hasPower) {
			revoke(player);
		}
		HeroTiers.claimPrimary(player, quirk.heroKey());
		QuirkState n = new QuirkState();
		n.hasPower = true;
		n.quirk = quirk.id;
		save(player, n);
		METER.remove(player.getUUID());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.quirk.granted", quirkName(quirk), heroName(quirk))
				.withStyle(ChatFormatting.GOLD), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		QuirkState s = state(player);
		if (s.hasPower) {
			QuirkKits.of(Quirk.byId(s.quirk)).stopAll(player);
		}
		save(player, new QuirkState());
		forget(player);
	}

	private static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		METER.remove(id);
		METER_SYNCED_AT.remove(id);
		WAS_SUPPRESSED.remove(id);
		QuirkAbilityManager.forget(id);
		QuirkKits.forget(id);
	}

	/** {@code /projecthero quirk costume}: costume on / off. Returns the new value. */
	public static boolean toggleCostume(ServerPlayer player) {
		boolean on = !state(player).costume;
		edit(player, n -> n.costume = on);
		return on;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		QuirkState s = player.getAttachedOrElse(ModAttachments.QUIRK_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		QuirkKit kit = QuirkKits.of(Quirk.byId(s.quirk));
		boolean erased = PowerSuppression.isSuppressed(player);
		boolean was = Boolean.TRUE.equals(WAS_SUPPRESSED.put(player.getUUID(), erased));
		if (erased) {
			if (!was) {
				kit.stopAll(player);
			}
		} else {
			kit.tick(player);
		}
		QuirkState after = state(player);
		if (after.overloadUntil > 0L && now >= after.overloadUntil) {
			edit(player, n -> n.overloadUntil = 0L);
			after = state(player);
		}
		flushMeter(player, after, now);
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		QuirkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		edit(player, n -> n.animId = QuirkState.ANIM_NONE);
	}

	/** Respawn: the meter, the cooldowns, the overload and the kit's values reset (the Quirk itself stays). */
	public static void onPlayerRespawn(ServerPlayer player) {
		QuirkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		METER.remove(player.getUUID());
		edit(player, n -> {
			n.meter = 0f;
			n.overloadUntil = 0L;
			n.animId = QuirkState.ANIM_NONE;
			n.abilityReadyAt.clear();
			n.vals.clear();
			n.vecs.clear();
		});
	}

	/** Death / logout / dimension change: running moves end (the power stays). */
	public static void clearTransient(ServerPlayer player) {
		QuirkState s = player.getAttachedOrElse(ModAttachments.QUIRK_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		QuirkKits.of(Quirk.byId(s.quirk)).stopAll(player);
		if (!s.vecs.isEmpty()) {
			edit(player, n -> n.vecs.clear());
		}
		forget(player);
	}
}
