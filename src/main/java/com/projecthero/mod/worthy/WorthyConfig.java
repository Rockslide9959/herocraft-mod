package com.projecthero.mod.worthy;

/**
 * v0.17.7 Thor x Super Soldier ("worthy by grant"): every number of the lent-weapon kits in one place, so the user can set them.
 * Plain constants; ticks unless the name says otherwise; damage in half-hearts. The user: "the scaling for these movesets should be
 * powerful, it should be comparable to shazam since the powers are being lent to you by a god" -- so they sit on ShazamConfig's scale
 * (fist 17, combo 17/17/19, abilities 16-20, ultimate 40): left click 17 / 18, combo hits 16-19, abilities 16-20, ultimates 40;
 * Stormbreaker a little heavier per hit than Mjolnir, the shield + weapon kit in between. No tier cap on top of these and NOT the
 * Super Soldier's own {@code SuperSoldierConfig.MAX_HIT} (20): every worthy hit goes through {@code WorthyMoves.hit}, never
 * {@code SuperSoldierAbilities.strike}. Only the usual boss cap applies ({@link #BOSS_MAX_FRACTION_PER_HIT}).
 */
public final class WorthyConfig {
	private WorthyConfig() {
	}

	// ================================================================== lending
	/** How often (ticks) every loan is re-checked: still squadmates, still a Super Soldier. */
	public static final int VALIDATE_EVERY_TICKS = 20;
	/** The lending flourish: a visual bolt on the Super Soldier and a spark line between the two. */
	public static final double LEND_MAX_DISTANCE = 6.0;
	/**
	 * R with a lent weapon that is not in his hands ("Call Mjolnir" / "Call Stormbreaker"): the gap between two calls, and the busy
	 * lock it sets. The weapon comes by Thor's own recall (from his pack at once, else flying in).
	 */
	public static final int CALL_WEAPON_CD = 20;
	public static final int CALL_WEAPON_LOCK = 4;

	// ================================================================== shared
	/** Minimum gap between two moves (a busy lock). */
	public static final int GLOBAL_LOCK_TICKS = 4;
	/** A boss (max health >= {@link #BOSS_HEALTH}) takes at most this fraction of its max health per hit, and is never knocked back. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.06f;
	public static final float BOSS_HEALTH = 300.0f;
	/** His leaps / launches never cause fall damage for this long. */
	public static final int NO_FALL_TICKS = 100;

	/** Melee (left click) with the lent weapon in the main hand: the whole hit, whatever the item's own bonus. */
	public static final double MJOLNIR_MELEE = 17.0;
	public static final double STORMBREAKER_MELEE = 18.0;
	/** Mjolnir's own item bonus (ThorKit.MJOLNIR_ITEM_BONUS) -- what the melee modifier makes up the difference from. */
	public static final double MJOLNIR_ITEM_BONUS = 10.0;

	/** The Stormbreaker versions of the shared moves hit this much harder (the axe is the heavier weapon). */
	public static final float AXE_BONUS = 1.0f;

	// ================================================================== Mjolnir only ("Cap wields the hammer")
	// R: Hammer Combo -- three swings on the enemy in front
	public static final int HAMMER_COMBO_CD = 100;
	public static final double HAMMER_COMBO_RANGE = 4.5;
	public static final float HAMMER_COMBO_HIT = 16.0f;
	public static final float HAMMER_COMBO_FINISHER = 18.0f;
	/** The finisher's little spark burst around the target. */
	public static final float HAMMER_COMBO_BURST = 14.0f;
	public static final double HAMMER_COMBO_BURST_RADIUS = 2.5;
	/** Ticks between the three swings. */
	public static final int HAMMER_COMBO_INTERVAL = 5;
	// Shift+R: Thunder Uppercut
	public static final int UPPERCUT_CD = 160;
	public static final double UPPERCUT_RANGE = 4.0;
	public static final float UPPERCUT_DAMAGE = 17.0f;
	public static final double UPPERCUT_LIFT = 1.0;
	// G: Call Lightning
	public static final int CALL_LIGHTNING_CD = 120;
	public static final double CALL_LIGHTNING_RANGE = 24.0;
	public static final float CALL_LIGHTNING_DAMAGE = 18.0f;
	public static final float CALL_LIGHTNING_SPLASH = 12.0f;
	public static final double CALL_LIGHTNING_SPLASH_RADIUS = 2.5;
	/** The struck target is jolted still for this long (Slowness IV; bosses immune). */
	public static final int CALL_LIGHTNING_STUN_TICKS = 10;
	// Shift+G: Thunderclap -- the hammer driven into the ground, a cone shockwave
	public static final int THUNDERCLAP_CD = 180;
	public static final double THUNDERCLAP_RANGE = 7.0;
	/** Cosine of the cone's half angle (0.5 = 120 degrees wide). */
	public static final double THUNDERCLAP_MIN_DOT = 0.5;
	public static final float THUNDERCLAP_DAMAGE = 16.0f;
	public static final double THUNDERCLAP_KNOCKBACK = 1.4;
	// Z: Thunder Slam -- a hop, then the slam
	public static final int SLAM_CD = 220;
	public static final double SLAM_HOP = 0.75;
	public static final double SLAM_RADIUS = 5.0;
	public static final float SLAM_DAMAGE = 18.0f;
	public static final double SLAM_KNOCKBACK = 1.2;
	public static final double SLAM_LIFT = 0.4;
	/** The slam lands when he touches down, or after this long at most. */
	public static final int SLAM_MAX_TICKS = 50;
	// Shift+Z (ultimate): Worthy Storm
	public static final int ULTIMATE_CD = 1800;
	public static final double STORM_RADIUS = 14.0;
	public static final int STORM_BOLTS = 5;
	public static final float STORM_BOLT_DAMAGE = 16.0f;
	public static final double STORM_BURST_RADIUS = 6.0;
	public static final float STORM_BURST_DAMAGE = 40.0f;
	/** Timeline: the bolt into the raised hammer, the first and last of the bolts on enemies, the burst. */
	public static final int STORM_CHARGE_T = 10;
	public static final int STORM_FIRST_BOLT_T = 14;
	public static final int STORM_BOLT_EVERY = 4;
	public static final int STORM_BURST_T = 36;
	// C / right click: Hammer Throw
	public static final int HAMMER_THROW_CD = 60;
	public static final double HAMMER_THROW_RANGE = 20.0;
	public static final int HAMMER_THROW_PIERCE = 3;
	public static final float HAMMER_THROW_DAMAGE = 17.0f;
	/** Flight time out (at most) and back. */
	public static final int THROW_OUT_TICKS = 10;
	public static final int THROW_BACK_TICKS = 8;

	// ================================================================== Stormbreaker only ("Cap with the axe")
	// R: Axe Cleaves -- two sweeping cleaves
	public static final int AXE_CLEAVES_CD = 120;
	public static final double AXE_CLEAVE_RANGE = 3.5;
	/** Cosine of the sweep's half angle (0.64 = ~100 degrees). */
	public static final double AXE_CLEAVE_MIN_DOT = 0.64;
	public static final float AXE_CLEAVE_FIRST = 17.0f;
	public static final float AXE_CLEAVE_SECOND = 19.0f;
	public static final int AXE_CLEAVE_INTERVAL = 8;
	// Shift+R: Rising Cleave
	public static final int RISING_CLEAVE_CD = 180;
	public static final float RISING_CLEAVE_DAMAGE = 18.0f;
	// G: Lightning Cleave -- a lightning line along the ground
	public static final int LIGHTNING_CLEAVE_CD = 140;
	public static final double LIGHTNING_CLEAVE_LENGTH = 12.0;
	public static final double LIGHTNING_CLEAVE_WIDTH = 1.2;
	public static final float LIGHTNING_CLEAVE_DAMAGE = 19.0f;
	// Shift+G: Executioner's Chop -- a leap onto the enemy
	public static final int EXEC_CHOP_CD = 220;
	public static final double EXEC_CHOP_RANGE = 9.0;
	public static final float EXEC_CHOP_DAMAGE = 20.0f;
	/** Under this fraction of max health the chop does x{@link #EXEC_CHOP_LOW_MULT}. */
	public static final float EXEC_CHOP_LOW_HEALTH = 0.3f;
	public static final float EXEC_CHOP_LOW_MULT = 1.25f;
	// Z: Storm Crash
	public static final int CRASH_CD = 240;
	public static final double CRASH_RADIUS = 4.5;
	public static final float CRASH_DAMAGE = 19.0f;
	public static final double CRASH_KNOCKBACK = 1.6;
	// Shift+Z (ultimate): Stormbreaker's Verdict
	public static final double VERDICT_RANGE = 24.0;
	public static final float VERDICT_DAMAGE = 40.0f;
	public static final double VERDICT_BURST_RADIUS = 6.0;
	public static final float VERDICT_BURST_DAMAGE = 18.0f;
	/** Timeline: the axe leaves, strikes (the column), the burst, home. */
	public static final int VERDICT_THROW_T = 6;
	public static final int VERDICT_STRIKE_T = 14;
	public static final int VERDICT_BURST_T = 20;
	public static final int VERDICT_HOME_T = 34;
	// C / right click: Axe Throw
	public static final int AXE_THROW_CD = 80;
	public static final double AXE_THROW_RANGE = 18.0;
	public static final int AXE_THROW_PIERCE = 2;
	public static final float AXE_THROW_DAMAGE = 18.0f;
	public static final int AXE_THROW_SLOW_TICKS = 40;

	// ================================================================== both weapon-only kits
	// X: Hammer / Axe Dash on the ground, Flight Boost in the air
	public static final int DASH_CD = 80;
	public static final double DASH_SPEED_MJOLNIR = 1.6;
	public static final double DASH_SPEED_STORMBREAKER = 1.35;
	public static final int FLIGHT_BOOST_TICKS = 50;
	public static final double FLIGHT_BOOST_MULT = 1.5;
	// Shift+X: Launch (the weapon raised, then up and forward; flight starts at the top). v0.17.7 (user: "remove the hammer spinning
	// animation when flight starts"): no whirl any more, the weapon is simply raised
	public static final int LAUNCH_CD = 120;
	public static final double LAUNCH_UP = 1.35;
	public static final double LAUNCH_FORWARD = 1.0;
	/** Flight starts this many ticks after the launch (the push comes at tick 4). */
	public static final int LAUNCH_FLIGHT_T = 8;
	// V: Whirl Guard -- every hit from the front blocked; melee attackers shocked
	public static final int GUARD_CD = 200;
	public static final int GUARD_TICKS = 30;
	public static final float GUARD_SHOCK = 16.0f;
	// Shift+V: Rally Bolt -- a bolt on himself, buffs for him and his squad
	public static final int RALLY_CD = 800;
	public static final double RALLY_RADIUS = 10.0;
	public static final int RALLY_TICKS = 160;
	public static final double RALLY_PUSH_RADIUS = 6.0;
	public static final double RALLY_PUSH = 1.0;

	// ================================================================== shield + weapon ("dual wield")
	// R: Shield and Hammer -- a shield bash, then the weapon
	public static final int SHIELD_STRIKE_CD = 120;
	public static final double SHIELD_STRIKE_RANGE = 4.0;
	public static final float SHIELD_STRIKE_BASH = 16.0f;
	public static final float SHIELD_STRIKE_WEAPON = 18.0f;
	public static final int SHIELD_STRIKE_GAP = 6;
	// Shift+R: Bash Launcher -- shield uppercut, the weapon spiked down after it
	public static final int BASH_LAUNCH_CD = 180;
	public static final float BASH_LAUNCH_DAMAGE = 16.0f;
	public static final float BASH_LAUNCH_SPIKE = 17.0f;
	public static final int BASH_LAUNCH_SPIKE_T = 9;
	// G: Charged Shield Throw -- the normal shield throw, every enemy it hits chains lightning
	public static final int CHARGED_THROW_CD = 160;
	public static final int CHARGED_TICKS = 100;
	public static final float CHAIN_DAMAGE = 16.0f;
	public static final double CHAIN_RADIUS = 5.0;
	public static final int CHAIN_TARGETS = 2;
	// Shift+G: Twin Throw -- the shield and the weapon together
	public static final int TWIN_THROW_CD = 200;
	// Z: Lightning Shield Slam
	public static final int SHIELD_SLAM_CD = 220;
	public static final float SHIELD_SLAM_DAMAGE = 17.0f;
	// Shift+Z (ultimate): Thunder Clap ("Endgame" -- the weapon struck into the shield)
	public static final int CLAP_T = 12;
	public static final double CLAP_RANGE = 10.0;
	public static final double CLAP_MIN_DOT = 0.34;
	public static final float CLAP_DAMAGE = 40.0f;
	public static final double CLAP_KNOCKBACK = 2.0;
	public static final double CLAP_LIFT = 0.5;
	public static final int CLAP_BOLTS = 3;
	// V: Lightning Guard -- the shield raised and charged
	public static final int LIGHTNING_GUARD_CD = 200;
	public static final int LIGHTNING_GUARD_TICKS = 50;
	public static final float LIGHTNING_GUARD_SHOCK = 17.0f;

	// ================================================================== flight
	/** Blocks per second (Thor's Mjolnir flight is 18 cruise / 36 sprinting). */
	public static final double FLIGHT_CRUISE_BPS_MJOLNIR = 15.0;
	public static final double FLIGHT_SPRINT_BPS_MJOLNIR = 30.0;
	public static final double FLIGHT_CRUISE_BPS_STORMBREAKER = 13.0;
	public static final double FLIGHT_SPRINT_BPS_STORMBREAKER = 26.0;
	/**
	 * The take-off: a little lift and straight into Thor's flight pose. v0.17.7 (user: "remove the hammer spinning animation when
	 * flight starts"): the whirl spin-up (ANIM_TAKEOFF) is gone -- {@code WorthyFlight.start} sets no animation and clears a Launch's.
	 */
	public static final double TAKEOFF_LIFT = 0.55;
	/** A double-tap can't toggle again for this long. */
	public static final int FLIGHT_TOGGLE_CD = 10;
	/** Landing only counts after this long in the air. */
	public static final int FLIGHT_LIFTOFF_GRACE = 10;
}
