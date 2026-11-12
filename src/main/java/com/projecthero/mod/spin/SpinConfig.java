package com.projecthero.mod.spin;

/**
 * v0.17.3 the Spin (the Spin): every number of the power in one place. The design ({@code docs/SPIN_DESIGN.md}) fixes the
 * damage, costs and durations; the rest (cooldowns where none is given, ranges, speeds, the Perfect Spin window) are chosen here.
 * Times are in ticks.
 */
public final class SpinConfig {
	private SpinConfig() {
	}

	// ---------------------------------------------------------------- passives
	/** No extra hearts: the player keeps 20 health (user, 2026-11-12). */
	public static final double HEALTH_BONUS = 0.0;
	/** +15% movement speed (of the base). */
	public static final double SPEED_BONUS = 0.15;
	/** +4 melee damage. */
	public static final double MELEE_BONUS = 4.0;
	/** 15% of every incoming hit taken away. */
	public static final float DAMAGE_RESIST = 0.15f;
	/** Passive Regeneration I (user, 2026-11-12): 1 health every 50 ticks, the same pace as the vanilla effect. */
	public static final int REGEN_EVERY_TICKS = 50;

	// ---------------------------------------------------------------- the Spin bar
	public static final float SPIN_MAX = 100f;
	/** 4 Spin a second. */
	public static final float SPIN_REGEN_PER_TICK = 4f / 20f;
	/** How often the bar is re-sent to the clients (it ticks in place on the server). */
	public static final int SYNC_INTERVAL = 5;

	// ---------------------------------------------------------------- riding
	/** On a horse every Spin move hits 30% harder... */
	public static final float RIDING_DAMAGE_MULT = 1.3f;
	/** ...and the Golden Rectangle closes twice as fast. */
	public static final int RIDING_CHARGE_DIVISOR = 2;

	// ---------------------------------------------------------------- the balls
	public static final int BALLS = 2;
	public static final double BALL_RANGE = 30.0;
	/** Blocks a tick outbound (24 blocks a second). */
	public static final double BALL_SPEED = 1.2;
	/** Blocks a tick on the way back (it speeds up to this). */
	public static final double BALL_RETURN_SPEED = 1.5;
	/** Caught when it comes this close to the hand. */
	public static final double BALL_CATCH_DISTANCE = 1.4;
	/** A ball that somehow never comes back is caught anyway after this long. */
	public static final int BALL_MAX_LIFE = 200;
	/** Twin Balls: the sideways speed each ball starts with, so they curve in from either side. */
	public static final double TWIN_SIDE_SPEED = 0.55;
	/** Twin Balls: how hard the balls steer back onto the aim point each tick. */
	public static final double TWIN_STEER = 0.22;

	// ---------------------------------------------------------------- curving after the look (R / Shift+R, outbound only)
	/** With no creature to home on, the ball bends toward the crosshair line at most this much a tick. */
	public static final double CURVE_LOOK_TURN_DEG = 6.0;
	/** ...easing in: each tick it turns this share of the angle still to go (capped by the line above). */
	public static final double CURVE_EASE = 0.6;
	/** Homing onto a creature under the crosshair: the sharpest turn a tick (turning circle ~6.9 blocks at 24 blocks/s). */
	public static final double CURVE_HOMING_TURN_DEG = 10.0;
	/** Twin Balls swoop harder (they start 28 degrees off to either side). */
	public static final double TWIN_TURN_DEG = 12.0;
	/** The most an R ball may turn over its whole flight out. */
	public static final double CURVE_MAX_TOTAL_DEG = 120.0;
	/** Twin Balls get this much on top for their own swoop in. */
	public static final double TWIN_SWOOP_DEG = 60.0;
	/** No steering while the thrower looks further than this from the ball (turning round never drags it back). */
	public static final double CURVE_LOOK_CONE_DEG = 70.0;
	/** ...except while the ball is still this close to his eyes (it starts at the hand, off to the side). */
	public static final double CURVE_NEAR = 3.0;
	/** A look goal more than this off the ball's heading is ignored (no U-turns). */
	public static final double CURVE_GIVE_UP_DEG = 100.0;
	/** The look goal: the point on the crosshair line this far beyond the ball's own distance. */
	public static final double CURVE_LEAD = 5.0;
	/** A creature counts as "aimed at" within this cone of the crosshair, or the cone of its body plus {@link #CURVE_AIM_SLACK}. */
	public static final double CURVE_AIM_CONE_DEG = 4.0;
	public static final double CURVE_AIM_SLACK = 0.6;
	/** A creature already homed on keeps the lock this much further off the crosshair (no flicker at the edge). */
	public static final double CURVE_KEEP_EXTRA_DEG = 4.0;
	/** Homing only onto creatures it reaches within this much turn. */
	public static final double CURVE_MAX_HOMING_TURN_DEG = 90.0;
	/** The reach check plans with this share of the real turn rate (a safety margin, so a planned curve always lands). */
	public static final double CURVE_REACH_MARGIN = 0.85;

	// ---------------------------------------------------------------- R / Shift+R
	public static final float BALL_DAMAGE = 10f;
	public static final float BALL_COST = 15f;
	public static final int BALL_COOLDOWN = 10;
	/** A ball hit spins the target for 3 s. */
	public static final int SPIN_TICKS = 60;
	public static final float TWIN_COST = 30f;
	public static final int TWIN_COOLDOWN = 20;

	// ---------------------------------------------------------------- G
	/** Spin Touch: the design gives no damage, so a light 6 rides with the lock. */
	public static final float TOUCH_DAMAGE = 6f;
	public static final float TOUCH_COST = 20f;
	public static final double TOUCH_REACH = 4.0;
	/** Joints locked for 2 s. */
	public static final int TOUCH_LOCK_TICKS = 40;
	public static final int TOUCH_COOLDOWN = 20;

	// ---------------------------------------------------------------- Shift+G (hold)
	public static final float GOLDEN_DAMAGE = 35f;
	/** Perfect Spin: double. */
	public static final float GOLDEN_PERFECT_DAMAGE = 70f;
	public static final float GOLDEN_COST = 50f;
	public static final int GOLDEN_COOLDOWN = 12 * 20;
	public static final double GOLDEN_RANGE = 40.0;
	/** The line's half-width (how close to the line a creature must be). */
	public static final double GOLDEN_WIDTH = 0.6;
	/** The Golden Rectangle closes onto the crosshair after this long (half on a horse). */
	public static final int GOLDEN_CHARGE_TICKS = 30;
	/** Release within this many ticks either side of the close = Perfect Spin (7 ticks, about a third of a second). */
	public static final int PERFECT_WINDOW = 3;
	/** Held this long past the close, the ball goes on its own (not Perfect). */
	public static final int CHARGE_OVERHOLD = 40;

	// ---------------------------------------------------------------- Z
	public static final float SCAN_COST = 15f;
	public static final double SCAN_RADIUS = 40.0;
	public static final int SCAN_GLOW_TICKS = 8 * 20;
	/** The ball spins on the ground this long, then hops back to the hand. */
	public static final int SCAN_BALL_TICKS = 40;
	public static final int SCAN_COOLDOWN = 8 * 20;

	// ---------------------------------------------------------------- Shift+Z (hold)
	public static final float BREAKER_DAMAGE = 60f;
	public static final float BREAKER_COST = 100f;
	public static final int BREAKER_COOLDOWN = 3 * 60 * 20;
	public static final double BREAKER_RANGE = 30.0;
	/** A release that is not a Perfect Spin fails and costs this much (no cooldown). */
	public static final float BREAKER_FAIL_COST = 20f;
	/** The figure reaches the target and strikes this long after the release. */
	public static final int BREAKER_STRIKE_DELAY = 10;
	/** Aging: Weakness II, Slowness III, Wither for 10 s. */
	public static final int AGING_TICKS = 10 * 20;

	// ---------------------------------------------------------------- X
	public static final float LEAP_COST = 10f;
	/** About 12 blocks forward and 5 up. */
	public static final double LEAP_FORWARD = 1.15;
	public static final double LEAP_UP = 1.0;
	public static final int LEAP_COOLDOWN = 20;
	/** No fall damage until he lands (or this long at most). */
	public static final int LEAP_SAFE_TICKS = 100;

	// ---------------------------------------------------------------- Shift+X
	public static final int VALKYRIE_DEAD_COOLDOWN = 60 * 20;
	public static final int VALKYRIE_WHISTLE_COOLDOWN = 3 * 20;
	/** Vanilla's best horse is 0.3375; Valkyrie runs at 0.42 (about 17 blocks a second). */
	public static final double VALKYRIE_SPEED = 0.42;
	public static final double VALKYRIE_HEALTH = 40.0;
	public static final double VALKYRIE_JUMP = 1.0;
	/** Further than this (or in another dimension) she is brought round behind you first. */
	public static final double VALKYRIE_FETCH_DISTANCE = 32.0;
	/** She gallops in for at most this long, then you are put in the saddle anyway. */
	public static final int VALKYRIE_GALLOP_TICKS = 50;
	public static final String VALKYRIE_TAG = "projecthero_valkyrie";

	// ---------------------------------------------------------------- C
	public static final float QUAKE_DAMAGE = 14f;
	public static final float QUAKE_COST = 25f;
	public static final double QUAKE_RADIUS = 6.0;
	public static final int QUAKE_SLOW_TICKS = 40;
	public static final int QUAKE_COOLDOWN = 40;
	public static final int QUAKE_BALL_TICKS = 10;

	// ---------------------------------------------------------------- Shift+C
	public static final float SHIELD_COST = 30f;
	public static final int SHIELD_TICKS = 4 * 20;
	public static final double SHIELD_DEFLECT_RADIUS = 2.8;
	public static final double SHIELD_HIT_RADIUS = 2.2;
	/** 4 damage per hit; a creature is hit again every 10 ticks (vanilla's hurt window). */
	public static final float SHIELD_DAMAGE = 4f;
	public static final int SHIELD_HIT_GAP = 10;
	public static final int SHIELD_COOLDOWN = 5 * 20;

	// ---------------------------------------------------------------- V
	public static final float HEAL_COST = 25f;
	/** 6 hearts over 3 s. */
	public static final float HEAL_TOTAL = 12f;
	public static final int HEAL_TICKS = 60;
	public static final int HEAL_COOLDOWN = 15 * 20;
	public static final double HEAL_AIM_RANGE = 16.0;

	// ---------------------------------------------------------------- Shift+V
	public static final float FACE_COST = 20f;
	public static final int FACE_TICKS = 20 * 20;
	public static final int FACE_COOLDOWN = 5 * 20;
	/** Mobs this close that can see him when he strikes become witnesses (they may target him again). */
	public static final double FACE_WITNESS_RANGE = 24.0;
}
