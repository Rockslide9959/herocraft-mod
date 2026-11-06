package com.projecthero.mod.stand;

/**
 * v0.17.2: the Stand numbers (Star Platinum, the first Stand). Ticks unless named otherwise; damage in half-hearts.
 * Design: {@code docs/STAR_PLATINUM_DESIGN.md}.
 */
public final class StandConfig {
	private StandConfig() {
	}

	// ---------------------------------------------------------------- Stamina
	public static final float STAMINA_MAX = 100f;
	/** Per second while the Stand is out and nothing was dealt or taken for {@link #OUT_OF_COMBAT_TICKS}. */
	public static final float STAMINA_REGEN_IDLE = 12f;
	/** Per second otherwise (in a fight, or the Stand put away). */
	public static final float STAMINA_REGEN_BUSY = 4f;
	public static final int OUT_OF_COMBAT_TICKS = 40;

	/** Range C: how far Star Platinum reaches from you. */
	public static final double REACH = 3.5;
	/** Extra entity reach on empty-hand punches while the Stand is out. */
	public static final double PUNCH_REACH_BONUS = 1.0;

	// ---------------------------------------------------------------- H: summon
	public static final int SUMMON_TOGGLE_COOLDOWN = 10;
	public static final int SUMMON_ANIM_TICKS = 16;

	// ---------------------------------------------------------------- Stand punches (empty hand, Stand out)
	/** v0.17.2 (user): "increase his melee damage to 8" (was 5; the third hit of the combo keeps its lead, 8 -> 12). */
	public static final float PUNCH_DAMAGE = 8f;
	public static final float PUNCH_FINISHER_DAMAGE = 12f;
	public static final double PUNCH_FINISHER_KNOCKBACK = 1.3;
	public static final int PUNCH_COMBO_WINDOW = 25;

	// ---------------------------------------------------------------- R: ORA Rush (hold)
	public static final int RUSH_HIT_INTERVAL = 2;
	public static final float RUSH_HIT_DAMAGE = 1.5f;
	/** v0.17.2 (user: "make every ability drain less stamina"): every cost below is about 40% of what it was. */
	public static final float RUSH_STAMINA_PER_TICK = 0.45f;
	public static final float RUSH_FINISHER_DAMAGE = 7f;
	public static final double RUSH_FINISHER_KNOCKBACK = 2.0;
	public static final int RUSH_COOLDOWN = 120;
	public static final float RUSH_START_COST = 2f;
	/** Rush is cut off after this long (the finisher fires). */
	public static final int RUSH_MAX_TICKS = 100;
	/**
	 * v0.17.2 (user: "make the time stop voice line play first and only have time stop ability activate when the time stop part of
	 * the sound effect happens"): ticks from Shift+Z to the stop -- "Star Platinum: The World!" runs 1.9 s and the stop's burst
	 * lands at 1.93 s of time_stop.ogg.
	 */
	public static final int TIME_STOP_WINDUP_TICKS = 39;

	// ---------------------------------------------------------------- Shift+R: Star Finger
	public static final double FINGER_RANGE = 8.0;
	public static final float FINGER_DAMAGE = 8f;
	public static final int FINGER_BLEED_TICKS = 80;
	public static final float BLEED_DAMAGE = 1f;
	public static final int FINGER_COOLDOWN = 160;
	public static final float FINGER_COST = 8f;

	// ---------------------------------------------------------------- G: Power Grab
	public static final int GRAB_COOLDOWN = 20;
	public static final int THROW_COOLDOWN = 80;
	public static final float THROW_COST = 4f;
	public static final int GRAB_MAX_TICKS = 200;
	public static final double THROW_SPEED = 2.2;
	/** Blocks harder than this can't be pulled loose. */
	public static final float GRAB_MAX_HARDNESS = 3.0f;
	public static final int THROWN_TRACK_TICKS = 40;

	// ---------------------------------------------------------------- Shift+G: Ball Bearing Flick
	public static final int BEARINGS = 3;
	public static final double BEARING_RANGE = 32.0;
	public static final float BEARING_DAMAGE = 6f;
	public static final int FLICK_COOLDOWN = 100;
	public static final int FLICK_COOLDOWN_FREE = 240;
	public static final float FLICK_COST = 4f;

	// ---------------------------------------------------------------- X: Stand Leap / Shift+X: Stand Dash
	public static final double LEAP_FORWARD = 1.15;
	public static final double LEAP_UP = 1.25;
	public static final int LEAP_REBOUNDS = 3;
	public static final double REBOUND_SPEED = 1.0;
	public static final double REBOUND_UP = 0.9;
	public static final int LEAP_COOLDOWN = 80;
	public static final float LEAP_COST = 4f;
	public static final double DASH_SPEED = 1.6;
	public static final int DASH_TICKS = 10;
	public static final int DASH_COOLDOWN = 60;
	public static final float DASH_COST = 4f;

	// ---------------------------------------------------------------- Z: Star Breaker (hold)
	public static final int BREAKER_FULL_CHARGE = 30;
	public static final int BREAKER_AUTO_RELEASE = 60;
	public static final float BREAKER_MIN_DAMAGE = 8f;
	public static final float BREAKER_MAX_DAMAGE = 20f;
	public static final double BREAKER_KNOCKBACK = 3.0;
	public static final double BREAKER_LIFT = 0.6;
	public static final float BREAKER_SMASH_HARDNESS = 1.5f;
	public static final int BREAKER_COOLDOWN = 140;
	public static final float BREAKER_COST = 6f;

	// ---------------------------------------------------------------- Shift+Z: Star Platinum: The World
	/** v0.17.2: needs this much Stamina and spends it when time resumes (was 50, and the whole bar). */
	public static final float TIME_STOP_COST = 20f;
	public static final int TIME_STOP_COOLDOWN = 90 * 20;
	public static final int TIME_STOP_WINDED_TICKS = 60;
	/** v0.17.2 (user: "Remove the stand mastery ... Make the max time stop for star platinum 10 seconds"): always 10 s. */
	public static final int TIME_STOP_TICKS = 200;

	/** Queued damage on a player lands at most this fraction of their max health. */
	public static final float TIME_STOP_PLAYER_CAP = 0.30f;
	/** Bosses take this fraction of the queued damage... */
	public static final float TIME_STOP_BOSS_FACTOR = 0.5f;
	/** ...and at most this fraction of their max health. */
	public static final float TIME_STOP_BOSS_CAP = 0.15f;
	public static final double TIME_STOP_MAX_KNOCK = 3.0;

	// ---------------------------------------------------------------- V: Stand Guard (hold) / Shift+V: Precision Sight
	public static final float GUARD_FACTOR = 0.30f;
	public static final float GUARD_STAMINA_PER_TICK = 0.1f;
	public static final int GUARD_COOLDOWN = 20;
	public static final int SIGHT_MAX_TICKS = 600;
	public static final int SIGHT_COOLDOWN = 40;
	public static final double SIGHT_GLOW_RANGE = 32.0;

	// ---------------------------------------------------------------- C: Super Inhale / Shift+C: ORAAA Uppercut
	public static final int INHALE_TICKS = 40;
	public static final double INHALE_RANGE = 10.0;
	public static final double INHALE_PULL = 0.35;
	public static final int INHALE_COOLDOWN = 240;
	public static final float INHALE_COST = 8f;
	public static final float UPPERCUT_DAMAGE = 7f;
	public static final double UPPERCUT_LIFT = 1.4;
	public static final int UPPERCUT_COOLDOWN = 120;
	public static final float UPPERCUT_COST = 6f;

	// ---------------------------------------------------------------- N: pose
	/** Holding a pose this long gives Resistance I (refreshed while held). */
	public static final int POSE_RESIST_AFTER = 30;

	/** At or above this max health a creature is a boss (capped hits, never thrown about). */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	/** Sounds and particles reach Stand users within this many blocks. */
	public static final double FX_RANGE = 48.0;
}
