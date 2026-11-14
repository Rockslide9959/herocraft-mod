package com.projecthero.mod.stand;

/**
 * v0.17.2: the Stand numbers (Star Platinum, the first Stand). Ticks unless named otherwise; damage in half-hearts.
 * v0.17.3: the user's rework list ("Star Platinum rework") set most of them -- each is marked with the user's words.
 * Design: {@code docs/STAR_PLATINUM_DESIGN.md}.
 */
public final class StandConfig {
	private StandConfig() {
	}

	// ---------------------------------------------------------------- Stamina
	public static final float STAMINA_MAX = 100f;
	/** v0.17.3 (user: "stand stamina regens 3 stamina per second"): one flat rate, in or out of a fight. */
	public static final float STAMINA_REGEN = 3f;
	/** v0.17.3 (user: "after time stop ends stamina regenerates 50% slower for 20 seconds"). */
	public static final float REGEN_SLOW_FACTOR = 0.5f;
	public static final int REGEN_SLOW_TICKS = 20 * 20;
	/** v0.17.4 (user: "make stand stamina only regenerate 1 second after not using stamina"): every spend restarts it. */
	public static final int STAMINA_REGEN_DELAY_TICKS = 20;

	/** Range C: how far Star Platinum reaches from you. */
	public static final double REACH = 3.5;
	/** Extra entity reach on empty-hand punches while the Stand is out. */
	public static final double PUNCH_REACH_BONUS = 1.0;

	// ---------------------------------------------------------------- H: summon
	public static final int SUMMON_TOGGLE_COOLDOWN = 10;
	/** v0.17.3 (user: "make him come out of the player"): it slides out of its user's body back to its spot over 0.4 s. */
	public static final int SUMMON_ANIM_TICKS = 8;

	// ---------------------------------------------------------------- Stand punches (empty-hand left click, Stand out)
	/** v0.17.2 (user): "increase his melee damage to 8" (the third hit of the chain keeps its lead, 12). */
	public static final float PUNCH_DAMAGE = 8f;
	public static final float PUNCH_FINISHER_DAMAGE = 12f;
	public static final double PUNCH_FINISHER_KNOCKBACK = 1.3;
	public static final int PUNCH_COMBO_WINDOW = 25;

	// ---------------------------------------------------------------- R: tap = Platinum Smash, hold = ORA Barrage
	/**
	 * v0.17.3: R held this long (0.3 s) is the barrage; let go sooner and it was a tap (one Platinum Smash hit, on the release).
	 * A quick tap is 1-4 ticks; 6 leaves room for a slow finger without making the barrage feel late.
	 */
	public static final int HOLD_THRESHOLD = 6;
	/** v0.17.3 (user: "first hit deals 8 damage, second hit deals 8 damage, third hit deals 8 damage and last hit deals 12 damage"). */
	public static final float SMASH_DAMAGE = 8f;
	public static final float SMASH_FINAL_DAMAGE = 12f;
	public static final int SMASH_HITS = 4;
	/** v0.17.3 (user: "enemies that get hit with the first 3 hits get stunned in place for half a second"). */
	public static final int SMASH_STUN_TICKS = 10;
	/** The last hit's knockback (blocks/tick, flat) and lift. */
	public static final double SMASH_FINAL_KNOCKBACK = 1.4;
	public static final double SMASH_FINAL_LIFT = 0.35;
	/** v0.17.3 (user: "3 second cooldown after doing the last hit"). */
	public static final int SMASH_COOLDOWN = 60;
	/** v0.17.3 (user: "ability goes on cooldown if user doesn't complete the combo and their last R input was 2 seconds ago"). */
	public static final int SMASH_RESET_TICKS = 40;
	/** v0.17.3 (user: "each hit should drain 4 stamina"). */
	public static final float SMASH_COST = 4f;

	/** v0.17.3 (user: "every punch deals 8 damage"): one punch every 4 ticks (5 a second). */
	public static final int BARRAGE_HIT_INTERVAL = 4;
	public static final float BARRAGE_HIT_DAMAGE = 8f;
	/** v0.17.3 (user: "drains 3 stamina every second that the ability is active"). */
	public static final float BARRAGE_STAMINA_PER_SECOND = 3f;
	/** Each hit holds its victim still this long (refreshed by the next). */
	public static final int BARRAGE_STUN_TICKS = 10;
	/** v0.17.3 (user: "once it ends they get launched 5 blocks"): the launch velocity for about 5 blocks. */
	public static final double BARRAGE_LAUNCH_SPEED = 0.62;
	public static final double BARRAGE_LAUNCH_LIFT = 0.4;
	/** The barrage is cut off after this long (5 s). */
	public static final int BARRAGE_MAX_TICKS = 100;
	public static final int BARRAGE_COOLDOWN = 100;
	/**
	 * v0.17.2 (user: "make the time stop voice line play first and only have time stop ability activate when the time stop part of
	 * the sound effect happens"): ticks from Shift+Z to the stop -- "Star Platinum: The World!" runs 1.9 s and the stop's burst
	 * lands at 1.93 s of time_stop.ogg.
	 */
	public static final int TIME_STOP_WINDUP_TICKS = 39;

	// ---------------------------------------------------------------- Shift+R: Star Finger
	/** v0.17.3 (user: "finger extends 30 blocks and attacks all enemies that it hits in that 30 block range"). */
	public static final double FINGER_RANGE = 30.0;
	/** v0.17.3 (user: "dealing 22 damage"). */
	public static final float FINGER_DAMAGE = 22f;
	/** v0.17.3 (user: "finger extends over the course of 1 second"). */
	public static final int FINGER_EXTEND_TICKS = 20;
	/** Held out at full length, then drawn back. */
	public static final int FINGER_HOLD_TICKS = 6;
	public static final int FINGER_RETRACT_TICKS = 6;
	public static final int FINGER_COOLDOWN = 160;
	/** v0.17.3 (user: "drains 15 stand energy"). */
	public static final float FINGER_COST = 15f;

	// ---------------------------------------------------------------- G: Ora Chase
	/** v0.17.3 (user: "star platinum moves forward 10 blocks 3 blocks per second"). */
	public static final double CHASE_SPEED = 3.0 / 20.0;
	public static final double CHASE_RANGE = 10.0;
	/** It grabs the first creature this close to its fist. */
	public static final double CHASE_GRAB_RADIUS = 0.9;
	/** v0.17.3 (user: "barrages them with one hand for 3 seconds"). */
	public static final int CHASE_BARRAGE_TICKS = 60;
	/** v0.17.3 (user: "the barrage deals 15 damage over 3 seconds"): 6 punches of 2.5, one every 10 ticks. */
	public static final float CHASE_BARRAGE_DAMAGE = 15f;
	public static final int CHASE_BARRAGE_HITS = 6;
	/** v0.17.3 (user: "throwing them 10 blocks away ... the knockback deals another 10 damage"). */
	public static final float CHASE_THROW_DAMAGE = 10f;
	public static final double CHASE_THROW_SPEED = 1.1;
	public static final double CHASE_THROW_LIFT = 0.45;
	/** How far above the grab point the victim is lifted by the neck (blocks). */
	public static final double CHASE_LIFT = 1.0;
	/** v0.17.3 (user: "drains 15 stamina"). */
	public static final float CHASE_COST = 15f;
	public static final int CHASE_COOLDOWN = 160;
	/** After the throw (or a miss) the Stand flies home this long before it is back at your side. */
	public static final int CHASE_RETURN_TICKS = 10;

	// ---------------------------------------------------------------- Shift+G: Ball Bearing Flick
	/** v0.17.3 (user: "player flicks a ball bearing that deals 20 damage"). */
	public static final float BEARING_DAMAGE = 20f;
	/** Blocks a tick (a fast shot, 80 b/s) and how long it flies before it drops. */
	public static final double BEARING_SPEED = 4.0;
	public static final int BEARING_LIFE = 30;
	public static final int FLICK_COOLDOWN = 100;
	public static final float FLICK_COST = 4f;

	// ---------------------------------------------------------------- X: Stand Jump / Shift+X: Ora Rush
	/** v0.17.3 (user: "allows the player to jump about 15 blocks in the direction they are looking in"). */
	public static final double JUMP_SPEED = 1.9;
	/** However flat the look, the jump leaves the ground at least this fast upward. */
	public static final double JUMP_MIN_UP = 0.55;
	/** v0.17.3 (user: "2 second cooldown"). */
	public static final int JUMP_COOLDOWN = 40;
	public static final float JUMP_COST = 4f;
	/** v0.17.3 (user: "Make this a mobile attacking move"): Ora Rush, an 8-block dash behind Star Platinum's fists. */
	public static final double ORA_RUSH_SPEED = 1.0;
	public static final int ORA_RUSH_TICKS = 8;
	public static final int ORA_RUSH_HIT_INTERVAL = 2;
	public static final float ORA_RUSH_HIT_DAMAGE = 6f;
	public static final double ORA_RUSH_REACH = 2.6;
	public static final int ORA_RUSH_COOLDOWN = 100;
	public static final float ORA_RUSH_COST = 10f;

	// ---------------------------------------------------------------- Z: Star Breaker (hold)
	public static final int BREAKER_FULL_CHARGE = 30;
	public static final int BREAKER_AUTO_RELEASE = 60;
	/** v0.17.3 (user: "make this deal 30 damage"): every release; the charge adds knockback and the block smash. */
	public static final float BREAKER_DAMAGE = 30f;
	public static final double BREAKER_KNOCKBACK = 3.0;
	public static final double BREAKER_LIFT = 0.6;
	public static final float BREAKER_SMASH_HARDNESS = 1.5f;
	public static final int BREAKER_COOLDOWN = 140;
	public static final float BREAKER_COST = 6f;

	// ---------------------------------------------------------------- Shift+Z: Star Platinum: The World
	/** v0.17.3: starting the stop needs one second of it. */
	public static final float TIME_STOP_MIN_STAMINA = 10f;
	/** v0.17.3 (user: "time stop drains 10 stamina per second"): it lasts while there is Stamina (or until Shift+Z again). */
	public static final float TIME_STOP_STAMINA_PER_SECOND = 10f;
	/** v0.17.3 (user: "player deals 30% increased damage during time stop"). */
	public static final float TIME_STOP_DAMAGE_BONUS = 1.3f;
	/**
	 * v0.17.3 (user: "player can fly at 10 blocks per second during time stop"): the vanilla flying speed giving 10 b/s (0.05 flies
	 * about 10.9 b/s).
	 */
	public static final float TIME_STOP_FLY_SPEED = 0.046f;
	public static final int TIME_STOP_COOLDOWN = 90 * 20;
	/** A safety cap: a full bar at 10 a second is exactly this long. */
	public static final int TIME_STOP_TICKS = 200;

	/** Queued damage on a player lands at most this fraction of their max health. */
	public static final float TIME_STOP_PLAYER_CAP = 0.30f;
	/** Bosses take this fraction of the queued damage... */
	public static final float TIME_STOP_BOSS_FACTOR = 0.5f;
	/** ...and at most this fraction of their max health. */
	public static final float TIME_STOP_BOSS_CAP = 0.15f;
	public static final double TIME_STOP_MAX_KNOCK = 3.0;

	// ---------------------------------------------------------------- V: Stand Guard (hold) / Shift+V: Stand Grab
	/** v0.17.3 (user: "blocks 60% of damage"): the damage let through. */
	public static final float GUARD_FACTOR = 0.40f;
	/** v0.17.3 (user: "remove time limit but when player releases V it goes on a 6 second cooldown"). */
	public static final int GUARD_COOLDOWN = 120;
	/** v0.17.3 (user: "pressing R during this does a 5 hit combo that deals a total of 30 damage"). */
	public static final int GRAB_COMBO_HITS = 5;
	public static final float GRAB_COMBO_DAMAGE = 30f;
	/** At least this long between two combo punches. */
	public static final int GRAB_COMBO_GAP = 4;
	public static final double GRAB_LAST_KNOCKBACK = 1.2;
	public static final int GRAB_COOLDOWN = 60;
	public static final double THROW_SPEED = 2.2;
	public static final int THROWN_TRACK_TICKS = 40;

	// ---------------------------------------------------------------- C: Super Inhale / Shift+C: ORAAA Uppercut
	public static final int INHALE_TICKS = 40;
	public static final double INHALE_RANGE = 10.0;
	public static final double INHALE_PULL = 0.35;
	/** v0.17.3 (user: "lower cooldown to 4 seconds"). */
	public static final int INHALE_COOLDOWN = 80;
	public static final float INHALE_COST = 8f;
	/** v0.17.3 (user: "increase this damage to 22"). */
	public static final float UPPERCUT_DAMAGE = 22f;
	public static final double UPPERCUT_LIFT = 1.4;
	public static final int UPPERCUT_COOLDOWN = 120;
	public static final float UPPERCUT_COST = 6f;

	// ---------------------------------------------------------------- N: pose
	/** v0.17.2 (user: "make this the only pose thats availible in the pose menu for now"): Jotaro's point. */
	public static final int POSE_COUNT = 1;
	/** Holding a pose this long gives Resistance I (refreshed while held). */
	public static final int POSE_RESIST_AFTER = 30;

	/** At or above this max health a creature is a boss (capped hits, never thrown about). */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	/** Sounds and particles reach Stand users within this many blocks. */
	public static final double FX_RANGE = 48.0;
}
