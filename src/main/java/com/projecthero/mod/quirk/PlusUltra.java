package com.projecthero.mod.quirk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkCuePayload;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.fx.QuirkSounds;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework: <b>Plus Ultra</b>, the shared ultimate gauge (0..100) of the seven kit Quirks, separate from the
 * kit's own meter. It fills from fighting and fires the Quirk's signature ultimate ({@link QuirkKit#ultimate}) when C is
 * HELD for {@link #HOLD_TICKS} at 100. A C TAP (and Shift+C) is still the kit's normal C move, full gauge or not.
 *
 * <p><b>Pacing</b> (the user: "roughly once per long fight"): about 60-90 s of solid fighting fills it. A landed hit gives
 * {@link #HIT_BASE} + {@link #HIT_PER_HP} per HP dealt (at most {@link #HIT_MAX}, one credit every {@link #HIT_INTERVAL}
 * ticks however many a blast hits); each HP taken gives {@link #TAKEN_PER_HP}; a combo route {@link #COMBO}; saving an ally
 * {@link #ALLY_SAVE} ({@link #add}). After {@link #DECAY_DELAY} ticks with no hit either way it drains
 * {@link #DECAY_PER_SECOND} a second. A reference fight (an 8-damage hit every second, 1 HP taken a second) fills it in
 * about 69 s.
 *
 * <p>State: the server value lives here (cheap to change every hit) and is copied to the synced {@code QuirkState} val
 * {@link #V_GAUGE} (x10) a few times a second, so it is saved with the Quirk and shown on the HUD. The hold is
 * {@link #V_HOLD} (its start time); a fired ultimate stamps {@link #V_FIRED} (the client shows the full-body first-person
 * view and the banner off it).
 */
public final class PlusUltra {
	public static final float MAX = 100f;
	public static final float HIT_BASE = 0.5f;
	public static final float HIT_PER_HP = 0.08f;
	public static final float HIT_MAX = 2.0f;
	public static final int HIT_INTERVAL = 8;
	public static final float TAKEN_PER_HP = 0.3f;
	public static final float COMBO = 3f;
	public static final float ALLY_SAVE = 10f;
	/** Out of combat this long (30 s) before it starts to drain... */
	public static final int DECAY_DELAY = 600;
	/** ...this much a second. */
	public static final float DECAY_PER_SECOND = 1.0f;
	/** C held this long at 100 fires the ultimate. */
	public static final int HOLD_TICKS = 20;

	/** Synced: the gauge x10 (0..1000). */
	public static final String V_GAUGE = "qf_pu";
	/** Synced: the game time the C hold started (0: not holding). */
	public static final String V_HOLD = "qf_pu_hold";
	/** Synced: the game time the last ultimate fired. */
	public static final String V_FIRED = "qf_pu_fired";

	private static final Map<UUID, Float> GAUGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_HIT_CREDIT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> HOLD_START = new ConcurrentHashMap<>();
	/** A fired ultimate swallows the C release that follows it. */
	private static final Map<UUID, Boolean> SWALLOW_RELEASE = new ConcurrentHashMap<>();
	/** Tests: ultimates fired per player. */
	private static final Map<UUID, Integer> FIRED_COUNT = new ConcurrentHashMap<>();

	private PlusUltra() {
	}

	static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> onDamage(entity, source, taken));
	}

	static void clearSessionState() {
		GAUGE.clear();
		LAST_COMBAT.clear();
		LAST_HIT_CREDIT.clear();
		HOLD_START.clear();
		SWALLOW_RELEASE.clear();
		FIRED_COUNT.clear();
	}

	static void forget(UUID id) {
		GAUGE.remove(id);
		LAST_COMBAT.remove(id);
		LAST_HIT_CREDIT.remove(id);
		HOLD_START.remove(id);
		SWALLOW_RELEASE.remove(id);
	}

	/** Holds a Quirk that has a Plus Ultra gauge (the seven kit Quirks; One For All keeps its own engine). */
	public static boolean applies(Player p) {
		Quirk q = Quirks.quirk(p);
		return q != null && !q.standalone && QuirkKits.of(q).hasUltimate();
	}

	// ---------------------------------------------------------------- the gauge

	/** Server: the gauge, 0..100. */
	public static float gauge(ServerPlayer p) {
		Float g = GAUGE.get(p.getUUID());
		if (g == null) {
			g = Quirks.state(p).val(V_GAUGE) / 10f;
			GAUGE.put(p.getUUID(), g);
		}
		return g;
	}

	public static boolean full(ServerPlayer p) {
		return gauge(p) >= MAX;
	}

	public static void set(ServerPlayer p, float value) {
		float before = gauge(p);
		float v = Mth.clamp(value, 0f, MAX);
		GAUGE.put(p.getUUID(), v);
		sync(p, before, v, true);
	}

	/** Adds to the gauge (kits: ally saves, special credits) and marks {@code p} as in combat. Returns the new value. */
	public static float add(ServerPlayer p, float amount) {
		if (!applies(p) || PowerSuppression.isSuppressed(p)) {
			return gauge(p);
		}
		LAST_COMBAT.put(p.getUUID(), p.level().getGameTime());
		float before = gauge(p);
		float v = Mth.clamp(before + amount, 0f, MAX);
		GAUGE.put(p.getUUID(), v);
		sync(p, before, v, false);
		if (before < MAX && v >= MAX) {
			p.playNotifySound(QuirkSounds.ULTRA_READY, SoundSource.PLAYERS, 0.9f, 1.0f);
			Quirks.say(p, "message.projecthero.quirk.plus_ultra.ready", ChatFormatting.GOLD);
		}
		return v;
	}

	private static void sync(ServerPlayer p, float before, float v, boolean force) {
		long want = Math.round(v * 10f);
		long have = Quirks.state(p).val(V_GAUGE);
		boolean edge = (v <= 0f || v >= MAX) && want != have;
		if (force || edge || Math.abs(want - have) >= 5) {
			Quirks.setVal(p, V_GAUGE, want);
		}
	}

	/** Client-safe: the synced gauge, 0..100. */
	public static float clientGauge(Player p) {
		QuirkState s = Quirks.peek(p);
		return s == null ? 0f : Mth.clamp(s.val(V_GAUGE) / 10f, 0f, MAX);
	}

	/** Client-safe: 0..1 through the C hold, or -1 when not holding. */
	public static float clientHold(Player p) {
		QuirkState s = Quirks.peek(p);
		if (s == null || s.val(V_HOLD) == 0L) {
			return -1f;
		}
		return Mth.clamp((p.level().getGameTime() - s.val(V_HOLD)) / (float) HOLD_TICKS, 0f, 1f);
	}

	/** Client-safe: ticks since the last ultimate fired (a large number if never). */
	public static long sinceFired(Player p) {
		QuirkState s = Quirks.peek(p);
		long at = s == null ? 0L : s.val(V_FIRED);
		return at == 0L ? Long.MAX_VALUE / 4 : p.level().getGameTime() - at;
	}

	private static void onDamage(LivingEntity victim, DamageSource source, float taken) {
		if (taken <= 0f) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && !(victim instanceof ArmorStand) && applies(attacker)) {
			hitDealt(attacker, taken);
		}
		if (victim instanceof ServerPlayer p && applies(p) && !QuirkMeters.selfHarm() && source.getEntity() != p) {
			add(p, taken * TAKEN_PER_HP);
		}
	}

	/** A landed hit of {@code damage}: credited once every {@link #HIT_INTERVAL} ticks (a blast that hits ten counts once). */
	public static void hitDealt(ServerPlayer p, float damage) {
		long now = p.level().getGameTime();
		LAST_COMBAT.put(p.getUUID(), now);
		Long last = LAST_HIT_CREDIT.get(p.getUUID());
		if (last != null && now - last < HIT_INTERVAL && now >= last) {
			return;
		}
		LAST_HIT_CREDIT.put(p.getUUID(), now);
		add(p, Math.min(HIT_MAX, HIT_BASE + HIT_PER_HP * damage));
	}

	// ---------------------------------------------------------------- the tick and the C hold

	static void tick(ServerPlayer p) {
		if (!applies(p)) {
			return;
		}
		long now = p.level().getGameTime();
		Long hold = HOLD_START.get(p.getUUID());
		if (hold != null) {
			QuirkKit kit = QuirkKits.of(Quirks.quirk(p));
			if (!full(p) || Quirks.overloaded(p) || PowerSuppression.isSuppressed(p)) {
				cancelHold(p);
			} else if (now - hold >= kit.ultimateHoldTicks()) {
				fire(p);
			} else if ((now - hold) % 5 == 0) {
				Vec3 c = p.position().add(0, 1.0, 0);
				p.serverLevel().sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, 4, 0.5, 0.6, 0.5, 0.05);
			}
		}
		Long last = LAST_COMBAT.get(p.getUUID());
		if (last == null) {
			LAST_COMBAT.put(p.getUUID(), now);
		} else if (now - last > DECAY_DELAY && gauge(p) > 0f && hold == null) {
			float before = gauge(p);
			float v = Math.max(0f, before - DECAY_PER_SECOND / 20f);
			GAUGE.put(p.getUUID(), v);
			sync(p, before, v, false);
		}
	}

	/**
	 * From {@link QuirkAbilityManager} for the C key (SLOT_6) after the erased / overloaded checks: at 100, a plain C press
	 * starts the hold instead of reaching the kit; a release before the hold completes plays the kit's C TAP (press then
	 * release); a completed hold fires the ultimate and swallows the release. Returns true when the key edge was consumed.
	 */
	static boolean interceptC(ServerPlayer p, QuirkKit kit, boolean pressed, boolean shift) {
		UUID id = p.getUUID();
		if (pressed) {
			if (shift || !applies(p) || !full(p)) {
				return false;
			}
			HOLD_START.put(id, p.level().getGameTime());
			Quirks.setVal(p, V_HOLD, p.level().getGameTime());
			QuirkSounds.play(p.serverLevel(), p.position(), QuirkSounds.CHARGE_UP, 0.9f, 1.0f);
			return true;
		}
		if (Boolean.TRUE.equals(SWALLOW_RELEASE.remove(id))) {
			return true;
		}
		if (HOLD_START.containsKey(id)) {
			cancelHold(p);
			// a tap: the kit's normal C move
			kit.handle(p, AbilitySlot.SLOT_6, true, false);
			kit.handle(p, AbilitySlot.SLOT_6, false, false);
			return true;
		}
		return false;
	}

	private static void cancelHold(ServerPlayer p) {
		HOLD_START.remove(p.getUUID());
		if (Quirks.state(p).val(V_HOLD) != 0L) {
			Quirks.setVal(p, V_HOLD, 0L);
		}
	}

	/** The hold completed (or a test / the harness): spend the whole gauge and fire the kit's ultimate. */
	public static void fire(ServerPlayer p) {
		Quirk q = Quirks.quirk(p);
		if (q == null || q.standalone) {
			return;
		}
		cancelHold(p);
		SWALLOW_RELEASE.put(p.getUUID(), true);
		set(p, 0f);
		Quirks.setVal(p, V_FIRED, p.level().getGameTime());
		FIRED_COUNT.merge(p.getUUID(), 1, Integer::sum);
		QuirkKit kit = QuirkKits.of(q);
		if (ServerPlayNetworking.canSend(p, QuirkCuePayload.TYPE)) {
			ServerPlayNetworking.send(p, new QuirkCuePayload(QuirkCuePayload.ULTIMATE, kit.ultimateLangKey(q), q.meterColour & 0xFFFFFF));
		}
		QuirkSounds.play(p.serverLevel(), p.position(), QuirkSounds.ULTIMATE_STING, 1.2f, 1.0f);
		kit.ultimate(p);
	}

	/**
	 * The pacing reference (docs + tests): seconds of "solid fighting" -- an {@code hitDamage} hit landed every
	 * {@code hitEvery} ticks and {@code takenPerSecond} HP taken a second -- to fill the gauge from empty.
	 */
	public static float secondsToFill(float hitDamage, int hitEvery, float takenPerSecond) {
		int every = Math.max(hitEvery, HIT_INTERVAL);
		float perHit = Math.min(HIT_MAX, HIT_BASE + HIT_PER_HP * hitDamage);
		float perSecond = perHit * 20f / every + takenPerSecond * TAKEN_PER_HP;
		return MAX / perSecond;
	}

	/** Tests: how many ultimates {@code p} has fired this session. */
	public static int firedCount(ServerPlayer p) {
		return FIRED_COUNT.getOrDefault(p.getUUID(), 0);
	}

	/** Tests: C is being held toward the ultimate. */
	public static boolean holding(ServerPlayer p) {
		return HOLD_START.containsKey(p.getUUID());
	}

	/** Tests: pretend the last hit either way was {@code ticks} ago. */
	public static void lastCombatForTest(ServerPlayer p, long ticksAgo) {
		LAST_COMBAT.put(p.getUUID(), p.level().getGameTime() - ticksAgo);
	}

	/**
	 * The ultimate every kit gets until it overrides {@link QuirkKit#ultimate} (v0.17.1 framework placeholder, so the gauge,
	 * the hold and the HUD are testable): a white flash and a Quirk-coloured blast at the holder, a shock ring, a strong
	 * camera punch for everyone near and a push on nearby mobs (the "PLUS ULTRA!" banner is already on the owner's HUD). No
	 * damage.
	 */
	public static void placeholderUltimate(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Quirk q = Quirks.quirk(p);
		int colour = q == null ? 0xFFC83A : q.meterColour & 0xFFFFFF;
		Vec3 at = p.position().add(0, 1.0, 0);
		QuirkFx.blast(level, at).colours(0xFFFFFF, colour).size(4.5f).life(18).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, colour).size(9f).thickness(0.8f).life(16).ground().spawn();
		QuirkCamera.preset(p, QuirkCamera.Preset.ULTIMATE);
		QuirkCamera.shakeAround(level, at, 24, 0.5f, 12);
		level.sendParticles(QuirkParticles.SPARK, at.x, at.y, at.z, 40, 0.6, 0.8, 0.6, 0.35);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.0), e -> e != p && e.isAlive())) {
			Vec3 d = e.position().subtract(p.position());
			d = d.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : d.normalize();
			e.push(d.x * 0.9, 0.35, d.z * 0.9);
			e.hurtMarked = true;
		}
		// the "PLUS ULTRA!" shout is the owner's HUD banner (QuirkCuePayload.ULTIMATE); nearby players hear the sting
	}
}
