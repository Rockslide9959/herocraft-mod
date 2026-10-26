package com.projecthero.mod.greenlantern.mech;

/**
 * v0.17.1: balance numbers for the Hard-Light Mech (Green Lantern's ultimate construct). Every damage number goes through
 * {@code GreenLanternRanks.power} (Oath + Lantern Rank + Willpower Surge) and every Ring Charge cost through
 * {@code GreenLanternEnergy.spend} (the Oath doubles it, the rank cuts it).
 */
public final class GreenLanternMechConfig {
	private GreenLanternMechConfig() {
	}

	// ---- forming / keeping it up
	/** Ring Charge to form the mech (of 10,000). */
	public static final float FORM_COST = 1500f;
	/** Ring Charge per second while it stands; when the ring can't pay it, the mech dissolves. */
	public static final float UPKEEP_PER_SEC = 15f;
	/** The mech assembling round the pilot (no moves, no walking). */
	public static final int ASSEMBLE_TICKS = 40;
	/** N: it dissolves over this long, then sets the pilot down. */
	public static final int DISSOLVE_TICKS = 20;
	/** Cooldown after leaving it (N, or the ring running dry) before it can be formed again. */
	public static final int REFORM_COOLDOWN_TICKS = 200;
	/** Cooldown after it is SHATTERED (its integrity hit 0). */
	public static final int SHATTER_COOLDOWN_TICKS = 1200;
	public static final String COOLDOWN_ID = "hard_light_mech";

	// ---- body
	public static final float WIDTH = 3.2f;
	public static final float HEIGHT = 6.5f;
	/** Integrity at Lantern Rank 1; +10% per rank above it. */
	public static final float BASE_INTEGRITY = 300f;
	public static final float INTEGRITY_PER_RANK = 0.10f;
	public static final double ARMOR = 8.0;
	/** Movement speed attribute: about 10 blocks a second on flat ground. */
	public static final double WALK_SPEED = 0.23;
	public static final double STEP_HEIGHT = 2.0;
	/** Space: the thrusters kick it up about 3.5 blocks. */
	public static final double JUMP_VELOCITY = 0.72;
	/** Where the pilot sits: 3.66 blocks up (feet of a sitting player 0.6 below this). */
	public static final double SEAT_Y = 3.66;
	public static final double SEAT_Z = 0.0;
	/** Speed while the shield is up / the beam is firing. */
	public static final float SHIELD_SPEED = 0.5f;
	public static final float BEAM_SPEED = 0.35f;

	// ---- R: punch combo (alternating fists; hold R to keep swinging)
	public static final float PUNCH_DAMAGE = 20f;
	public static final float PUNCH_COST = 8f;
	public static final int PUNCH_COOLDOWN_TICKS = 10;
	/** The fist lands this many ticks after the swing starts (the clip's impact frame). */
	public static final int PUNCH_IMPACT_TICKS = 4;
	public static final double PUNCH_REACH = 5.5;
	public static final double PUNCH_KNOCKBACK = 1.8;

	// ---- Shift+R: Rocket Fist
	public static final float ROCKET_DAMAGE = 30f;
	public static final float ROCKET_COST = 60f;
	public static final int ROCKET_COOLDOWN_TICKS = 120;
	public static final double ROCKET_RANGE = 24.0;
	public static final double ROCKET_SPEED = 2.6;
	public static final int ROCKET_LAUNCH_TICKS = 5;

	// ---- G: Ground Slam
	public static final float SLAM_DAMAGE = 28f;
	public static final float SLAM_COST = 80f;
	public static final int SLAM_COOLDOWN_TICKS = 160;
	public static final int SLAM_IMPACT_TICKS = 11;
	public static final double SLAM_RADIUS = 9.0;
	public static final int SLAM_WAVE_TICKS = 10;

	// ---- X: Thruster Dash
	public static final float DASH_DAMAGE = 14f;
	public static final float DASH_COST = 30f;
	public static final int DASH_COOLDOWN_TICKS = 50;
	public static final double DASH_SPEED = 2.4;
	public static final int DASH_HIT_TICKS = 10;

	// ---- Z (hold): Chest Lantern Beam
	public static final float BEAM_DAMAGE = 8f;
	public static final int BEAM_INTERVAL_TICKS = 5;
	public static final float BEAM_DRAIN_PER_SEC = 50f;
	public static final double BEAM_RANGE = 48.0;

	// ---- V (hold): Hard-Light Shield wall
	public static final float SHIELD_COST = 40f;
	public static final float SHIELD_DRAIN_PER_SEC = 20f;
	/** Hits from the front arc while the shield is up do only this fraction to the mech. */
	public static final float SHIELD_FRONT_FACTOR = 0.2f;

	// ---- C: Missile Pods
	public static final float MISSILE_DAMAGE = 9f;
	public static final float MISSILE_COST = 120f;
	public static final int MISSILE_COOLDOWN_TICKS = 200;
	public static final int MISSILE_COUNT = 12;
	public static final double MISSILE_TARGET_RANGE = 36.0;
}
