package com.projecthero.mod.allmight;

/**
 * Every All Might / One For All tunable in one place (v0.12.33) -- resource, abilities, forms, Full Cowl,
 * landing impacts and visuals. Nothing else in the package hard-codes a balance number. All times are game ticks
 * (20 per second); all distances are blocks.
 */
public final class AllMightConfig {
	private AllMightConfig() {
	}

	private static final int S = 20;

	// ---------------------------------------------------------------- OFA Power (the resource)
	public static final float OFA_MAX = 100.0f;
	/** OFA restored every {@link #OFA_REGEN_INTERVAL_TICKS} ticks. */
	public static final float OFA_REGEN_AMOUNT = 1.0f;
	/** 15 ticks = 1 OFA every 0.75 s while in combat ... */
	public static final int OFA_REGEN_INTERVAL_TICKS = 15;
	/** ... and 8 ticks (2.5x faster) once {@link #OFA_COMBAT_LOCKOUT_TICKS} have passed without dealing/taking damage. */
	public static final int OFA_REGEN_INTERVAL_OUT_OF_COMBAT_TICKS = 8;
	public static final int OFA_COMBAT_LOCKOUT_TICKS = 5 * S;

	// ---------------------------------------------------------------- forms (v0.12.34: Base Form / Power Form)
	// Base Form is a plain Minecraft player: no bonuses, no passives, no abilities. Everything below is the Power Form (H).

	/** Power Form melee damage: a player's base 1 plus 12 = 13. */
	public static final double FULL_ATTACK_BONUS = 12.0;
	/** Power Form max health: 20 + 20 = 40. The current health keeps its percentage across the change. */
	public static final double FULL_HEALTH_BONUS = 20.0;
	/** Damage taken is multiplied by (1 - this) in the Power Form -- 50% less from everything. */
	public static final float FULL_DAMAGE_REDUCTION = 0.50f;
	/** Speed III (amplifier 2). */
	public static final int FULL_SPEED_AMPLIFIER = 2;
	/** Regeneration I (amplifier 0). */
	public static final int FULL_REGEN_AMPLIFIER = 0;
	/** How high a jump carries him, in blocks (a vanilla jump is about 1.25). */
	public static final double FULL_JUMP_BLOCKS = 3.0;
	/** The Power Form is 1.5x as tall: 1.8 -> 2.7 blocks. Added to Attributes.SCALE and eased in / out over {@link #GROWTH_TICKS}. */
	public static final double FULL_SCALE_BONUS = 0.5;
	public static final int GROWTH_TICKS = 20;

	/** H: the damage-proof, ability-locked transformation window (the one second he grows). */
	public static final int TRANSFORM_TICKS = 20;
	/** Changing back takes the same second (he shrinks). */
	public static final int DETRANSFORM_TICKS = 20;
	/** Minimum gap between two H presses. */
	public static final int FORM_TOGGLE_DEBOUNCE_TICKS = 8;

	// ---------------------------------------------------------------- Plus Ultra (C)
	/** While Plus Ultra is on, OFA drains this much every {@link #PLUS_ULTRA_DRAIN_INTERVAL_TICKS} ticks (5 a second). */
	public static final float PLUS_ULTRA_DRAIN_AMOUNT = 1.0f;
	public static final int PLUS_ULTRA_DRAIN_INTERVAL_TICKS = 4;
	/** Cooldown that starts when Plus Ultra is switched off (or runs out of OFA). */
	public static final int PLUS_ULTRA_COOLDOWN_TICKS = 20 * S;
	/** Every Smash / ability deals this much more damage while it is on (+30%). */
	public static final float PLUS_ULTRA_MULTIPLIER = 1.30f;
	/** Below this much OFA he starts venting steam, showing he is running out of power. */
	public static final float LOW_OFA_STEAM = 30.0f;

	// ---------------------------------------------------------------- Smashes
	// Detroit Smash (R): a devastating close-range punch
	public static final int DETROIT_COST = 10;
	public static final int DETROIT_COOLDOWN = 3 * S;
	public static final int DETROIT_WINDUP = 6;
	public static final float DETROIT_DAMAGE = 18.0f;
	public static final double DETROIT_RANGE = 5.0;
	public static final double DETROIT_WIDTH = 3.0;
	public static final double DETROIT_HEIGHT = 3.0;
	public static final double DETROIT_KNOCKBACK = 2.2;
	public static final double DETROIT_LIFT = 0.45;

	// Texas Smash (G): a wide travelling air blast
	public static final int TEXAS_COST = 15;
	public static final int TEXAS_COOLDOWN = 6 * S;
	public static final int TEXAS_WINDUP = 8;
	public static final float TEXAS_DAMAGE = 24.0f;
	public static final double TEXAS_RANGE = 10.0;
	public static final double TEXAS_WIDTH = 5.0;
	public static final double TEXAS_HEIGHT = 4.0;
	public static final double TEXAS_KNOCKBACK = 3.0;
	public static final double TEXAS_LIFT = 0.5;
	/** The wave advances this many blocks per tick (10 blocks take 5 ticks). */
	public static final double TEXAS_WAVE_SPEED = 2.0;

	// Carolina Smash (V): a high-speed offensive dash
	public static final int CAROLINA_COST = 20;
	public static final int CAROLINA_COOLDOWN = 5 * S;
	public static final int CAROLINA_WINDUP = 5;
	public static final float CAROLINA_DAMAGE = 20.0f;
	public static final double CAROLINA_DISTANCE = 26.0;
	public static final double CAROLINA_SPEED = 1.5;
	public static final double CAROLINA_KNOCKBACK = 2.4;

	// New Hampshire Smash (Shift+R): an aerial launch and a crashing landing
	public static final int NEW_HAMPSHIRE_COST = 25;
	public static final int NEW_HAMPSHIRE_COOLDOWN = 8 * S;
	public static final int NEW_HAMPSHIRE_WINDUP = 6;
	public static final float NEW_HAMPSHIRE_DAMAGE = 20.0f;
	/** Launch apex height above the take-off point (12-18 blocks). */
	public static final double NEW_HAMPSHIRE_HEIGHT = 15.0;
	public static final double NEW_HAMPSHIRE_FORWARD_SPEED = 0.9;
	public static final double NEW_HAMPSHIRE_LANDING_RADIUS = 6.0;
	public static final double NEW_HAMPSHIRE_KNOCKBACK = 2.6;

	// United States of Smash (Z, hold 5 s): the ultimate
	public static final int UNITED_STATES_COST = 100;
	public static final int UNITED_STATES_COOLDOWN = 60 * S;
	/** Hold Z this long: the aura grows and the wind builds; the punch lands the moment the charge completes. */
	public static final int UNITED_STATES_CHARGE_TICKS = 5 * S;
	/** The punch pose starts this many ticks before the hit (the rest of the charge is aura and wind only). */
	public static final int UNITED_STATES_POSE_LEAD = 30;
	public static final float UNITED_STATES_DAMAGE = 75.0f;
	public static final double UNITED_STATES_RANGE = 15.0;
	public static final double UNITED_STATES_WIDTH = 8.0;
	public static final double UNITED_STATES_HEIGHT = 8.0;
	public static final double UNITED_STATES_KNOCKBACK = 5.0;
	public static final double UNITED_STATES_LIFT = 0.9;
	/** The larger surrounding wave: radius and the damage it does (a fraction of the primary). */
	public static final double UNITED_STATES_SECONDARY_RANGE = 25.0;
	public static final float UNITED_STATES_SECONDARY_DAMAGE_FRACTION = 0.3f;
	public static final double UNITED_STATES_SECONDARY_KNOCKBACK = 3.0;
	/** Ticks between the primary punch and the secondary wave beginning to expand. */
	public static final int UNITED_STATES_SECONDARY_DELAY = 6;
	public static final int UNITED_STATES_SECONDARY_EXPAND_TICKS = 10;

	// Leap (X) -- a utility ability: the mod has no reusable enhanced-leap for this kit, and the Smashes are all attacks
	public static final int LEAP_COST = 5;
	public static final int LEAP_COOLDOWN = 5 * S;
	public static final double LEAP_HEIGHT = 17.0;
	public static final double LEAP_FORWARD_SPEED = 0.7;

	// Every Smash is locked out for this long after another starts (except the ones that set their own, longer window)
	public static final int GLOBAL_LOCK_TICKS = 6;

	// ---------------------------------------------------------------- bosses
	/** A boss (max health >= this, or a Wither / Ender Dragon) loses at most this fraction of its max health per Smash hit and is not knocked back. */
	public static final float BOSS_HEALTH_THRESHOLD = 300.0f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- passive punches
	public static final double PUNCH_KNOCKBACK = 1.2;
	public static final double SPRINT_PUNCH_KNOCKBACK = 1.9;

	// ---------------------------------------------------------------- landing impacts (fall distance in blocks)
	public static final float LANDING_SMALL_MIN_FALL = 6.0f;
	public static final float LANDING_MEDIUM_MIN_FALL = 12.0f;
	public static final float LANDING_HEAVY_MIN_FALL = 20.0f;
	public static final float LANDING_SMALL_DAMAGE = 2.0f;
	public static final float LANDING_MEDIUM_DAMAGE = 8.0f;
	public static final float LANDING_HEAVY_DAMAGE = 20.0f;
	public static final double LANDING_SMALL_RADIUS = 2.0;
	public static final double LANDING_MEDIUM_RADIUS = 4.0;
	public static final double LANDING_HEAVY_RADIUS = 7.0;
	/** Fall damage is fully cancelled for this long after any of his own launches (Leap, New Hampshire, Carolina). */
	public static final int LAUNCH_NO_FALL_TICKS = 15 * S;

	// ---------------------------------------------------------------- environment (block destruction)
	/** Master switch. Also requires the server's normal ability-griefing rule ({@code AbilityHelpers.canGrief}). */
	public static final boolean BLOCK_DESTRUCTION = true;
	/** Blocks harder than this are never broken (stone 1.5, obsidian 50; bedrock and other unbreakables are -1 and always excluded). */
	public static final float DETROIT_BLOCK_HARDNESS = 2.0f;
	public static final double DETROIT_BLOCK_RADIUS = 1.6;
	public static final int DETROIT_BLOCK_MAX = 12;
	public static final float TEXAS_BLOCK_HARDNESS = 2.0f;
	public static final double TEXAS_BLOCK_RADIUS = 2.6;
	public static final int TEXAS_BLOCK_MAX = 30;
	public static final float CAROLINA_BLOCK_HARDNESS = 1.0f;
	public static final double CAROLINA_BLOCK_RADIUS = 1.2;
	public static final int CAROLINA_BLOCK_MAX = 4;
	public static final float NEW_HAMPSHIRE_BLOCK_HARDNESS = 2.0f;
	public static final double NEW_HAMPSHIRE_BLOCK_RADIUS = 3.2;
	public static final int NEW_HAMPSHIRE_BLOCK_MAX = 36;
	public static final float UNITED_STATES_BLOCK_HARDNESS = 5.0f;
	public static final double UNITED_STATES_BLOCK_RADIUS = 9.0;
	public static final int UNITED_STATES_BLOCK_MAX = 360;

	// ---------------------------------------------------------------- visuals
	/** Multiplies every particle count (1.0 = default; 0 turns the particles off). */
	public static final float PARTICLE_INTENSITY = 1.0f;
	/** Camera shake: radius in blocks within which players feel the big impacts. */
	public static final double SHAKE_RADIUS = 40.0;
	public static final boolean SCREEN_SHAKE = true;
}
