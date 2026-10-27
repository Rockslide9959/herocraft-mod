package com.projecthero.mod.falcon;

/**
 * v0.17.1: every Falcon (Sam Wilson) number in one place (static finals, like Cyborg's / Nova's). Times are in ticks.
 * The second v0.17.1 pass (user): directional jet flight like the Kryptonian's (19 b/s, 35 sprinting, 40 holding X), a
 * 100-round Wrist Gun magazine, and the user's damage numbers (guns 7, missiles 10, Wing Slash 15, Redwing lasers 5,
 * bombs 10). Bosses still take at most 8% of their max health a hit.
 */
public final class FalconConfig {
	private FalconConfig() {
	}

	// ---------------------------------------------------------------- Thrust
	public static final float THRUST_MAX = 100f;
	/** Refill a second while flying (not burning the jets). */
	public static final float THRUST_REGEN_FLYING = 6f;
	/** Refill a second on the ground. */
	public static final float THRUST_REGEN_GROUND = 12f;
	/** Burnt a second while X is held (the jet burn). */
	public static final float BOOST_COST_PER_SECOND = 14f;

	// ---------------------------------------------------------------- flight (the shared directional flight)
	/** Cruise, blocks a tick (19 b/s). */
	public static final double FLIGHT_SPEED = 19.0 / 20.0;
	/** Sprint-flying, blocks a tick (30 b/s; v0.17.1 user: was 35). */
	public static final double FLIGHT_SPRINT_SPEED = 30.0 / 20.0;
	/** Holding X: the jets drive him along the look at this speed, blocks a tick (40 b/s). */
	public static final double FLIGHT_BOOST_SPEED = 40.0 / 20.0;
	/** Space / Sneak straight up / down, blocks a tick (11 b/s). */
	public static final double FLIGHT_VERTICAL_SPEED = 0.55;
	/** A / D slide at this fraction of the cruise. */
	public static final double FLIGHT_STRAFE = 0.7;
	/** Fraction of the gap to the wanted velocity closed a tick while steering / against the motion / with no input. */
	public static final double FLIGHT_ACCELERATION = 0.16;
	public static final double FLIGHT_BRAKE = 0.32;
	public static final double FLIGHT_IDLE = 0.09;
	/** Ground contact never lands him this soon after take-off. */
	public static final int FLIGHT_LIFTOFF_GRACE = 10;
	/** Take-off from the ground: the jet kick up (blocks a tick). */
	public static final double TAKEOFF_LIFT = 0.9;
	/** X held this long (ticks) is the jet burn; released sooner it is a tap (the Afterburner). */
	public static final int BOOST_HOLD_TICKS = 5;
	/** A landing at least this fast (blocks a tick) is a hero landing (dust ring, the kneel). */
	public static final double HERO_LANDING_SPEED = 0.55;
	public static final int LAND_POSE_TICKS = 16;
	/** Passive: +20% sprint speed in the gear. */
	public static final double SPRINT_BONUS = 0.20;
	/** How long the wings take to unfold / fold, ticks (client). */
	public static final float WING_UNFOLD_TICKS = 9f;

	// ---------------------------------------------------------------- combat
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// R: Wrist Guns (hold) -- the assault rifle's report, tracers and impacts
	public static final float GUN_DAMAGE = 7f;
	/** Ticks between rounds (5 a second). */
	public static final int GUN_INTERVAL = 4;
	public static final double GUN_RANGE = 48.0;
	public static final float AMMO_MAX = 100f;
	/** Rounds a shot. */
	public static final float AMMO_PER_SHOT = 1f;
	/** Refill a second while not firing. */
	public static final float AMMO_REGEN_PER_SECOND = 3f;
	/** After letting go (a tiny gap so a tap can't machine-gun past the fire rate). */
	public static final int GUN_COOLDOWN = 4;

	// Shift+R: Micro-Missile Volley
	public static final int MISSILES = 4;
	/** A direct hit (the one it struck takes no splash on top). */
	public static final float MISSILE_DAMAGE = 10f;
	/** Everyone else in the blast. */
	public static final float MISSILE_SPLASH = 3f;
	public static final double MISSILE_BLAST = 2.5;
	public static final double MISSILE_RANGE = 40.0;
	/** Lock-on cone (cosine of the half angle, about 25 degrees). */
	public static final double MISSILE_CONE = 0.9;
	public static final double MISSILE_SPEED = 1.4;
	public static final int MISSILE_LIFE = 60;
	public static final float MISSILE_COST = 20f;
	public static final int MISSILE_COOLDOWN = 160;

	// G: Wing Slash (the right wing sweeps round in front / in the air a diving slash)
	public static final float SLASH_DAMAGE = 15f;
	public static final double SLASH_RANGE = 4.8;
	/** Arc: cosine of the half angle (about 60 degrees -- a 120 degree sweep). */
	public static final double SLASH_ARC = 0.5;
	public static final float DIVE_SLASH_DAMAGE = 15f;
	public static final double DIVE_SLASH_DISTANCE = 9.0;
	public static final double DIVE_SLASH_SPEED = 1.8;
	/** The wing's swing, ticks (client animation; the hit lands on the press). */
	public static final int SLASH_SWING_TICKS = 9;
	public static final int SLASH_COOLDOWN = 50;

	// Shift+G: Wing Shield (hold; release = bash)
	public static final float SHIELD_HP = 40f;
	public static final int SHIELD_MAX_TICKS = 160;
	public static final double SHIELD_ARC = 0.34;
	public static final float BASH_DAMAGE = 6f;
	public static final double BASH_RANGE = 4.0;
	public static final double BASH_KNOCKBACK = 1.6;
	public static final int SHIELD_COOLDOWN = 100;

	// X tap: Afterburner (X held = the jet burn, above)
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
	public static final float LASER_DAMAGE = 5f;
	public static final float STRIKE_COST = 15f;
	public static final int STRIKE_COOLDOWN = 140;

	// Shift+Z: Redwing Bombing Run
	public static final double BOMB_RANGE = 48.0;
	public static final int BOMBS = 6;
	public static final int BOMB_INTERVAL = 5;
	public static final float BOMB_DAMAGE = 10f;
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
