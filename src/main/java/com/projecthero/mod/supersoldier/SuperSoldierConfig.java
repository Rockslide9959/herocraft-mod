package com.projecthero.mod.supersoldier;

/**
 * Every Super Soldier tunable (v0.14.8): plain constants, ticks unless the name says otherwise. He is peak human, not a
 * god -- every number here is meant to sit below the Hulk / Thor / All Might.
 */
public final class SuperSoldierConfig {
	private SuperSoldierConfig() {
	}

	// ---------------------------------------------------------------- passives
	/** Movement speed: +50% of base (ADD_MULTIPLIED_BASE). */
	public static final double SPEED_BONUS = 0.5;
	/** +5 hearts. */
	public static final double HEALTH_BONUS = 10.0;
	/** Extra melee damage while the main hand is empty. */
	public static final double UNARMED_BONUS = 7.0;
	/** Damage taken is multiplied by this (30% less). */
	public static final float DAMAGE_TAKEN_FACTOR = 0.7f;
	/** Jump apex height in blocks (vanilla is about 1.25). 2.25 lets him hop up a two-block ledge. */
	public static final double JUMP_BLOCKS = 2.25;
	/** Safe fall distance on top of vanilla's 3 blocks (matches the higher jump). */
	public static final double SAFE_FALL_BONUS = 3.0;
	/** Knockback resistance (0..1). */
	public static final double KNOCKBACK_RESISTANCE = 0.2;
	/** Attack speed: +15% of base. */
	public static final double ATTACK_SPEED_BONUS = 0.15;
	/** Out-of-combat healing: 1 HP every this many ticks, once this long has passed without a fight. */
	public static final int REGEN_INTERVAL = 40;
	public static final int REGEN_DELAY = 100;

	// ---------------------------------------------------------------- general
	/** Anything with this much max health (or a Titan / Wither / Dragon) is a boss: capped damage, no knockback. */
	public static final float BOSS_HEALTH_THRESHOLD = 300.0f;
	/** A single move never takes more than this fraction of a boss's max health. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.06f;
	/** Minimum gap between any two moves. */
	public static final int GLOBAL_LOCK_TICKS = 4;
	/** His own leaps never cause fall damage for this long after take-off. */
	public static final int LEAP_NO_FALL_TICKS = 100;

	// ---------------------------------------------------------------- R: Combo Strike
	public static final int COMBO_COOLDOWN = 100;
	public static final float COMBO_HIT_DAMAGE = 5.0f;
	public static final float COMBO_FINISHER_DAMAGE = 7.0f;
	/** Ticks between the three punches. */
	public static final int COMBO_INTERVAL = 5;
	public static final double COMBO_RANGE = 4.0;
	public static final double COMBO_FINISHER_KNOCKBACK = 1.0;

	// ---------------------------------------------------------------- Shift+R: Uppercut Launcher
	public static final int UPPERCUT_COOLDOWN = 160;
	public static final float UPPERCUT_DAMAGE = 10.0f;
	/** Upward speed given to the target: about 5 blocks of air. */
	public static final double UPPERCUT_LIFT = 1.05;
	public static final double UPPERCUT_RANGE = 4.0;

	// ---------------------------------------------------------------- G: Shield Throw
	public static final int SHIELD_THROW_COOLDOWN = 140;
	public static final float SHIELD_DAMAGE = 8.0f;
	/** Enemies one throw can hit (the first plus two ricochets). */
	public static final int SHIELD_MAX_HITS = 3;
	public static final double SHIELD_SPEED = 1.6;
	public static final double SHIELD_RANGE = 24.0;
	/** How far the shield looks for the next enemy to ricochet into. */
	public static final double SHIELD_RICOCHET_RANGE = 10.0;
	public static final double SHIELD_KNOCKBACK = 0.6;
	public static final int SHIELD_MAX_LIFE = 200;

	// ---------------------------------------------------------------- Shift+G: Shield Bash Charge
	public static final int BASH_COOLDOWN = 180;
	public static final float BASH_DAMAGE = 7.0f;
	public static final double BASH_SPEED = 1.1;
	public static final int BASH_TICKS = 8;
	public static final double BASH_KNOCKBACK = 1.6;
	public static final double BASH_WIDTH = 1.6;

	// ---------------------------------------------------------------- Z: Leaping Slam
	public static final int SLAM_COOLDOWN = 240;
	public static final float SLAM_DAMAGE = 12.0f;
	public static final double SLAM_RADIUS = 5.0;
	public static final double SLAM_HEIGHT = 4.0;
	public static final double SLAM_FORWARD = 0.8;
	public static final double SLAM_KNOCKBACK = 1.2;
	public static final double SLAM_LIFT = 0.4;
	/** The slam lands on its own after this long even if he never touches the ground (a cliff edge, water). */
	public static final int SLAM_MAX_AIR_TICKS = 60;

	// ---------------------------------------------------------------- Shift+Z: ULTIMATE Super Soldier Onslaught
	public static final int ONSLAUGHT_COOLDOWN = 2000;
	public static final int ONSLAUGHT_DURATION = 200;
	/** The opening shockwave. */
	public static final float ONSLAUGHT_BURST_DAMAGE = 8.0f;
	public static final double ONSLAUGHT_BURST_RADIUS = 6.0;
	/** Extra melee damage while it runs (attribute, on top of the unarmed bonus). */
	public static final double ONSLAUGHT_ATTACK_BONUS = 5.0;
	/** Every melee hit during it shocks enemies this close to the target for this much. */
	public static final float ONSLAUGHT_SHOCK_DAMAGE = 4.0f;
	public static final double ONSLAUGHT_SHOCK_RADIUS = 3.0;

	// ---------------------------------------------------------------- X: Tactical Roll
	public static final int ROLL_COOLDOWN = 80;
	public static final double ROLL_SPEED = 1.35;
	public static final int ROLL_IFRAMES = 10;

	// ---------------------------------------------------------------- Shift+X: High Leap
	public static final int HIGH_LEAP_COOLDOWN = 160;
	public static final double HIGH_LEAP_HEIGHT = 5.0;
	public static final double HIGH_LEAP_FORWARD = 1.25;

	// ---------------------------------------------------------------- V: Battle Cry
	public static final int BATTLE_CRY_COOLDOWN = 600;
	public static final double BATTLE_CRY_ALLY_RADIUS = 16.0;
	public static final double BATTLE_CRY_ENEMY_RADIUS = 12.0;
	public static final int BATTLE_CRY_BUFF_TICKS = 200;
	public static final int BATTLE_CRY_DEBUFF_TICKS = 160;

	// ---------------------------------------------------------------- Shift+V: Tactical Focus
	public static final int FOCUS_COOLDOWN = 500;
	public static final int FOCUS_DURATION = 160;
	public static final double FOCUS_RADIUS = 30.0;
	public static final int FOCUS_GLOW_TICKS = 200;
	/** His melee hits on a marked (glowing) enemy while Focus runs are critical: x1.5. */
	public static final float FOCUS_CRIT_MULTIPLIER = 1.5f;
}
