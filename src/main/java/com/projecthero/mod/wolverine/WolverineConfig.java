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
	/** Multiples of Super Regeneration's calm base heal (4 HP/s): 3 / 6 / 9 HP per second (v0.12.16 nerf). */
	public static final float REGEN_MULT_NORMAL = 0.75f;
	public static final float REGEN_MULT_INJURED = 1.5f;
	public static final float REGEN_MULT_CRITICAL = 2.25f;
	public static final float INJURED_BELOW = 0.50f;
	public static final float CRITICAL_BELOW = 0.25f;
	/** Berserker Rage adds this much to the regeneration multiplier (+100%). */
	public static final float RAGE_REGEN_BONUS = 1.0f;

	// ---- emergency healing ----
	/** Health (fraction of max) he is left at when the death resurrection fires. */
	public static final float EMERGENCY_HEAL_FRACTION = 0.30f;
	/** The resurrection window: no damage, Slowness III + Blindness + Weakness I, flesh model. */
	public static final int EMERGENCY_HEAL_TICKS = 10 * S;
	public static final int EMERGENCY_COOLDOWN_TICKS = 180 * S;
	/** Emergency resurrection look: full flesh for 10 s, then the skin fades back over another 10 s. */
	public static final int FLESH_HOLD_TICKS = 10 * S;
	public static final int FLESH_FADE_TICKS = 10 * S;

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
	public static final double MELEE_BONUS_DAMAGE = 3.0; // bare-hand melee = 1 + 3 = 4
	/** Claws out AND an empty hand: +8 unarmed damage on top (4 + 8 = 12). Not applied while holding an item. */
	public static final double CLAW_MELEE_BONUS = 8.0;
	public static final double SPEED_BONUS = 0.40; // Speed II
	public static final double JUMP_BONUS = 0.30; // ~2.1 blocks: clears a 2-block wall
	/** Passive strength mining bonus; claws add {@link #CLAW_MINING_BONUS} on top (1.5x total). */
	public static final double STRENGTH_MINING_BONUS = 0.20;
	public static final double CLAW_MINING_BONUS = 0.30;

	// ---- enhanced senses ----
	/** Mobs hunting the Wolverine (targeting him) within this many blocks glow orange -- for his client only. */
	public static final double HUNTER_RADIUS = 40.0;
	/** How often the server refreshes his list of hunters. */
	public static final int HUNTER_SCAN_TICKS = 10;
	/** N key -- Sniff: every living thing within this many blocks is highlighted... */
	public static final double SNIFF_RADIUS = 40.0;
	/** ...for this long. No cooldown; only a tiny anti-spam guard. */
	public static final int SNIFF_TICKS = 20 * S;
	public static final int SNIFF_SPAM_GUARD = 10;

	// ---- Ability 1: Claw Slash (R) ----
	public static final float SLASH_DAMAGE = 18.0f;
	public static final double SLASH_RANGE = 4.5;
	public static final int SLASH_COOLDOWN = 30; // 1.5 s

	// ---- Ability 2: Cross Slash (G) ----
	public static final float CROSS_DAMAGE_EACH = 12.0f;
	public static final double CROSS_RANGE = 4.0;
	public static final int CROSS_GAP_TICKS = 5;
	public static final int CROSS_COOLDOWN = 4 * S;

	// ---- Ability 3: Claw Dash (X) ----
	public static final double DASH_BLOCKS = 21.0;
	public static final float DASH_DAMAGE = 18.0f;
	/** Safety cap only -- the dash normally ends the moment the Wolverine lands. */
	public static final int DASH_MAX_TICKS = 30;
	/** How far in front of the Wolverine a seized enemy is held while he drags it along. */
	public static final double DASH_GRAB_DISTANCE = 1.6;
	/** Ticks of the player's velocity the grabbed entity is placed ahead by, to stay in front despite tick order and latency. */
	public static final double DASH_GRAB_LEAD_TICKS = 2.0;
	public static final double DASH_HIT_RADIUS = 2.0;
	public static final int DASH_COOLDOWN = 6 * S;

	// ---- Ability 6: Berserker Rage (C) -- gated by a rage bar, not a cooldown ----
	public static final int RAGE_TICKS = 30 * S;
	public static final float RAGE_BAR_MAX = 100.0f;
	/** Bar points gained per point of damage the Wolverine takes / deals (not gained while raging). */
	/** Bar points (of 100) gained for every hit he lands or takes -- flat, whatever the damage. */
	public static final float RAGE_PER_HIT = 1.0f;
	/** The Rage bar starts draining after this long without dealing or taking damage... */
	public static final int RAGE_DRAIN_DELAY_TICKS = 10 * S;
	/** ...at this many bar points per tick (5% of the bar per second: a full bar empties in 20 s). */
	public static final float RAGE_DRAIN_PER_TICK = 5.0f / S;
	public static final double RAGE_DAMAGE_BONUS = 0.50;
	public static final double RAGE_SPEED_BONUS = 0.30;

	// ---- Ability 5: Frenzy (V) ----
	public static final int FRENZY_STRIKES = 5;
	public static final float FRENZY_DAMAGE = 8.0f;
	public static final double FRENZY_RANGE = 4.0;
	public static final int FRENZY_INTERVAL_TICKS = 4; // ~0.2 s
	public static final int FRENZY_COOLDOWN = 15 * S;

	// ---- Ability 4: Adamantium Execution (Z) -- hold to charge, release to strike ----
	/** Hold the key this long; on release after a full charge the execution fires. */
	public static final int EXECUTION_CHARGE_TICKS = 5 * S;
	/** A charge whose release was never received (GUI opened, etc.) is dropped after this long. */
	public static final int EXECUTION_CHARGE_TIMEOUT = 30 * S;
	public static final float EXECUTION_DAMAGE = 60.0f;
	public static final double EXECUTION_RANGE = 4.0;
	public static final int EXECUTION_WINDUP_TICKS = 8;
	public static final double EXECUTION_LUNGE_BLOCKS = 3.0;
	public static final int EXECUTION_COOLDOWN = 30 * S;
	/** Cooldown when the strike finds nothing to hit -- no full 30 s penalty for a whiff. */
	public static final int EXECUTION_MISS_COOLDOWN = 8 * S;

	/** Right-click claw strike (off hand) reach and swing guard. */
	public static final double OFFHAND_STRIKE_REACH = 3.5;
	public static final int OFFHAND_STRIKE_GUARD = 6;

	/** Claw toggle spam guard. */
	public static final int TOGGLE_COOLDOWN = 8;
}
