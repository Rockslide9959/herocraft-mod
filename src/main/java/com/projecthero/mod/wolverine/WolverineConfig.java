package com.projecthero.mod.wolverine;

/**
 * Every Wolverine tunable in one place (v0.12.1) -- damage, cooldowns, ranges, regeneration tiers,
 * resistances. Nothing else in the package hard-codes a balance number. All times are game ticks.
 */
public final class WolverineConfig {
	private WolverineConfig() {
	}

	private static final int S = 20;

	// ---- healing factor (extends Super Regeneration's base rate: 1 HP / 0.25 s = 4 HP/s) ----
	/** Multiples of Super Regeneration's calm base heal: 4 / 8 / 12 HP per second. */
	public static final float REGEN_MULT_NORMAL = 1.0f;
	public static final float REGEN_MULT_INJURED = 2.0f;
	public static final float REGEN_MULT_CRITICAL = 3.0f;
	public static final float INJURED_BELOW = 0.50f;
	public static final float CRITICAL_BELOW = 0.25f;
	/** Berserker Rage adds this much to the regeneration multiplier (+100%). */
	public static final float RAGE_REGEN_BONUS = 1.0f;

	// ---- emergency healing ----
	public static final float EMERGENCY_BELOW = 0.15f;
	public static final float EMERGENCY_HEAL_FRACTION = 0.30f;
	public static final int EMERGENCY_HEAL_TICKS = 2 * S;
	public static final int EMERGENCY_COOLDOWN_TICKS = 60 * S;

	// ---- survivability ----
	public static final float DAMAGE_REDUCTION = 0.35f;
	public static final float RAGE_DAMAGE_REDUCTION = 0.20f;
	public static final float FALL_REDUCTION = 0.75f;
	public static final double KNOCKBACK_RESISTANCE = 0.75;
	/** Added on top during Rage and Claw Dash -- the attribute is clamped to 1.0 (total immunity). */
	public static final double KNOCKBACK_RESISTANCE_BONUS = 0.25;
	/** Poison / Wither duration multiplier (strong resistance: they last a quarter as long). */
	public static final float POISON_WITHER_DURATION_FACTOR = 0.25f;

	// ---- physique ----
	public static final double MELEE_BONUS_DAMAGE = 8.0;
	/** Bare-hand melee is 1 + 8 + this = 12 with the claws out (base claw damage 12). */
	public static final double CLAW_MELEE_BONUS = 3.0;
	public static final double SPEED_BONUS = 0.20;
	public static final double JUMP_BONUS = 0.25;
	/** Passive strength mining bonus; claws add {@link #CLAW_MINING_BONUS} on top (1.5x total). */
	public static final double STRENGTH_MINING_BONUS = 0.20;
	public static final double CLAW_MINING_BONUS = 0.30;

	// ---- enhanced senses ----
	/** Hostile mobs inside this many blocks are outlined for the Wolverine's own client only. */
	public static final double SENSE_RADIUS = 12.0;

	// ---- Ability 1: Claw Slash (R) ----
	public static final float SLASH_DAMAGE = 18.0f;
	public static final double SLASH_RANGE = 3.5;
	public static final int SLASH_COOLDOWN = 30; // 1.5 s

	// ---- Ability 2: Cross Slash (G) ----
	public static final float CROSS_DAMAGE_EACH = 15.0f;
	public static final double CROSS_RANGE = 3.0;
	public static final int CROSS_GAP_TICKS = 5;
	public static final int CROSS_COOLDOWN = 4 * S;

	// ---- Ability 3: Claw Dash (Z) ----
	public static final double DASH_BLOCKS = 7.0;
	public static final float DASH_DAMAGE = 24.0f;
	public static final int DASH_MAX_TICKS = 14;
	public static final double DASH_HIT_RADIUS = 2.0;
	public static final int DASH_COOLDOWN = 6 * S;

	// ---- Ability 4: Berserker Rage (X) ----
	public static final int RAGE_TICKS = 12 * S;
	public static final int RAGE_COOLDOWN = 45 * S;
	public static final double RAGE_DAMAGE_BONUS = 0.50;
	public static final double RAGE_SPEED_BONUS = 0.30;

	// ---- Ability 5: Frenzy (C) ----
	public static final int FRENZY_STRIKES = 5;
	public static final float FRENZY_DAMAGE = 8.0f;
	public static final double FRENZY_RANGE = 4.0;
	public static final int FRENZY_INTERVAL_TICKS = 4; // ~0.2 s
	public static final int FRENZY_COOLDOWN = 15 * S;

	// ---- Ability 6: Adamantium Execution (V) ----
	public static final float EXECUTION_DAMAGE = 60.0f;
	public static final double EXECUTION_RANGE = 4.0;
	public static final int EXECUTION_WINDUP_TICKS = 8;
	public static final double EXECUTION_LUNGE_BLOCKS = 3.0;
	public static final int EXECUTION_COOLDOWN = 30 * S;
	/** Cooldown when the strike finds nothing to hit -- no full 30 s penalty for a whiff. */
	public static final int EXECUTION_MISS_COOLDOWN = 8 * S;

	/** Claw toggle spam guard. */
	public static final int TOGGLE_COOLDOWN = 8;
}
