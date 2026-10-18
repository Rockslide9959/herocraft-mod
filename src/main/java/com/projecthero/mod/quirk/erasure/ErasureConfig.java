package com.projecthero.mod.quirk.erasure;

/**
 * v0.16.7: every number of the Erasure Quirk (Shota Aizawa / Eraser Head), the ability ids, the synced kit vals and the
 * pose ids (100..119). Ticks unless noted.
 * <pre>
 *   R  Capture Lash      Shift+R  Capture Disarm
 *   G  Erase (hold)      Shift+G  Erase Glare (hold)
 *   X  Capture Swing     Shift+X  Capture Zip
 *   Z  Capture Bind      Shift+Z  Capture Hoist
 *   V  Bind Slam         Shift+V  Double Bind
 *   C  Goggles (toggle)  Shift+C  Eye Drops (works while blinking)
 * </pre>
 */
public final class ErasureConfig {
	private ErasureConfig() {
	}

	// ---------------------------------------------------------------- ability ids
	public static final String LASH = "quirk_erasure_lash";
	public static final String DISARM = "quirk_erasure_disarm";
	public static final String ERASE = "quirk_erasure_erase";
	public static final String GLARE = "quirk_erasure_glare";
	public static final String SWING = "quirk_erasure_swing";
	public static final String ZIP = "quirk_erasure_zip";
	public static final String BIND = "quirk_erasure_bind";
	public static final String HOIST = "quirk_erasure_hoist";
	public static final String SLAM = "quirk_erasure_slam";
	public static final String DOUBLE = "quirk_erasure_double_bind";
	public static final String GOGGLES = "quirk_erasure_goggles";
	public static final String DROPS = "quirk_erasure_eye_drops";

	// ---------------------------------------------------------------- synced kit vals / vecs (QuirkState)
	/** 0 = not erasing, 1 = Erase, 2 = Erase Glare. */
	public static final String V_ERASE = "er_erase";
	/** When the current stare began (hair rises from here). */
	public static final String V_ERASE_AT = "er_erase_at";
	/** 1 = swinging on the scarf, 2 = zipping to a creature. */
	public static final String V_LINE = "er_line";
	public static final String V_LINE_AT = "er_line_at";
	/** The swing's rope length x1000. */
	public static final String V_ROPE = "er_rope";
	/** The zip's creature (entity id + 1). */
	public static final String V_ZIP_TARGET = "er_zip";
	public static final String P_HOOK = "er_hook";
	/** The lash / disarm whip: when it went out, and where the tip went. */
	public static final String V_LASH_AT = "er_lash_at";
	public static final String P_LASH = "er_lash";
	/** The bound creature (entity id + 1), till when, and 1 if it is hoisted. */
	public static final String V_BIND = "er_bind";
	public static final String V_BIND_UNTIL = "er_bind_until";
	public static final String V_HOIST = "er_hoist";
	/** Bind Slam's creature (id + 1) and when it started. */
	public static final String V_SLAM = "er_slam";
	public static final String V_SLAM_AT = "er_slam_at";
	/** Double Bind's two creatures (ids + 1) and when it started. */
	public static final String V_DB_A = "er_db_a";
	public static final String V_DB_B = "er_db_b";
	public static final String V_DB_AT = "er_db_at";
	/** 1 while the goggles are down over the eyes. */
	public static final String V_GOGGLES = "er_goggles";
	/** 1 once the Dry Eyes meter has been filled for this life. */
	public static final String V_INIT = "er_init";

	// ---------------------------------------------------------------- poses (Quirk.ERASURE.animBase = 100)
	public static final int ANIM_LASH = 101;
	public static final int ANIM_DISARM = 102;
	public static final int ANIM_SWING = 103;
	public static final int ANIM_ZIP = 104;
	public static final int ANIM_BIND = 105;
	public static final int ANIM_HOIST = 106;
	public static final int ANIM_SLAM = 107;
	public static final int ANIM_DOUBLE = 108;
	public static final int ANIM_GOGGLES = 109;
	public static final int ANIM_DROPS = 110;
	public static final int ANIM_BLINK = 111;

	// ---------------------------------------------------------------- Dry Eyes (100 = fresh)
	public static final float METER_FULL = 100f;
	/** Erase drains this a tick (~11 s from fresh). */
	public static final float ERASE_DRAIN = 0.45f;
	/** Erase Glare drains this many times faster. */
	public static final float GLARE_DRAIN_MULT = 3f;
	/** Refill a tick while not erasing (~10 s from empty). */
	public static final float REFILL = 0.5f;
	/** Refill a tick while blinking (eyes shut). */
	public static final float BLINK_REFILL = 1.25f;
	/** The forced blink at 0: eyes shut, no moves except Eye Drops. */
	public static final int BLINK_TICKS = 40;
	/** A stare needs at least this much left to start. */
	public static final float ERASE_MIN_START = 3f;

	// ---------------------------------------------------------------- G: Erase / Erase Glare
	public static final double ERASE_RANGE = 24.0;
	/** Half-angle of the Erase cone, degrees (the creature you look at). */
	public static final double ERASE_CONE_DEGREES = 7.0;
	/** Half-angle of the Glare cone, degrees (everyone in front). */
	public static final double GLARE_CONE_DEGREES = 40.0;
	/** Each tick of the stare suppresses for this long (the effect fades a moment after the stare breaks). */
	public static final int SUPPRESS_TICKS = 6;
	/** After a stare ends. */
	public static final int ERASE_COOLDOWN = 10;
	public static final int GLARE_COOLDOWN = 60;

	// ---------------------------------------------------------------- R: Capture Lash / Capture Disarm
	public static final double LASH_RANGE = 16.0;
	public static final float LASH_DAMAGE = 3f;
	/** Speed the lashed creature is pulled in at, blocks a tick. */
	public static final double LASH_PULL = 1.25;
	public static final int LASH_COOLDOWN = 50;
	public static final int LASH_MISS_COOLDOWN = 12;
	public static final double DISARM_RANGE = 14.0;
	public static final int DISARM_COOLDOWN = 160;
	/** Scarf whip shown out and back. */
	public static final int LASH_SHOW_TICKS = 9;

	// ---------------------------------------------------------------- X: Capture Swing / Capture Zip
	public static final double SWING_RANGE = 30.0;
	public static final int SWING_COOLDOWN = 10;
	public static final double SWING_SLACK_BREAK = 6.0;
	public static final double ZIP_RANGE = 24.0;
	public static final double ZIP_SPEED = 1.35;
	public static final double ZIP_ARRIVE = 2.2;
	public static final int ZIP_MAX_TICKS = 30;
	public static final float ZIP_KICK_DAMAGE = 4f;
	public static final int ZIP_COOLDOWN = 80;

	// ---------------------------------------------------------------- Z: Capture Bind / Capture Hoist
	public static final double BIND_RANGE = 16.0;
	public static final int BIND_TICKS = 80;
	public static final int BIND_COOLDOWN = 200;
	public static final double HOIST_HEIGHT = 2.5;
	public static final int HOIST_COOLDOWN = 240;

	// ---------------------------------------------------------------- V: Bind Slam / Double Bind
	public static final double SLAM_RANGE = 12.0;
	public static final int SLAM_LIFT_TICKS = 8;
	public static final double SLAM_LIFT_SPEED = 0.95;
	public static final double SLAM_DOWN_SPEED = 2.2;
	public static final int SLAM_MAX_TICKS = 40;
	public static final float SLAM_DAMAGE = 8f;
	public static final int SLAM_COOLDOWN = 160;
	public static final double DOUBLE_RANGE = 14.0;
	/** The second creature: the nearest other one within this of the first. */
	public static final double DOUBLE_PARTNER_RADIUS = 8.0;
	public static final double DOUBLE_PULL = 0.85;
	public static final int DOUBLE_MAX_TICKS = 16;
	public static final double DOUBLE_HIT_DISTANCE = 1.5;
	public static final float DOUBLE_DAMAGE = 7f;
	public static final int DOUBLE_SLOW_TICKS = 40;
	public static final int DOUBLE_COOLDOWN = 240;

	// ---------------------------------------------------------------- C: Goggles / Eye Drops
	public static final int GOGGLES_COOLDOWN = 10;
	public static final int GOGGLES_NIGHT_VISION = 260;
	/** The goggles outline powered players / special mobs within this (client side). */
	public static final double GOGGLES_RANGE = 32.0;
	public static final int DROPS_COOLDOWN = 1200;

	// ---------------------------------------------------------------- shared
	/** Bosses take at most this fraction of their max health a hit, and are never rooted / thrown. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
	public static final float BOSS_HEALTH_THRESHOLD = 150f;
}
