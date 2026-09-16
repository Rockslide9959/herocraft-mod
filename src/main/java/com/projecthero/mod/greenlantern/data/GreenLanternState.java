package com.projecthero.mod.greenlantern.data;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.ConstructType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The entire Green Lantern Hero-Tier power for one player, in one isolated namespaced attachment
 * ({@code projecthero:green_lantern_state}). Isolated from every other Hero-Tier/experimental store,
 * exactly like {@code MaxSteelState} is.
 *
 * <p>Persistent and {@code copyOnDeath()} -- the power, its Ring Charge and its Mastery progress must
 * survive death and relog. Synced to everyone (other clients need {@link #suited} to render the suit).
 * The server stays authoritative: every gameplay check runs against the server-side copy.
 *
 * <p>Transient combat state (flight, active barrier, construct-wheel-hold gesture) deliberately lives
 * in separate, non-persisted attachments instead of here -- see {@code ModAttachments}' Green Lantern
 * section -- both to keep this codec well under the 16-field {@code RecordCodecBuilder} ceiling
 * (Symbiote's split precedent) and because that state should NOT survive a relog.
 *
 * <p>Codec field order is load-bearing -- do not reorder.
 */
public final class GreenLanternState {
	public static final int SUIT_IDLE = 0;
	public static final int SUIT_SUITING_UP = 1;
	public static final int SUIT_SUITING_DOWN = 2;

	public static final int MASTERY_BONDED = 0;
	public static final int MASTERY_I = 1;
	public static final int MASTERY_II = 2;
	public static final int MASTERY_III = 3;
	public static final int MASTERY_IV = 4;

	/** Permanent Hero-Tier power flag -- set once the ring has bonded. */
	public boolean hasPower;
	/** Ring Charge, 0..{@link GreenLanternConfig#MAX_RING_CHARGE}. */
	public float ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
	/** Whether the suit is on (or actively forming/retracting). */
	public boolean suited;
	/** {@link #SUIT_IDLE}/{@link #SUIT_SUITING_UP}/{@link #SUIT_SUITING_DOWN}. */
	public int suitAnimDir = SUIT_IDLE;
	/** Absolute game-time the current suit-up/suit-down animation started. */
	public long suitAnimStartTick;
	/** {@link ConstructType} ordinal currently selected for the C key. */
	public int selectedConstruct = ConstructType.HARD_LIGHT_WALL.ordinal();
	/** {@link #MASTERY_BONDED}..{@link #MASTERY_IV}. */
	public int masteryLevel = MASTERY_BONDED;
	/** Cumulative Ring Charge ever spent -- drives Mastery I-IV energy thresholds. */
	public long totalEnergySpent;
	/** Cumulative damage absorbed by shields/domes/walls/cages -- Mastery II's second requirement. */
	public float totalDamageBlocked;
	/** Cumulative distance flown via ring flight, in blocks -- Mastery III's second requirement. */
	public double totalFlightDistance;
	/** {@code abilityId} -> absolute game-time it is ready again (survives relog/death/dimension). */
	public final Map<String, Long> abilityReadyAt;
	/** Absolute game-time of the last ring-ability use -- passive regen waits 8s after this. */
	public long lastAbilityUseTick;
	/** Absolute game-time the current night began while bonded (0 = not currently tracking a night). */
	public long nightStartTick;
	/** Mastery I's "survive one full night while bonded" requirement, once satisfied. */
	public boolean nightSurvived;
	/** Mastery IV's "defeat a boss while bonded" requirement, once satisfied. */
	public boolean bossDefeatedWhileBonded;

	public GreenLanternState() {
		this(false, GreenLanternConfig.MAX_RING_CHARGE, false, SUIT_IDLE, 0L,
				ConstructType.HARD_LIGHT_WALL.ordinal(), MASTERY_BONDED, 0L, 0f, 0.0,
				new HashMap<>(), 0L, 0L, false, false);
	}

	public GreenLanternState(boolean hasPower, float ringCharge, boolean suited, int suitAnimDir,
			long suitAnimStartTick, int selectedConstruct, int masteryLevel, long totalEnergySpent,
			float totalDamageBlocked, double totalFlightDistance, Map<String, Long> abilityReadyAt,
			long lastAbilityUseTick, long nightStartTick, boolean nightSurvived, boolean bossDefeatedWhileBonded) {
		this.hasPower = hasPower;
		this.ringCharge = ringCharge;
		this.suited = suited;
		this.suitAnimDir = suitAnimDir;
		this.suitAnimStartTick = suitAnimStartTick;
		this.selectedConstruct = selectedConstruct;
		this.masteryLevel = masteryLevel;
		this.totalEnergySpent = totalEnergySpent;
		this.totalDamageBlocked = totalDamageBlocked;
		this.totalFlightDistance = totalFlightDistance;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.lastAbilityUseTick = lastAbilityUseTick;
		this.nightStartTick = nightStartTick;
		this.nightSurvived = nightSurvived;
		this.bossDefeatedWhileBonded = bossDefeatedWhileBonded;
	}

	public GreenLanternState copy() {
		return new GreenLanternState(hasPower, ringCharge, suited, suitAnimDir, suitAnimStartTick,
				selectedConstruct, masteryLevel, totalEnergySpent, totalDamageBlocked, totalFlightDistance,
				abilityReadyAt, lastAbilityUseTick, nightStartTick, nightSurvived, bossDefeatedWhileBonded);
	}

	public ConstructType selectedConstructType() {
		return ConstructType.byOrdinal(selectedConstruct);
	}

	/** Whether {@code masteryLevel} has unlocked at least {@code level} ({@link #MASTERY_I} etc.). */
	public boolean hasMastery(int level) {
		return masteryLevel >= level;
	}

	public static final Codec<GreenLanternState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("ring_charge", GreenLanternConfig.MAX_RING_CHARGE).forGetter(s -> s.ringCharge),
			Codec.BOOL.optionalFieldOf("suited", false).forGetter(s -> s.suited),
			Codec.INT.optionalFieldOf("suit_anim_dir", SUIT_IDLE).forGetter(s -> s.suitAnimDir),
			Codec.LONG.optionalFieldOf("suit_anim_start_tick", 0L).forGetter(s -> s.suitAnimStartTick),
			Codec.INT.optionalFieldOf("selected_construct", ConstructType.HARD_LIGHT_WALL.ordinal())
					.forGetter(s -> s.selectedConstruct),
			Codec.INT.optionalFieldOf("mastery_level", MASTERY_BONDED).forGetter(s -> s.masteryLevel),
			Codec.LONG.optionalFieldOf("total_energy_spent", 0L).forGetter(s -> s.totalEnergySpent),
			Codec.FLOAT.optionalFieldOf("total_damage_blocked", 0f).forGetter(s -> s.totalDamageBlocked),
			Codec.DOUBLE.optionalFieldOf("total_flight_distance", 0.0).forGetter(s -> s.totalFlightDistance),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("last_ability_use_tick", 0L).forGetter(s -> s.lastAbilityUseTick),
			Codec.LONG.optionalFieldOf("night_start_tick", 0L).forGetter(s -> s.nightStartTick),
			Codec.BOOL.optionalFieldOf("night_survived", false).forGetter(s -> s.nightSurvived),
			Codec.BOOL.optionalFieldOf("boss_defeated_while_bonded", false).forGetter(s -> s.bossDefeatedWhileBonded)
	).apply(instance, GreenLanternState::new));
}
