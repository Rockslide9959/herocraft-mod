package com.projecthero.mod.quirk.zerogravity;

/** v0.16.7: every Zero Gravity number (ticks unless noted). Ability ids are {@code quirk_zero_gravity_<move>}. */
public final class ZeroGravityConfig {
	private ZeroGravityConfig() {
	}

	private static final String P = "quirk_zero_gravity_";

	// ---------------------------------------------------------------- ability ids
	public static final String TOUCH = P + "float_touch";
	public static final String FLING = P + "float_fling";
	public static final String RELEASE = P + "release";
	public static final String SLAM = P + "meteor_slam";
	public static final String SHOWER = P + "meteor_shower";
	public static final String SHIELD = P + "debris_shield";
	public static final String SELF = P + "zero_g_self";
	public static final String LEAP = P + "float_leap";
	public static final String COMET = P + "comet";
	public static final String METEOR = P + "uravity_meteor";
	public static final String THROW = P + "gunhead_throw";
	public static final String COMBO = P + "gunhead_combo";

	// ---------------------------------------------------------------- the Nausea meter
	/** Nausea a second per unit of floated weight (a zombie weighs about 1, a block of rubble 0.4, herself 1). */
	public static final float NAUSEA_PER_WEIGHT_SEC = 0.5f;
	/** The longer things float, the faster it climbs: up to this many times faster... */
	public static final float NAUSEA_TIME_BONUS_MAX = 1.5f;
	/** ...reached after this long floating without a break. */
	public static final int NAUSEA_TIME_BONUS_TICKS = 600;
	/** Nausea lost a second while nothing floats. */
	public static final float NAUSEA_DECAY_SEC = 8f;
	/** A one-off sting every time she floats something (per creature). */
	public static final float NAUSEA_PER_FLOAT = 3f;
	/** The warning shows once she is this sick. */
	public static final float NAUSEA_WARN = 75f;
	/** At 100: everything drops, she is sick (vanilla Nausea) and has no moves for this long. */
	public static final int NAUSEA_OVERLOAD_TICKS = 100;

	/** Weight of one floated creature: bounding box volume x this, clamped. */
	public static final double WEIGHT_PER_VOLUME = 1.5;
	public static final double WEIGHT_MIN = 0.5;
	public static final double WEIGHT_MAX = 6.0;
	public static final double WEIGHT_RUBBLE = 0.4;
	public static final double WEIGHT_SELF = 1.0;

	// ---------------------------------------------------------------- floating
	/** A touched creature rises this far, then hangs there drifting. */
	public static final double FLOAT_RISE = 1.6;
	public static final double FLOAT_LIFT_SPEED = 0.22;
	/** A floater further than this from her drops (and anything in another world). */
	public static final double FLOAT_LEASH = 64.0;
	/** Most creatures she can float at once (touch moves). */
	public static final int TOUCH_MAX = 5;
	/** Comet's own cap (the whole area at once). */
	public static final int COMET_MAX = 16;
	/** A touched squadmate lands softly on Release (Slow Falling). */
	public static final int ALLY_SLOW_FALL_TICKS = 80;

	// ---------------------------------------------------------------- R / Shift+R
	public static final double TOUCH_REACH = 4.0;
	public static final double TOUCH_ASSIST_DEGREES = 18.0;
	public static final float TOUCH_DAMAGE = 2f;
	public static final int TOUCH_COOLDOWN = 10;
	public static final double FLING_SPEED = 2.0;
	public static final int FLING_COOLDOWN = 30;

	// ---------------------------------------------------------------- G / Shift+G
	public static final int RELEASE_COOLDOWN = 10;
	public static final double SLAM_SPEED = 2.4;
	public static final float SLAM_DAMAGE = 8f;
	public static final double SLAM_SPLASH_RADIUS = 2.0;
	public static final float SLAM_SPLASH_DAMAGE = 4f;
	public static final int SLAM_WATCH_TICKS = 60;
	public static final int SLAM_COOLDOWN = 60;

	// ---------------------------------------------------------------- X / Shift+X
	/** Rubble lifted from the loose ground within this radius. */
	public static final int RUBBLE_RADIUS = 5;
	public static final int SHOWER_PIECES = 8;
	public static final double SHOWER_RANGE = 28.0;
	public static final int SHOWER_HOVER_TICKS = 20;
	public static final int SHOWER_FIRE_GAP = 3;
	public static final double SHOWER_SPEED = 1.3;
	public static final float SHOWER_DAMAGE = 5f;
	public static final double SHOWER_SPLASH = 1.6;
	public static final int SHOWER_LIFE = 120;
	/** Lifted ground is put back exactly after this long. */
	public static final int RUBBLE_RESTORE_TICKS = 240;
	public static final int SHOWER_COOLDOWN = 160;

	public static final int SHIELD_PIECES = 6;
	public static final int SHIELD_TICKS = 160;
	public static final double SHIELD_RADIUS = 1.8;
	public static final double SHIELD_CATCH_RADIUS = 2.8;
	public static final int SHIELD_COOLDOWN = 400;

	// ---------------------------------------------------------------- Z / Shift+Z
	public static final double SELF_RISE_SPEED = 0.28;
	public static final double SELF_SINK_SPEED = 0.28;
	public static final double SELF_HOVER_DRAG = 0.82;
	public static final int SELF_COOLDOWN = 10;
	/** Her gravity during the leap is cut by this fraction (1 = none at all). */
	public static final double LEAP_GRAVITY_CUT = 0.85;
	public static final double LEAP_FORWARD = 1.5;
	public static final double LEAP_UP = 1.1;
	public static final int LEAP_MAX_TICKS = 160;
	public static final int LEAP_COOLDOWN = 120;

	// ---------------------------------------------------------------- V / Shift+V
	public static final double COMET_RADIUS = 8.0;
	public static final int COMET_COOLDOWN = 500;

	public static final double METEOR_RANGE = 28.0;
	public static final double METEOR_HEIGHT = 7.0;
	public static final int METEOR_RISE_TICKS = 25;
	public static final int METEOR_HOLD_TICKS = 6;
	public static final int METEOR_LIFE = 120;
	public static final float METEOR_DAMAGE = 14f;
	public static final double METEOR_RADIUS = 3.0;
	public static final double METEOR_KNOCKBACK = 1.0;
	public static final int METEOR_RESTORE_TICKS = 300;
	public static final int METEOR_COOLDOWN = 600;
	public static final float METEOR_NAUSEA = 15f;

	// ---------------------------------------------------------------- C / Shift+C
	public static final double THROW_REACH = 3.5;
	public static final float THROW_DAMAGE = 4f;
	public static final double THROW_SPEED = 1.0;
	public static final double THROW_LIFT = 0.45;
	/** A floating target is thrown this fast instead (and keeps floating: it sails much further). */
	public static final double THROW_FLOAT_SPEED = 2.4;
	public static final int THROW_COOLDOWN = 60;

	public static final double COMBO_REACH = 3.5;
	public static final float[] COMBO_DAMAGE = { 4f, 4f, 6f };
	public static final int COMBO_GAP = 5;
	public static final double COMBO_FINISH_KNOCKBACK = 1.1;
	public static final int COMBO_COOLDOWN = 80;

	// ---------------------------------------------------------------- bosses
	/** Bosses are never floated or thrown, and a hit takes at most this much of their max health (like All Might). */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
}
