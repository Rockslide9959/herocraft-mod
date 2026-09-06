package com.projecthero.mod.maxsteel;

/**
 * Every tunable number for the Max Steel Hero-Tier power in one place, so nothing is a magic literal
 * buried in a handler. Grouped by system. All energy figures are in T.U.R.B.O. Energy units (the pool
 * is {@link #MAX_TURBO_ENERGY}); all durations are in ticks (20/sec) unless the field name says
 * otherwise.
 *
 * <p>Sourced from {@code MAX_STEEL_POWER_SPEC.md} and the user's build brief. Where the two disagree
 * the build brief wins (it is the direct instruction) and the difference is noted on the field.
 */
public final class MaxSteelConfig {
	private MaxSteelConfig() {
	}

	// ---------------- T.U.R.B.O. Energy ----------------

	/** v0.6.20: lowered from 500 to 250 -- with the Turbo Modes no longer time-capped, the pool size is
	 *  the real limiter on how long a mode can stay up, and 500 made that effectively unlimited. */
	public static final float MAX_TURBO_ENERGY = 250f;
	/** Regen per second while not draining a specialised mode and out of combat. v0.9.2: 10% slower (was 20). */
	public static final float OUT_OF_COMBAT_REGEN_PER_SEC = 18f;
	/** Regen per second while "in combat" (hurt or dealt damage recently). v0.9.2: 10% slower (was 10). */
	public static final float COMBAT_REGEN_PER_SEC = 9f;
	/** Ticks a hit (given or taken) keeps the player "in combat" for regen purposes. */
	public static final int COMBAT_WINDOW_TICKS = 100;
	/** No regen at all for this long after a high-cost ability. */
	public static final int HIGH_COST_REGEN_DELAY_TICKS = 20;
	/** A spend at or above this fraction of the pool counts as "high cost" and trips the regen delay. */
	public static final float HIGH_COST_FRACTION = 0.15f;
	/**
	 * v0.9.2: an overload (spending the pool to 0 mid-mode) now locks the specialised modes and every
	 * ability until natural regen brings T.U.R.B.O. Energy back up to this level -- not a fixed timer.
	 */
	public static final float OVERLOAD_RECOVER_ENERGY = 150f;
	/** HUD bar flashes below this. */
	public static final float LOW_ENERGY_WARN = 50f;

	// ---------------- emergency totem revive (v0.6.17) ----------------

	/** T.U.R.B.O. Energy spent when the suit auto-revives a downed, unsuited Max Steel. */
	public static final float TOTEM_REVIVE_COST = 150f;
	/** Cooldown on the auto-revive: 20 minutes. */
	public static final int TOTEM_REVIVE_COOLDOWN_TICKS = 20 * 60 * 20;

	// ---------------- transformation ----------------

	/** Normal suit-up / suit-down duration. */
	public static final int TRANSFORM_TICKS = 25; // ~1.25 s
	/** First-ever bonding cut-scene duration. */
	public static final int FIRST_BOND_TICKS = 80; // ~4 s
	/** Hold Ability 1 this long while unsuited to transform without attacking. */
	public static final int HOLD_TO_SUIT_UP_TICKS = 20;
	/** Hold Ability 1 this long while suited + out of combat to suit down. */
	public static final int HOLD_TO_SUIT_DOWN_TICKS = 30;
	/** Short reconfiguration flourish when switching directly between specialised modes. */
	public static final int MODE_SWAP_TICKS = 8;

	// ---------------- base mode passives ----------------

	public static final float BASE_MELEE_BONUS = 4f;
	public static final float BASE_KNOCKBACK_RESIST = 0.25f;
	/** v0.9.2: removed -- Base Mode no longer has a flat incoming-damage reduction, only the suit's
	 *  raw (diamond-tier) armour. Kept as 0 so anything still referencing it is a no-op. */
	public static final float BASE_DAMAGE_REDUCTION = 0f;
	public static final float BASE_FALL_DAMAGE_REDUCTION = 0.50f;
	public static final float BASE_JUMP_BONUS = 0.20f;
	/** Seconds of held breath before normal air depletion begins. */
	public static final int BASE_AIR_SECONDS = 45;

	// ---------------- Ability 1: Turbo Blast ----------------

	public static final float BLAST_DAMAGE = 10f;
	public static final float BLAST_COST = 4f;
	public static final int BLAST_COOLDOWN_TICKS = 9; // 0.45 s
	public static final int BLAST_MAX_CHARGE_TICKS = 30; // 1.5 s
	public static final float CHARGED_BLAST_MAX_DAMAGE = 22f;
	public static final float CHARGED_BLAST_MAX_COST = 16f;
	public static final double CHARGED_BLAST_BURST_RADIUS = 2.5;
	/**
	 * v0.9.4: 4.0 blocks/tick and now held <b>constant</b> the whole flight (the entity re-asserts it
	 * every tick, and inertia/acceleration are neutralised) -- an arrow leaves a bow at ~3.0 and only
	 * slows from there, so a flat 4.0 is unmistakably a fast bolt. (v0.9.3 tried 3.5 with the vanilla
	 * accelerate-from-slow model, which still read as sluggish off the muzzle.)
	 */
	public static final float BLAST_PROJECTILE_SPEED = 4.0f;
	public static final double BLAST_RANGE = 64.0;
	/**
	 * v0.9.2: Turbo Blast is now a real flying projectile ({@code TurboBoltEntity}) rather than an
	 * instant hitscan. This is its (square) hitbox size in blocks at a tap; a full charge inflates it.
	 * "Slightly bigger than a snowball, and thicker" -- the visual bolt trail is scaled to match.
	 */
	public static final float BLAST_PROJECTILE_SIZE = 0.55f;
	public static final float CHARGED_BLAST_PROJECTILE_SIZE = 0.9f;
	/** Ticks the bolt lives before fizzling if it hits nothing (~{@link #BLAST_RANGE} / speed + margin). */
	public static final int BLAST_PROJECTILE_LIFE_TICKS = 18;

	// ---------------- Ability 2: Turbo Strength ----------------

	public static final float STRENGTH_ACTIVATION_COST = 12f;
	public static final float STRENGTH_DRAIN_PER_SEC = 2f;
	/** v0.9.2: +4 on top of the +4 Base Mode bonus -> +8 total unarmed melee in Strength Mode (was +10 -> +14). */
	public static final float STRENGTH_MELEE_BONUS = 4f;
	public static final float STRENGTH_KNOCKBACK_DEALT_BONUS = 0.50f;
	public static final float STRENGTH_KNOCKBACK_RESIST_BONUS = 0.40f;
	/**
	 * v0.9.2: Strength Mode's defence is now a real <b>Resistance I</b> effect plus a shield block --
	 * crouch and you brace, taking only {@code 1 - STRENGTH_SHIELD_BLOCK} of incoming damage. The old
	 * flat 35% reduction in the damage hook is gone.
	 */
	public static final float STRENGTH_SHIELD_BLOCK = 0.50f;
	/** Slam attack (press the Strength Mode key again while in Strength Mode). */
	public static final float STRENGTH_SLAM_DAMAGE = 15f;
	public static final double STRENGTH_SLAM_RADIUS = 5.0;
	public static final float STRENGTH_SLAM_COST = 10f;
	public static final int STRENGTH_SLAM_COOLDOWN_TICKS = 60; // 3 s
	/** v0.6.21: Strength Mode is heavy -- the player moves 20% slower (was 15%). */
	public static final float STRENGTH_SPRINT_PENALTY = 0.20f;
	/** v0.6.21: and swings 20% slower while in Strength Mode. */
	public static final float STRENGTH_ATTACK_SPEED_PENALTY = 0.20f;
	public static final float HEAVY_PUNCH_BONUS_DAMAGE = 8f;
	public static final double HEAVY_PUNCH_SHOCKWAVE_RADIUS = 3.0;
	public static final int HEAVY_PUNCH_COOLDOWN_TICKS = 80; // 4 s

	// ---------------- Ability 3: Turbo Speed ----------------

	public static final float SPEED_ACTIVATION_COST = 10f;
	public static final float SPEED_DRAIN_PER_SEC = 2.5f;
	public static final double SPEED_GROUND_SPEED = 0.42; // blocks/tick target
	public static final float SPEED_FALL_DAMAGE_REDUCTION = 0.60f;
	public static final double TURBO_DASH_DISTANCE = 6.0;
	public static final float TURBO_DASH_DAMAGE = 8f;
	public static final float TURBO_DASH_COST = 6f;
	public static final int TURBO_DASH_COOLDOWN_TICKS = 30; // 1.5 s

	// ---------------- Ability 4: Turbo Flight ----------------

	public static final float FLIGHT_ACTIVATION_COST = 8f;
	public static final float FLIGHT_HOVER_DRAIN_PER_SEC = 1.5f;
	public static final float FLIGHT_NORMAL_DRAIN_PER_SEC = 2f;
	public static final float FLIGHT_BOOST_DRAIN_PER_SEC = 4f;
	/** Fall-damage grace after flight ends. */
	public static final int FLIGHT_LANDING_GRACE_TICKS = 40;

	// ---------------- Ability 5: Turbo Stealth ----------------

	public static final float STEALTH_ACTIVATION_COST = 15f;
	public static final float STEALTH_DRAIN_PER_SEC = 2.5f;
	public static final int STEALTH_COOLDOWN_TICKS = 240; // 12 s
	/** A single hit above this raw amount breaks stealth. */
	public static final float STEALTH_BREAK_DAMAGE = 6f;

	// ---------------- Ability 6: Turbo Cannon ----------------

	/**
	 * v0.9.2: the Turbo Cannon now charges for a full 5 seconds. Instant-cast deals
	 * {@link #CANNON_MIN_DAMAGE}; every extra second held adds {@link #CANNON_PER_SECOND_DAMAGE} up to
	 * the 5-second cap ({@link #CANNON_FULL_DAMAGE}). Energy cost scales the same way -- the longer the
	 * charge, the more of the pool it burns.
	 */
	public static final int CANNON_MAX_CHARGE_TICKS = 100; // 5 s
	public static final float CANNON_MIN_COST = 18f;
	public static final float CANNON_FULL_COST = 60f;
	public static final float CANNON_MIN_DAMAGE = 12f;
	public static final float CANNON_PER_SECOND_DAMAGE = 5f;
	public static final float CANNON_FULL_DAMAGE = 37f; // 12 + 5 * 5
	public static final float CANNON_AOE_DAMAGE = 16f;
	public static final double CANNON_AOE_RADIUS = 4.0;
	public static final int CANNON_COOLDOWN_TICKS = 160; // 8 s
	public static final float CANNON_LAUNCH_SPEED = 2.6f;
	/** Ticks the living-projectile state runs before it force-ends even with no collision. */
	public static final int CANNON_MAX_FLIGHT_TICKS = 40;
}
