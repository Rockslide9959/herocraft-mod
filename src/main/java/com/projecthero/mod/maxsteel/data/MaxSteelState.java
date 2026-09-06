package com.projecthero.mod.maxsteel.data;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.maxsteel.MaxSteelMode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The entire Max Steel Hero-Tier power for one player, in one isolated namespaced attachment
 * ({@code projecthero:max_steel_state}). Isolated from every Thor / Tony Stark / Spider-Man /
 * experimental store exactly like those are isolated from each other.
 *
 * <p>Persistent and {@code copyOnDeath()} (the power and its long cooldowns must survive death and
 * relog), and synced to <em>everyone</em> -- other players' clients need {@link #transformed} /
 * {@link #mode} / {@link #transformStartTick} to render a transformed Max Steel and its pixel
 * suit-up, the same reason {@code TonyStarkState} and {@code SpiderManState} sync to all. The server
 * stays authoritative: every gameplay check runs against the server-side copy, so a client seeing
 * this data cannot fake the power, the energy or a mode.
 *
 * <p><b>Sync budget.</b> The mutators in {@link com.projecthero.mod.maxsteel.MaxSteel} only re-save when
 * a viewer-visible value changed; {@link #turboEnergy} is written at whole-point granularity (the HUD
 * draws whole points) so a 4/sec regen does not put a packet on the wire every tick.
 *
 * <p>Codec field order is load-bearing (12 of the 16 {@code RecordCodecBuilder} slots) -- do not
 * reorder.
 */
public final class MaxSteelState {
	/** {@link #transformDir} values. */
	public static final int DIR_IDLE = 0;
	public static final int DIR_SUITING_UP = 1;
	public static final int DIR_SUITING_DOWN = 2;

	/** Permanent Hero-Tier power flag -- set once Steel has bonded. */
	public boolean hasPower;
	/** T.U.R.B.O. Energy, 0..{@link com.projecthero.mod.maxsteel.MaxSteelConfig#MAX_TURBO_ENERGY}. */
	public float turboEnergy = com.projecthero.mod.maxsteel.MaxSteelConfig.MAX_TURBO_ENERGY;
	/** Whether the Max Steel suit is on (or actively forming / retracting). */
	public boolean transformed;
	/** The suit's current configuration ({@link MaxSteelMode} ordinal). */
	public int mode = MaxSteelMode.BASE.ordinal();
	/** {@link #DIR_IDLE} / {@link #DIR_SUITING_UP} / {@link #DIR_SUITING_DOWN} -- drives the reveal. */
	public int transformDir = DIR_IDLE;
	/** Absolute game-time the current suit-up / suit-down / mode-swap animation started. */
	public long transformStartTick;
	/** Duration in ticks of the current transform animation (normal suit-up vs. the long first bond). */
	public int transformDurationTicks = com.projecthero.mod.maxsteel.MaxSteelConfig.TRANSFORM_TICKS;
	/** {@code abilityId} -> absolute game-time it is ready again (survives relog/death/dimension). */
	public final Map<String, Long> abilityReadyAt;
	/** Absolute game-time the active specialised mode hits its maximum duration and force-ends. */
	public long modeEndsAt;
	/** Absolute game-time specialised modes become available again (0-energy overload lock-out). */
	public long lockoutUntil;
	/** Absolute game-time of the last high-cost spend -- regeneration waits {@code HIGH_COST_REGEN_DELAY} after it. */
	public long lastHighCostTick;
	/** Absolute game-time the player last gave or took damage -- gates combat-rate regen. */
	public long combatUntil;
	/** Absolute game-time until which a landing takes no fall damage (granted when flight ends). */
	public long fallGraceUntil;
	/**
	 * v0.6.17: a specialised {@link MaxSteelMode} ordinal to drop straight into the moment a suit-up
	 * animation finishes, or -1. Set when a mode key (G/X/Z/V) is pressed while unsuited so the suit
	 * goes up <em>into</em> that mode rather than settling in Base.
	 */
	public int pendingMode = -1;

	public MaxSteelState() {
		this(false, com.projecthero.mod.maxsteel.MaxSteelConfig.MAX_TURBO_ENERGY, false,
				MaxSteelMode.BASE.ordinal(), DIR_IDLE, 0L,
				com.projecthero.mod.maxsteel.MaxSteelConfig.TRANSFORM_TICKS, new HashMap<>(),
				0L, 0L, 0L, 0L, 0L, -1);
	}

	public MaxSteelState(boolean hasPower, float turboEnergy, boolean transformed, int mode, int transformDir,
			long transformStartTick, int transformDurationTicks, Map<String, Long> abilityReadyAt,
			long modeEndsAt, long lockoutUntil, long lastHighCostTick, long combatUntil, long fallGraceUntil,
			int pendingMode) {
		this.hasPower = hasPower;
		this.turboEnergy = turboEnergy;
		this.transformed = transformed;
		this.mode = mode;
		this.transformDir = transformDir;
		this.transformStartTick = transformStartTick;
		this.transformDurationTicks = transformDurationTicks;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.modeEndsAt = modeEndsAt;
		this.lockoutUntil = lockoutUntil;
		this.lastHighCostTick = lastHighCostTick;
		this.combatUntil = combatUntil;
		this.fallGraceUntil = fallGraceUntil;
		this.pendingMode = pendingMode;
	}

	public MaxSteelState copy() {
		return new MaxSteelState(hasPower, turboEnergy, transformed, mode, transformDir, transformStartTick,
				transformDurationTicks, abilityReadyAt, modeEndsAt, lockoutUntil, lastHighCostTick, combatUntil,
				fallGraceUntil, pendingMode);
	}

	public MaxSteelMode modeEnum() {
		return MaxSteelMode.byOrdinal(mode);
	}

	public static final Codec<MaxSteelState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.optionalFieldOf("turbo_energy", com.projecthero.mod.maxsteel.MaxSteelConfig.MAX_TURBO_ENERGY)
					.forGetter(s -> s.turboEnergy),
			Codec.BOOL.optionalFieldOf("transformed", false).forGetter(s -> s.transformed),
			Codec.INT.optionalFieldOf("mode", MaxSteelMode.BASE.ordinal()).forGetter(s -> s.mode),
			Codec.INT.optionalFieldOf("transform_dir", DIR_IDLE).forGetter(s -> s.transformDir),
			Codec.LONG.optionalFieldOf("transform_start_tick", 0L).forGetter(s -> s.transformStartTick),
			Codec.INT.optionalFieldOf("transform_duration_ticks",
					com.projecthero.mod.maxsteel.MaxSteelConfig.TRANSFORM_TICKS).forGetter(s -> s.transformDurationTicks),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("mode_ends_at", 0L).forGetter(s -> s.modeEndsAt),
			Codec.LONG.optionalFieldOf("lockout_until", 0L).forGetter(s -> s.lockoutUntil),
			Codec.LONG.optionalFieldOf("last_high_cost_tick", 0L).forGetter(s -> s.lastHighCostTick),
			Codec.LONG.optionalFieldOf("combat_until", 0L).forGetter(s -> s.combatUntil),
			Codec.LONG.optionalFieldOf("fall_grace_until", 0L).forGetter(s -> s.fallGraceUntil),
			Codec.INT.optionalFieldOf("pending_mode", -1).forGetter(s -> s.pendingMode)
	).apply(instance, MaxSteelState::new));
}
