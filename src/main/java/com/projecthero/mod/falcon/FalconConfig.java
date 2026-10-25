package com.projecthero.mod.falcon;

/**
 * v0.17.1: every Falcon (Sam Wilson) number in one place (static finals, like Cyborg's / Nova's). Times are in ticks.
 * Balanced to Super Speed / Cyborg level after the v0.17.1 Green Lantern nerf: single big hits 12-18, small hits 2-5.
 */
public final class FalconConfig {
	private FalconConfig() {
	}

	// ---------------------------------------------------------------- Thrust
	public static final float THRUST_MAX = 100f;
	/** Refill a second while gliding (not boosting). */
	public static final float THRUST_REGEN_GLIDE = 6f;
	/** Refill a second on the ground. */
	public static final float THRUST_REGEN_GROUND = 12f;
	/** Burnt a second by the jet boost (hold Sprint while gliding). */
	public static final float BOOST_COST_PER_SECOND = 14f;
	/** The wing-flap climb burst (Space while gliding). */
	public static final float FLAP_COST = 6f;
	public static final int FLAP_COOLDOWN = 10;

	// ---------------------------------------------------------------- flight (client-simulated on the vanilla glide)
	/** Jet boost: the speed it pulls toward along the look, blocks a tick (34 b/s), and how hard. */
	public static final double BOOST_SPEED = 1.7;
	public static final double BOOST_PULL = 0.12;
	public static final double BOOST_PUSH = 0.06;
	/** Wing flap: upward kick and a little forward. */
	public static final double FLAP_LIFT = 0.62;
	public static final double FLAP_FORWARD = 0.12;
	/** Ground skim: within this height of the ground and above this speed, a speed bonus up to the cap. */
	public static final double SKIM_HEIGHT = 3.5;
	public static final double SKIM_MIN_SPEED = 0.6;
	public static final double SKIM_ACCEL = 0.025;
	public static final double SKIM_CAP = 2.0;
	/** Below this speed (blocks a tick) the glide stalls (HUD warning). */
	public static final double STALL_SPEED = 0.3;
	/** Take-off from the ground: the jet kick. */
	public static final double TAKEOFF_LIFT = 1.15;
	/** A landing at least this fast (blocks a tick) is a hero landing (dust ring, the kneel). */
	public static final double HERO_LANDING_SPEED = 0.55;
	public static final int LAND_POSE_TICKS = 16;
	/** Passive: +20% sprint speed in the gear. */
	public static final double SPRINT_BONUS = 0.20;

	// ---------------------------------------------------------------- combat
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// R: Wrist Guns (hold)
	public static final float GUN_DAMAGE = 2.5f;
	public static final int GUN_INTERVAL = 4;
	public static final double GUN_RANGE = 40.0;
	public static final int GUN_MAX_TICKS = 60;
	public static final int GUN_COOLDOWN = 30;

	// Shift+R: Micro-Missile Volley
	public static final int MISSILES = 4;
	public static final float MISSILE_DAMAGE = 6f;
	public static final float MISSILE_SPLASH = 3f;
	public static final double MISSILE_BLAST = 2.5;
	public static final double MISSILE_RANGE = 40.0;
	/** Lock-on cone (cosine of the half angle, about 25 degrees). */
	public static final double MISSILE_CONE = 0.9;
	public static final double MISSILE_SPEED = 1.4;
	public static final int MISSILE_LIFE = 60;
	public static final float MISSILE_COST = 20f;
	public static final int MISSILE_COOLDOWN = 160;

	// G: Wing Slash (ground arc / air diving slash)
	public static final float SLASH_DAMAGE = 9f;
	public static final double SLASH_RANGE = 4.5;
	/** Arc: cosine of the half angle (about 60 degrees -- a 120 degree sweep). */
	public static final double SLASH_ARC = 0.5;
	public static final float DIVE_SLASH_DAMAGE = 12f;
	public static final double DIVE_SLASH_DISTANCE = 9.0;
	public static final double DIVE_SLASH_SPEED = 1.8;
	public static final int SLASH_COOLDOWN = 50;

	// Shift+G: Wing Shield (hold; release = bash)
	public static final float SHIELD_HP = 40f;
	public static final int SHIELD_MAX_TICKS = 160;
	public static final double SHIELD_ARC = 0.34;
	public static final float BASH_DAMAGE = 6f;
	public static final double BASH_RANGE = 4.0;
	public static final double BASH_KNOCKBACK = 1.6;
	public static final int SHIELD_COOLDOWN = 100;

	// X: Afterburner
	public static final double AFTERBURNER_SPEED = 2.2;
	public static final double LEAP_UP = 1.3;
	public static final double LEAP_FORWARD = 0.9;
	public static final float AFTERBURNER_COST = 15f;
	public static final int AFTERBURNER_COOLDOWN = 40;

	// Shift+X: Barrel Roll
	public static final int ROLL_TICKS = 12;
	public static final double ROLL_SPEED = 1.2;
	public static final double ROLL_REFLECT_RADIUS = 3.5;
	public static final float ROLL_COST = 10f;
	public static final int ROLL_COOLDOWN = 80;

	// Z: Redwing Strike
	public static final double REDWING_RANGE = 32.0;
	public static final int LASER_SHOTS = 5;
	public static final int LASER_INTERVAL = 10;
	public static final float LASER_DAMAGE = 3f;
	public static final float STRIKE_COST = 15f;
	public static final int STRIKE_COOLDOWN = 140;

	// Shift+Z: Redwing Bombing Run
	public static final double BOMB_RANGE = 48.0;
	public static final int BOMBS = 6;
	public static final int BOMB_INTERVAL = 5;
	public static final float BOMB_DAMAGE = 4f;
	public static final double BOMB_RADIUS = 3.0;
	public static final double EMP_RADIUS = 5.0;
	public static final int EMP_STUN_TICKS = 60;
	public static final float BOMB_COST = 25f;
	public static final int BOMB_COOLDOWN = 300;

	// V: Tactical Goggles
	public static final double GOGGLES_RANGE = 60.0;
	/** Holding V this long (ticks) with the goggles already on is a zoom, not a toggle off. */
	public static final int GOGGLES_HOLD_TICKS = 8;
	public static final int GOGGLES_COOLDOWN = 10;

	// Shift+V: Lift & Carry
	public static final double CARRY_REACH = 3.5;
	public static final int CARRY_MAX_TICKS = 200;
	public static final double THROW_SPEED = 1.8;
	public static final int CARRY_COOLDOWN = 160;

	// C: Dive Bomb
	public static final double DIVE_MIN_HEIGHT = 5.0;
	public static final double DIVE_SPEED = 2.4;
	public static final int DIVE_MAX_TICKS = 60;
	public static final float DIVE_MIN_DAMAGE = 6f;
	public static final float DIVE_MAX_DAMAGE = 18f;
	/** Extra damage a block of drop. */
	public static final float DIVE_DAMAGE_PER_BLOCK = 0.6f;
	public static final double DIVE_RADIUS = 4.0;
	public static final float DIVE_COST = 10f;
	public static final int DIVE_COOLDOWN = 160;

	// Shift+C: Talon Drop
	public static final double TALON_REACH = 4.0;
	public static final int TALON_RISE_TICKS = 16;
	public static final double TALON_RISE_SPEED = 1.0;
	public static final float TALON_COST = 20f;
	public static final int TALON_COOLDOWN = 240;
}
