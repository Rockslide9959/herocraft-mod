package com.projecthero.mod.oathbreaker;

/**
 * Every tunable number for The Oathbreaker in one place (v0.14.0 hard requirement -- balancing this boss
 * should never mean hunting through {@code OathbreakerEntity}/{@code OathbreakerSummon} for a stray
 * constant). Filled in stage by stage as the three-phase duel-boss rework lands; see
 * {@code docs/OATHBREAKER_REFERENCE.md} for the design this backs.
 */
public final class OathbreakerTuning {
	private OathbreakerTuning() {
	}

	// ---------------- stats ----------------

	public static final float MAX_HEALTH_BASE = 4000.0f;
	/** Extra max-health fraction per additional player within {@link #HP_SCALE_RADIUS} at spawn. */
	public static final double HP_SCALE_PER_EXTRA_PLAYER = 0.40;
	/** Extra players beyond the first stop counting past this many (i.e. a 4-player cap total). */
	public static final int HP_SCALE_MAX_EXTRA_PLAYERS = 3;
	public static final double HP_SCALE_RADIUS = 48.0;

	public static final double ARMOR = 10.0;
	public static final double ARMOR_TOUGHNESS = 4.0;

	/** Normal knockback resistance; raised to {@link #KNOCKBACK_RESISTANCE_HYPER_ARMOR} during any
	 * wind-up or active attack so players can't knock him out of his own swing ("hyper armor"). */
	public static final double KNOCKBACK_RESISTANCE_BASE = 0.8;
	public static final double KNOCKBACK_RESISTANCE_HYPER_ARMOR = 1.0;

	public static final double MOVEMENT_SPEED_PHASE1 = 0.19; // v0.13.9: 0.15/0.18/0.22 -> 0.19/0.22/0.26 (~1.6/2.1/3.0 blocks/s)
	public static final double MOVEMENT_SPEED_PHASE2 = 0.22;
	public static final double MOVEMENT_SPEED_PHASE3 = 0.26;

	public static final double FOLLOW_RANGE = 48.0;
	public static final double BOSS_BAR_RADIUS = 48.0;
	public static final int XP_REWARD = 500;

	// ---------------- phases ----------------

	/** Phase 1 -> 2 ("Oath Shattered") triggers at or below this health fraction. */
	public static final float PHASE_2_HEALTH_FRACTION = 0.60f;
	/** Phase 2 -> 3 ("enrage") triggers at or below this health fraction. */
	public static final float PHASE_3_HEALTH_FRACTION = 0.25f;

	// ---------------- "Oath Shattered" (phase 1 -> 2) ----------------

	/** Scripted, invulnerable, no AI -- matches the {@code phase_transition} clip. */
	public static final int TRANSITION_TICKS = 60; // 3s
	/** Ticks into the clip at which the sword is driven into the ground: shockwave + texture swap. */
	public static final int TRANSITION_PLUNGE_TICKS = 30;
	public static final double SHOCKWAVE_RADIUS = 8.0;
	/** The ring expands from him to {@link #SHOCKWAVE_RADIUS} over this many ticks; each player is hit once
	 * as the front passes them. */
	public static final int SHOCKWAVE_EXPAND_TICKS = 8;
	public static final float SHOCKWAVE_DAMAGE = 12.0f;
	public static final double SHOCKWAVE_KNOCKBACK = 2.2;
	public static final double SHOCKWAVE_LIFT = 0.5;
	public static final float TRANSITION_SHAKE_INTENSITY = 1.0f;
	public static final int TRANSITION_SHAKE_TICKS = 25;
	public static final float TRANSITION_ZOOM = 0.15f;
	public static final int TRANSITION_ZOOM_TICKS = 25;

	// ---------------- spawn / death ----------------

	/** Ticks spent in the crouched spawn pose before AI/combat wakes up -- matches the 1.25s spawn clip. */
	public static final int SPAWN_TICKS = 25;
	/** The death: to both knees, sword planted, head bowed, fading into soul fire -- matches the
	 * {@code death} clip; he's removed when it ends. */
	public static final int DEATH_TICKS = 60; // 3s
	/** The body starts fading into soul particles this many ticks in (once he's settled on his knees). */
	public static final int DEATH_FADE_START_TICKS = 24;
	/** "The oath... is fulfilled." goes to every player this close. */
	public static final double DEATH_MESSAGE_RADIUS = 48.0;

	// ---------------- anti-cheese ----------------

	/** A target he can't reach (no path, pillared this far above him, or in water/lava) for this long gets
	 * a ranged answer: Soul Spear in phase 1, Chains of the Forsworn (which pull them down) in phase 2+. */
	public static final int UNREACHABLE_TICKS = 60; // 3s
	public static final double PILLAR_HEIGHT = 3.0;
	/** Pathfinding is re-checked this often (it's the expensive part). */
	public static final int PATH_CHECK_INTERVAL_TICKS = 10;
	/** Stuck (in a block, or in a pit/hole, making no headway toward a target out of reach) this long ->
	 * a short Leaping Cleave toward them to get out. */
	public static final int STUCK_TICKS = 100; // 5s
	public static final double STUCK_MOVE_EPSILON = 0.5;
	public static final double UNSTICK_LEAP_MAX_RANGE = 6.0;
	/** Soul Spear (phase-1 ranged answer): thrown off-hand with the chain-throw wind-up
	 * ({@link #CHAIN_THROW_TICKS}), then a fast, visible, dodgeable bolt. */
	public static final float SOUL_SPEAR_DAMAGE = 21.0f;
	public static final double SOUL_SPEAR_SPEED = 2.5;
	public static final double SOUL_SPEAR_REACH = 40.0;
	public static final double SOUL_SPEAR_HIT_RADIUS = 0.6;

	// ---------------- poise / stagger ----------------

	/** Hidden poise meter. Every point of incoming damage (before armor) drains one point of poise. */
	public static final float POISE_MAX = 400.0f;
	/** Poise snaps back to full after this long with no damage taken. */
	public static final int POISE_REGEN_DELAY_TICKS = 80; // 4s
	/** Poise broken: kneeling, no AI, punishable -- matches the {@code stagger} clip. */
	public static final int STAGGER_TICKS = 50; // 2.5s
	/** Incoming damage multiplier while staggered. */
	public static final float STAGGER_DAMAGE_MULTIPLIER = 1.30f;
	/** The {@code hit} flinch clip's length; only played when he isn't attacking or staggered. */
	public static final int FLINCH_TICKS = 6;
	/** Chance (1 in N) that a hit taken while idle plays the flinch. */
	public static final int FLINCH_ONE_IN = 3;

	// ---------------- shared attack infrastructure ----------------

	public static final int ATTACK_TRIGGER_RANGE = 5;
	/** Shared cooldown between attack sequences. v0.13.9: halved (2.5s/2s/1.5s -> 1.2s/0.9s/0.6s) -- he
	 * attacks far more often. */
	public static final int ATTACK_COOLDOWN_TICKS_PHASE1 = 24;
	public static final int ATTACK_COOLDOWN_TICKS_PHASE2 = 18;
	public static final int ATTACK_COOLDOWN_TICKS_PHASE3 = 12;
	/** v0.13.9: while off cooldown he re-faces the target at this rate (degrees/tick) instead of waiting on
	 * vanilla's lazy body rotation -- the reason a player standing still was sometimes never hit: his
	 * body (which every cone is measured along) lagged up to ~75 degrees behind his head. */
	public static final float TURN_DEGREES_PER_TICK = 30.0f;
	/** During a wind-up he keeps turning toward the target at this rate; once the strike commits, he doesn't. */
	public static final float WINDUP_TURN_DEGREES_PER_TICK = 20.0f;

	// ---------------- Stance Dash ----------------

	/** v0.14.0: wind-up cut 2s -> 1.5s per spec; the 2s post-stance punish window is unchanged. */
	public static final int STANCE_WINDUP_TICKS = 30; // 1.5s
	public static final int STANCE_DASH_TICKS = 6;
	/** Ticks into the {@code dash_attack} clip at which the blade visibly connects -- damage resolves here,
	 * not on the clip's first frame. */
	public static final int STANCE_DASH_CONTACT_TICKS = 2;
	/** The lunge itself: a scripted glide toward where the target stood when the dash committed, stopping
	 * {@link #STANCE_DASH_STOP_SHORT} short of them, between these bounds, over this many ticks. v0.13.9: was
	 * a fixed 4 blocks; it now reaches up to 9. */
	public static final double STANCE_DASH_LUNGE_MIN = 3.0;
	public static final double STANCE_DASH_LUNGE_DISTANCE = 9.0;
	public static final double STANCE_DASH_STOP_SHORT = 1.0;
	public static final int STANCE_DASH_LUNGE_TICKS = 4;
	/** v0.13.9: the dash is also his gap-closer -- a target this far away (and beyond melee trigger range)
	 * gets it, rolled {@link #STANCE_DASH_GAP_CHANCE} every {@link #STANCE_DASH_GAP_ROLL_TICKS}. */
	public static final double STANCE_DASH_TRIGGER_RANGE = 10.0;
	public static final float STANCE_DASH_GAP_CHANCE = 0.5f;
	public static final int STANCE_DASH_GAP_ROLL_TICKS = 10;
	/** v0.13.9: anyone the blade passes within this horizontal distance of along the whole lunge is cut
	 * (plus the usual cone at the end) -- a dash can no longer glide straight through you and miss. */
	public static final double STANCE_DASH_SWEEP_RADIUS = 2.0;
	public static final int STANCE_POST_TICKS = 40; // 2s
	public static final float STANCE_DAMAGE = 42.0f; // v0.13.9: every damage number +40%
	public static final double STANCE_RANGE = 6.0;
	public static final double STANCE_ARC_DEGREES = 70.0;
	public static final double STANCE_KNOCKBACK = 0.9;
	/** Phase 2+: 25% chance the wind-up is followed by a feint before the real dash. */
	public static final float STANCE_FEINT_CHANCE = 0.25f;
	/** The feint: weight shifts forward as if to dash, then settles back into the wind-up -- the real dash
	 * fires the moment this ends, i.e. 0.5s later than the player expected. Matches {@code dash_feint}. */
	public static final int STANCE_FEINT_TICKS = 10; // 0.5s

	// ---------------- Four/Five-Strike Combo ----------------

	public static final int COMBO_HITS_PHASE1 = 4;
	/** Phase 2+: a 5th hit (a thrust) is added. */
	public static final int COMBO_HITS_PHASE2 = 5;
	public static final int COMBO_WINDUP_TICKS = 10;
	/** Full length of each {@code combo_strike_N} clip (contact + follow-through). */
	public static final int COMBO_STRIKE_HOLD_TICKS = 7;
	/** Ticks into a strike clip at which the blade connects and damage resolves. */
	public static final int COMBO_STRIKE_CONTACT_TICKS = 2;
	public static final float COMBO_DAMAGE_PER_HIT = 14.0f;
	public static final double COMBO_RANGE = 4.0;
	public static final double COMBO_ARC_DEGREES = 80.0;
	public static final double COMBO_KNOCKBACK = 0.6;
	/** Phase 2+: each wind-up gets a random extra hold on top of {@link #COMBO_WINDUP_TICKS}. */
	public static final int COMBO_DELAY_MAX_TICKS = 12; // 0.6s
	/** Phase 2+: the 5th hit is a thrust -- longer reach, narrower cone. */
	public static final double COMBO_THRUST_RANGE = 4.5;
	public static final double COMBO_THRUST_ARC_DEGREES = 40.0;

	// ---------------- Oath Guard / Riposte ----------------

	public static final int GUARD_STANCE_TICKS = 30; // 1.5s
	public static final double GUARD_ARC_DEGREES = 100.0;
	/** Only offered if the target attacked the Oathbreaker within this many ticks. */
	public static final int GUARD_ELIGIBLE_AFTER_HIT_TICKS = 60; // 3s
	/** Only melee from within this distance can be parried (anything further is a projectile or power). */
	public static final double GUARD_MELEE_REACH = 6.0;
	public static final int RIPOSTE_TICKS = 8; // 0.4s
	/** Ticks into the {@code riposte} clip at which the thrust lands. */
	public static final int RIPOSTE_CONTACT_TICKS = 3;
	public static final float RIPOSTE_DAMAGE = 26.0f;
	public static final double RIPOSTE_RANGE = 4.5;
	public static final double RIPOSTE_ARC_DEGREES = 60.0;
	public static final double RIPOSTE_KNOCKBACK = 1.6;

	// ---------------- Backstep ----------------

	/** Chance, after any attack finishes with the target still this close, to hop backward instead of
	 * going straight back to idle/cooldown. */
	public static final float BACKSTEP_CHANCE = 0.30f;
	public static final double BACKSTEP_TRIGGER_RANGE = 2.5;
	public static final int BACKSTEP_TICKS = 10; // 0.5s
	public static final double BACKSTEP_DISTANCE = 3.0;
	/** The airborne part of the hop (the rest of {@link #BACKSTEP_TICKS} is the landing settle). */
	public static final int BACKSTEP_HOP_TICKS = 6;
	public static final double BACKSTEP_ARC_HEIGHT = 0.8;

	// ---------------- Leaping Cleave ----------------

	public static final double LEAP_MIN_RANGE = 8.0;
	public static final double LEAP_MAX_RANGE = 20.0;
	/** Target must have stayed in the leap band this long before it triggers. */
	public static final int LEAP_KITE_TICKS = 60; // 3s
	public static final int LEAP_WINDUP_TICKS = 12; // 0.6s
	/** Flight time -- the landing spot is locked at takeoff, so this is the dodge window. */
	public static final int LEAP_AIR_TICKS = 16; // 0.8s
	/** The overhead chop on landing plus the heavy settle -- the punish window. */
	public static final int LEAP_LAND_TICKS = 16; // 0.8s
	public static final double LEAP_ARC_HEIGHT_BASE = 4.0;
	public static final double LEAP_ARC_HEIGHT_PER_BLOCK = 0.2;
	public static final float LEAP_DAMAGE = 28.0f;
	/** Damage at the very edge of {@link #LEAP_RADIUS}, as a fraction of {@link #LEAP_DAMAGE}. */
	public static final float LEAP_EDGE_DAMAGE_FRACTION = 0.5f;
	public static final double LEAP_RADIUS = 4.0;
	public static final double LEAP_KNOCKBACK = 1.2;

	// ---------------- Soul Rend (phase 2+) ----------------

	/** Drags the sword along the ground behind him -- matches {@code soul_rend_windup}. */
	public static final int SOUL_REND_WINDUP_TICKS = 16; // 0.8s
	/** For the last this-many ticks of the wind-up the line's direction is LOCKED and soul particles
	 * flicker along the whole length -- the 0.5s "sidestep now" telegraph before the first eruption. */
	public static final int SOUL_REND_TELEGRAPH_TICKS = 10;
	/** The rising slash -- matches {@code soul_rend_strike}. */
	public static final int SOUL_REND_STRIKE_TICKS = 12; // 0.6s
	public static final int SOUL_REND_CONTACT_TICKS = 2;
	/** Eruptions walk this many blocks out along the locked line, one block every
	 * {@link #SOUL_REND_TICKS_PER_BLOCK} ticks, starting on the slash's contact frame. */
	public static final int SOUL_REND_LENGTH = 12;
	public static final int SOUL_REND_TICKS_PER_BLOCK = 2;
	public static final double SOUL_REND_RADIUS = 1.2;
	public static final float SOUL_REND_DAMAGE = 17.0f;
	public static final int SOUL_REND_FIRE_TICKS = 60; // 3s

	// ---------------- Chains of the Forsworn (phase 2+) ----------------

	public static final double CHAIN_MIN_RANGE = 6.0;
	public static final double CHAIN_MAX_RANGE = 16.0;
	/**
	 * The spec gives Chains weight 10 "only when in range" -- but its range (6-16) never overlaps the 5-block
	 * melee trigger every other attack uses, so a plain weighted pick would make it the ONLY eligible attack
	 * out there and he'd throw it after every single cooldown. Instead, while the target sits in the band
	 * and he's off cooldown, he rolls this chance once every {@link #CHAIN_ROLL_INTERVAL_TICKS}.
	 */
	public static final float CHAIN_RANGED_CHANCE = 0.35f;
	public static final int CHAIN_ROLL_INTERVAL_TICKS = 20;
	/** Wind-up before the throw -- matches {@code chain_throw}. */
	public static final int CHAIN_THROW_TICKS = 10; // 0.5s
	/** Chain head speed (blocks/tick) and how far it flies before it gives up. */
	public static final double CHAIN_SPEED = 1.6;
	public static final double CHAIN_REACH = 18.0;
	/** How close the head has to pass to a player to catch them. */
	public static final double CHAIN_HIT_RADIUS = 0.9;
	/** The yank toward him -- matches {@code chain_pull}; the victim stops this far in front of him. */
	public static final int CHAIN_PULL_TICKS = 10; // 0.5s
	public static final double CHAIN_PULL_STOP_DISTANCE = 2.5;
	/** A miss: the chain retracts and he's open -- matches {@code chain_recover}. */
	public static final int CHAIN_RECOVER_TICKS = 20; // 1s

	// ---------------- Enrage (phase 2 -> 3) ----------------

	/** A roaring stance -- NOT invulnerable (spec), but no AI and no stagger. Matches {@code enrage}. */
	public static final int ENRAGE_TICKS = 30; // 1.5s
	/** Ticks into the clip at the roar's peak: the shake fires and the brighter phase-3 texture swaps in. */
	public static final int ENRAGE_ROAR_TICKS = 10;
	public static final float ENRAGE_SHAKE_INTENSITY = 0.9f;
	public static final int ENRAGE_SHAKE_TICKS = 25;

	// ---------------- Judgement (phase 3) ----------------

	/** Its own cooldown, separate from the shared one; rolled 50% whenever it's up. */
	public static final int JUDGEMENT_COOLDOWN_TICKS = 400; // 20s
	public static final float JUDGEMENT_USE_CHANCE = 0.5f;
	/** Straight up this far -- matches {@code judgement_rise}. */
	public static final double JUDGEMENT_RISE_HEIGHT = 8.0;
	public static final int JUDGEMENT_RISE_TICKS = 10;
	/** Hangs in the air tracking the target, a soul beam pointing at where he'll land -- matches
	 * {@code judgement_hang}. The landing spot LOCKS when this ends. */
	public static final int JUDGEMENT_HANG_TICKS = 20;
	/** The whole slam clip ({@code judgement_slam}); the fall itself is the first
	 * {@link #JUDGEMENT_FALL_TICKS}, the impact lands on that frame, the rest is recovery. */
	public static final int JUDGEMENT_SLAM_TICKS = 24;
	public static final int JUDGEMENT_FALL_TICKS = 5;
	public static final double JUDGEMENT_RADIUS = 6.0;
	public static final float JUDGEMENT_DAMAGE_CENTER = 48.0f;
	public static final float JUDGEMENT_DAMAGE_EDGE = 14.0f;
	public static final double JUDGEMENT_KNOCKBACK = 1.8;
	public static final float JUDGEMENT_SHAKE_INTENSITY = 1.2f;
	public static final int JUDGEMENT_SHAKE_TICKS = 30;
	public static final float JUDGEMENT_ZOOM = 0.2f;
	public static final int JUDGEMENT_ZOOM_TICKS = 20;
	/** The lingering soul-fire circle left behind: everything inside burns until the players reposition. */
	public static final double JUDGEMENT_RING_RADIUS = 6.0;
	public static final float JUDGEMENT_RING_DAMAGE_PER_SECOND = 6.0f;
	public static final int JUDGEMENT_RING_TICKS = 100; // 5s

	// ---------------- Execution (phase 3, unblockable grab) ----------------

	public static final int EXECUTION_COOLDOWN_TICKS = 500; // 25s
	public static final float EXECUTION_USE_CHANCE = 0.5f;
	/** Red flash + warden charge, then this wind-up -- matches {@code execution_windup}. */
	public static final int EXECUTION_WINDUP_TICKS = 16; // 0.8s
	/** The short lunge (matches {@code execution_lunge}); the grab checks at its end. */
	public static final int EXECUTION_LUNGE_TICKS = 4;
	public static final double EXECUTION_LUNGE_DISTANCE = 1.8;
	public static final double EXECUTION_GRAB_RANGE = 2.5;
	public static final double EXECUTION_GRAB_ARC_DEGREES = 60.0;
	/** Victim held up in front of him, no movement -- matches {@code execution_hold}. */
	public static final int EXECUTION_HOLD_TICKS = 30; // 1.5s
	/** Where the held victim hangs: this far in front of him, at this fraction of his height (their feet
	 * well off the ground). */
	public static final double EXECUTION_HOLD_FORWARD = 1.4;
	public static final double EXECUTION_HOLD_HEIGHT_FRACTION = 0.42;
	/** Other players dealing this much (raw) damage to him during the hold free the victim and stagger him. */
	public static final float EXECUTION_ESCAPE_DAMAGE = 150.0f;
	/** The impale -- matches {@code execution_impale}; the blade goes in on the contact frame. */
	public static final int EXECUTION_IMPALE_TICKS = 16;
	public static final int EXECUTION_IMPALE_CONTACT_TICKS = 6;
	public static final float EXECUTION_DAMAGE = 55.0f;
	public static final double EXECUTION_THROW_KNOCKBACK = 2.4;
	/** Whiffed grab -- matches {@code execution_whiff}; the punish window. */
	public static final int EXECUTION_WHIFF_TICKS = 30; // 1.5s

	// ---------------- Phantom Echo (phase 3 passive) ----------------

	/** Each Combo/Stance Dash arc is repeated by a ghost from where he stood, this long after it landed. */
	public static final int ECHO_DELAY_TICKS = 20; // 1s
	public static final float ECHO_DAMAGE_FRACTION = 0.6f;

	// ---------------- weights ----------------

	/** Phase 1, once in trigger range. Oath Guard only if the target hit him recently. */
	public static final int WEIGHT_P1_STANCE_DASH = 35;
	public static final int WEIGHT_P1_COMBO = 45;
	public static final int WEIGHT_P1_OATH_GUARD = 20;
	/** Phase 2 melee pool (Chains is rolled separately at range -- see {@link #CHAIN_RANGED_CHANCE}). */
	public static final int WEIGHT_P2_STANCE_DASH = 25;
	public static final int WEIGHT_P2_COMBO = 35;
	public static final int WEIGHT_P2_SOUL_REND = 20;
	public static final int WEIGHT_P2_OATH_GUARD = 10;
	/** Phase 3 melee pool; Judgement and Execution sit outside the weights (their own cooldowns, 50% when up). */
	public static final int WEIGHT_P3_STANCE_DASH = 20;
	public static final int WEIGHT_P3_COMBO = 30;
	public static final int WEIGHT_P3_SOUL_REND = 20;
	public static final int WEIGHT_P3_OATH_GUARD = 5;
}
