package com.projecthero.mod.andy;

/** v0.17.3: Andy's numbers in one place (ticks unless said otherwise). */
public final class AndyConfig {
	private AndyConfig() {
	}

	// ---------------------------------------------------------------- the body
	/** Part HP: head, body, right arm, left arm, right leg, left leg. */
	public static final float[] PART_HP = { 8f, 14f, 8f, 8f, 8f, 8f };
	/** A part that has not been hit for this long knits back at {@link #PART_KNIT} HP a second. */
	public static final int KNIT_DELAY = 100;
	public static final float PART_KNIT = 1.0f;
	/** An explosion lands this share of its damage on every part. */
	public static final float BLAST_SHARE = 0.55f;

	// ---------------------------------------------------------------- regeneration (Normal, Fast or Burst on V; half Normal while starving)
	// v0.17.3 (user: "the regen is slow but player can press V to speed it up at the cost of losing hunger a lot faster"):
	// V cycles Normal, Fast and Burst. SPEED_SLOW is never picked (an old save may still hold it); its numbers are the starved rate.
	public static final int SPEED_SLOW = 0;
	public static final int SPEED_NORMAL = 1;
	public static final int SPEED_FAST = 2;
	public static final int SPEED_BURST = 3;
	public static final int SPEEDS = 4;
	/** How long a broken part stays gone before it starts to regrow (starving: {@link #starvedDelay}). */
	public static final int[] REGROW_DELAY = { 100, 50, 14, 6 };
	/**
	 * How long the regrowth itself takes (v0.17.3, user: "regrowth should be slower"): at Normal 170 ticks = 8.5 s. Fast and
	 * Burst scale the whole thing, the gaps between the layers included. The Slow slot is the starved rate.
	 */
	public static final int[] REGROW_TICKS = { 340, 170, 68, 34 };
	/**
	 * v0.17.3 (user: "make it so that you need hunger/saturation in order to regrow otherwise you regenerate 50% slower than
	 * normal speed"): a part whose food share ({@link #partFood}) could not be paid in full when it started regrows at half the
	 * NORMAL speed, whatever the mode: a whole limb in this many ticks (the torso {@link #TORSO_FACTOR} times that); its wait
	 * before starting doubles from Normal's too ({@code Andy.payForPart}).
	 */
	public static final int STARVED_TICKS = 2 * 170;

	/** The wait before a regrowth starts while starving: twice Normal's. */
	public static int starvedDelay() {
		return 2 * REGROW_DELAY[SPEED_NORMAL];
	}
	/**
	 * The three layers of a regrowth, as {start, length} shares of {@link #REGROW_TICKS} (user: "the bone starts pushing out and
	 * then 2 seconds after the muscle starts rebuilding and then 2 seconds after that the skin starts building on"). At Normal
	 * (170 ticks): the bone pushes out over 0-3 s, the muscle builds over 2-6 s, the skin over 4-8.5 s -- the part is usable
	 * the moment the skin closes.
	 */
	public static final float[] LAYER_BONE = { 0f, 60f / 170f };
	public static final float[] LAYER_MUSCLE = { 40f / 170f, 80f / 170f };
	public static final float[] LAYER_SKIN = { 80f / 170f, 90f / 170f };
	/**
	 * v0.17.3 (user: "Slow mode: regenerating your entire body should drain an amount equal to half the hunger bar, Fast mode ...
	 * 75% of the hunger bar, Burst Mode ... the full hunger bar"): food points a WHOLE body (the body and all four limbs) costs to
	 * regrow, per speed {Slow (unused), Normal, Fast, Burst} (user: "lets go back to flat values"): each part pays a fifth of it
	 * up front, the moment it starts regrowing, out of the saturation first, then the hunger bar. The bar is 20 points.
	 */
	public static final float[] WHOLE_BODY_FOOD = { 10f, 10f, 15f, 20f };
	public static final int BODY_SHARES = 5;

	/** Food points one part costs to regrow at {@code speed}. */
	public static float partFood(int speed) {
		return WHOLE_BODY_FOOD[Math.floorMod(speed, SPEEDS)] / BODY_SHARES;
	}
	/** Crimson Bullet: the regeneration delay is multiplied by this for {@link #CRIMSON_SLOW_TICKS}. */
	public static final float CRIMSON_SLOW_FACTOR = 2.5f;
	public static final int CRIMSON_SLOW_TICKS = 300;

	/** 0..1 through one layer at regrowth progress {@code p}. */
	public static float layer(float[] layer, float p) {
		return Math.max(0f, Math.min(1f, (p - layer[0]) / layer[1]));
	}

	public static float skinShare(float p) {
		return layer(LAYER_SKIN, p);
	}

	/**
	 * A part that only lost its end (the hands and feet after a flight, the bit of an arm the finger shots used up) regrows
	 * the same three layers over this share of a whole part's time: the share of it that is missing, at least
	 * {@link #CUT_MIN_SHARE}.
	 */
	public static final float CUT_MIN_SHARE = 0.35f;

	public static float cutRegrowTicks(int speed, float missing) {
		return REGROW_TICKS[Math.floorMod(speed, SPEEDS)] * Math.max(CUT_MIN_SHARE, Math.min(1f, missing));
	}

	// ---------------------------------------------------------------- the order a broken body grows back in
	/**
	 * v0.17.3 (user: "torso until theres space for arms and then both arms at the same time and then once torso is done both
	 * legs at the same time ... like how it would work as if you actually had healing powers"): whenever the torso is gone it
	 * comes back first, from the neck down (spine and ribcage, then the muscle, then the skin; shoulders first, hips last), and
	 * the limbs grow out of it as there is something to grow from:
	 * <ol>
	 *   <li>once its regrowth passes {@link #SHOULDER_SHARE} (the bone is down past the shoulders, the first muscle is on the
	 *   upper chest) BOTH arms start together;</li>
	 *   <li>once it passes {@link #HIP_SHARE} (1: the torso is whole, hips and all) BOTH legs start together.</li>
	 * </ol>
	 * A limb lost while the torso is whole regrows at once, in parallel with anything else. The torso, the biggest part, takes
	 * {@link #TORSO_FACTOR} times a limb's time. From only a head at Normal: a 1 s wait, the torso 1-12.9 s, both arms
	 * 4.8-13.3 s, both legs 12.9-21.4 s: walking again after about 21 s (Fast 8.6 s, Burst 4.3 s).
	 */
	public static final float TORSO_FACTOR = 1.4f;
	public static final float SHOULDER_SHARE = 0.32f;
	public static final float HIP_SHARE = 1.0f;
	/** With only the head left, the torso starts after this (by speed; starving: twice Normal's). */
	public static final int[] STAGE_DELAY = { 40, 20, 8, 4 };

	/** How long {@code part}'s regrowth takes at {@code speed} (the torso is bigger than a limb). */
	public static int regrowTicks(int part, int speed) {
		int t = REGROW_TICKS[Math.floorMod(speed, SPEEDS)];
		return part == com.projecthero.mod.andy.data.AndyState.BODY ? Math.round(t * TORSO_FACTOR) : t;
	}

	/** The share of the torso's regrowth a part waits for while the torso is gone: arms the shoulders, legs the hips, the head all. */
	public static float torsoShareFor(int part) {
		return switch (part) {
			case com.projecthero.mod.andy.data.AndyState.RIGHT_ARM, com.projecthero.mod.andy.data.AndyState.LEFT_ARM -> SHOULDER_SHARE;
			case com.projecthero.mod.andy.data.AndyState.RIGHT_LEG, com.projecthero.mod.andy.data.AndyState.LEFT_LEG -> HIP_SHARE;
			default -> 1f;
		};
	}

	// ---------------------------------------------------------------- Undead
	/** Just shattered: untouchable and no moves for this long (the body then grows back by the staged regrowth). */
	public static final int REFORM_GRACE = 30;
	/** A launched limb or head lasts at most this long. */
	public static final int SEVERED_LIFE = 600;

	// ---------------------------------------------------------------- passives
	/** Bloody Arts: + melee (Victor: {@link #VICTOR_MELEE}). */
	public static final double BLOODY_ARTS_MELEE = 3.0;
	public static final double VICTOR_MELEE = 7.0;
	/** Victor hits harder with every move. */
	public static final float VICTOR_DAMAGE = 1.35f;
	/** Repair Headbutt: a head hit this hard (or the head breaking) snaps back at the nearest enemy within the reach. */
	public static final float HEADBUTT_TRIGGER = 4f;
	public static final double HEADBUTT_REACH = 4.0;
	public static final float HEADBUTT_DAMAGE = 8f;
	public static final int HEADBUTT_COOLDOWN = 40;

	// ---------------------------------------------------------------- R: fingers and the stretch punch
	public static final float PARTS_BULLET_DAMAGE = 7f;
	public static final double FINGER_SPEED = 2.6;
	/** v0.17.3 (user: "remove the cooldown on parts bullet please"): none -- only the key (one shot a tap) and the arm it eats limit it. */
	public static final int PARTS_BULLET_COOLDOWN = 0;
	/** The finger shots and the thrown arm fly at what the crosshair is on, up to this far (beyond it: straight along the look). */
	public static final double AIM_RANGE = 64.0;
	public static final float CRIMSON_DAMAGE = 12f;
	public static final int CRIMSON_COOLDOWN = 20;
	/**
	 * Each finger shot takes a bite out of the shooting arm, from the fingertips up: a Parts Bullet 1/12 (about twelve shots
	 * use up a whole arm), a Crimson Bullet four times that. A used-up arm is gone (the other arm shoots); what is gone starts
	 * to grow back after {@link #EATEN_REGROW_WAIT} without a shot.
	 */
	public static final float PARTS_EAT = 1f / 12f;
	public static final float CRIMSON_EAT = 4f / 12f;
	public static final int EATEN_REGROW_WAIT = 40;
	/** Hold R: past {@link #PUNCH_HOLD_TICKS} the arm draws back (a shorter tap is a Parts Bullet, fired on release). */
	public static final int PUNCH_HOLD_TICKS = 7;
	/** Let go at least this long after pressing to throw the punch (earlier: the wind-up is dropped, nothing fires). */
	public static final int PUNCH_MIN_HOLD = 14;
	public static final float PUNCH_DAMAGE = 20f;
	public static final int PUNCH_COOLDOWN = 20;
	public static final double PUNCH_SPEED = 2.2;

	// ---------------------------------------------------------------- G
	public static final float CRESCENT_DAMAGE = 13f;
	public static final double CRESCENT_REACH = 4.5;
	public static final int CRESCENT_COOLDOWN = 50;
	public static final float GREAT_CRESCENT_DAMAGE = 18f;
	public static final double GREAT_CRESCENT_RANGE = 18.0;
	public static final int GREAT_CRESCENT_COOLDOWN = 400;

	// ---------------------------------------------------------------- flight (Blood Propulsion)
	/** Blocks a tick: 25 b/s; holding right click 40 b/s (and the body burns away); just a head 32 b/s. */
	public static final double PROPULSION_SPEED = 1.25;
	public static final double BOOST_SPEED = 2.0;
	public static final double HEAD_FLIGHT_SPEED = 1.6;
	/** v0.17.3 (user: "remove the time limit on the flight, player should be able to fly infinitely"): no limit. */
	public static final int PROPULSION_MAX = Integer.MAX_VALUE;
	public static final int PROPULSION_COOLDOWN = 30;
	/** Rows (model px, of 12) of each arm and leg that burst off at take-off: the hands and feet. */
	public static final int HAND_ROWS = 4;
	/**
	 * The boost burns the body away in three stages -- both legs (from the feet up), both arms (from the hands up), the body
	 * (from the bottom up) -- this many ticks each: 80 ticks (4 s) of boosting from a whole body down to the head. A stage
	 * whose parts are already gone is skipped.
	 */
	public static final int[] BURN_TICKS = { 28, 24, 28 };
	/** Shift + X held this long starts the flight (a shorter Shift tap does nothing). */
	public static final int FLIGHT_HOLD_TICKS = 4;

	// X tapped: Leg Launch; X held: Head Launch (AndyLaunch)
	/** A plain X held this long fires the Head Launch on the spot; released sooner it is a Leg Launch. */
	public static final int HEAD_LAUNCH_HOLD = 20;
	/** The Head Launch wind-up shows from this long into the hold (so a quick tap never flashes it). */
	public static final int LAUNCH_CHARGE_SHOW = 5;
	/** A launch flies a scripted arc: this many blocks a tick forward, this much up at the start, minus the gravity every tick. */
	public static final double LEG_LAUNCH_SPEED = 1.55;
	public static final double LEG_LAUNCH_LIFT = 0.45;
	public static final double HEAD_LAUNCH_SPEED = 1.6;
	public static final double HEAD_LAUNCH_LIFT = 1.1;
	public static final double LAUNCH_GRAVITY = 0.08;
	/**
	 * v0.17.3 (user: "make leg launch and the head launch dash the player where the player aims ... directly where the player is
	 * aiming like with the other dash abilities"): both fly in a straight line along the look, no arc, this far (blocks).
	 */
	public static final double LEG_LAUNCH_RANGE = 20.0;
	public static final double HEAD_LAUNCH_RANGE = 45.0;
	public static final double LEG_LAUNCH_DASH_SPEED = 2.0;
	public static final double HEAD_LAUNCH_DASH_SPEED = 2.5;

	/** v0.17.3 (user: passive auto-eat): he eats from his inventory once his food falls below this (food + saturation, points). */
	public static final int AUTO_EAT_BELOW = 14;
	public static final int AUTO_EAT_EVERY = 30;
	/** The arc is pushed for at most this long (it normally ends on landing first). */
	public static final int LEG_LAUNCH_MAX = 30;
	public static final int HEAD_LAUNCH_MAX = 60;
	/** Leg Launch: what the flying body does to whatever it passes through. (The Head Launch uses {@link #HEAD_BULLET_DAMAGE}.) */
	public static final float LEG_LAUNCH_DAMAGE = 6f;
	public static final int LEG_LAUNCH_COOLDOWN = 60;
	public static final int HEAD_LAUNCH_COOLDOWN = 200;

	// ---------------------------------------------------------------- Z
	public static final float VORTEX_DAMAGE = 16f;
	public static final double VORTEX_SPEED = 1.7;
	public static final int VORTEX_PIERCE = 3;
	public static final int VORTEX_COOLDOWN = 120;
	public static final float HEAD_BULLET_DAMAGE = 12f;
	public static final double HEAD_SPEED = 1.5;
	public static final int HEAD_BULLET_COOLDOWN = 200;

	// ---------------------------------------------------------------- Shift+V: the Bone Sword (AndyBoneSword)
	/**
	 * v0.17.3 (user: "add an ability for shift+V where the player rips an arm off and uses it as a bone sword"): he tears off his
	 * off arm and holds it in his main hand as a sword. Melee with it hits for {@link #BONE_SWORD_DAMAGE} (an empty hand's
	 * total, Bloody Arts included), sweeps like a sword and reaches a little further; the torn-off arm only grows back once it
	 * is put away (Shift+V again, after {@link #BONE_SWORD_MAX}, or on losing the hand).
	 */
	public static final float BONE_SWORD_DAMAGE = 9f;
	public static final float BONE_SWORD_SWEEP_DAMAGE = 4f;
	public static final double BONE_SWORD_REACH_BONUS = 1.0;
	/** Attack speed while held: a sword's 1.6 a second (the player's base is 4). */
	public static final double BONE_SWORD_ATTACK_SPEED = 1.6;
	public static final int BONE_SWORD_MAX = 1200;
	public static final int BONE_SWORD_COOLDOWN = 60;
	/**
	 * v0.17.3 (user: "make the bone sword hold V for 2 seconds"): V held this long draws it. From {@link #BONE_SWORD_TAP} on he
	 * reaches over and struggles with the arm (the hold is the struggle); at the end it tears off, he grips it and after
	 * {@link #BONE_SWORD_REVEAL} more it bursts, leaving the sword in his hand.
	 */
	public static final int BONE_SWORD_HOLD = 40;
	/** A V released before this is a tap: Tear Off. */
	public static final int BONE_SWORD_TAP = 5;
	public static final int BONE_SWORD_REVEAL = 14;

	// ---------------------------------------------------------------- V: Tear Off
	/**
	 * v0.17.3 (user: "put another move on regular V"): he rips off his most damaged limb (the off arm when none is hurt) and with
	 * it whatever is on him: fire, poison, wither, slowness and every other harmful effect. The limb regrows as ever.
	 */
	public static final int TEAR_OFF_COOLDOWN = 160;

	// ---------------------------------------------------------------- Victor (disabled)
	/**
	 * v0.17.3 (user: "remove the Victor ability for now"): off. No key pulls the card (Shift+V is the Bone Sword), the kit never
	 * swaps to Dead Vulcan / Division Bullet / Dead Blade / Dead Road, and the HUD, haze and guide pages are hidden. The code
	 * stays: set this back to true to bring him back.
	 */
	public static final boolean VICTOR_ENABLED = false;
	public static final int VICTOR_TICKS = 600;
	public static final int VICTOR_COOLDOWN = 1800;

	public static final double DEAD_LINE_RANGE = 24.0;
	public static final float DEAD_LINE_DAMAGE = 4f;
	public static final int DEAD_LINE_HIT_EVERY = 4;
	public static final int DEAD_LINE_MAX = 100;
	public static final int DEAD_LINE_COOLDOWN = 60;
	public static final int QUARDEAD_TICKS = 300;
	public static final float CLONE_DAMAGE = 5f;
	public static final int QUARDEAD_COOLDOWN = 800;

	// ---------------------------------------------------------------- Victor's kit
	public static final int VULCAN_SHOTS = 10;
	public static final float VULCAN_DAMAGE = 4f;
	public static final int VULCAN_COOLDOWN = 60;
	public static final float DEAD_BLADE_DAMAGE = 22f;
	public static final double DEAD_BLADE_REACH = 5.5;
	public static final int DEAD_BLADE_COOLDOWN = 80;
	public static final double DEAD_ROAD_RANGE = 32.0;
	public static final float DEAD_ROAD_DAMAGE = 7f;
	public static final int DEAD_ROAD_TICKS = 50;
	public static final int DEAD_ROAD_COOLDOWN = 240;
	public static final int DIVISION_CLONE_TICKS = 160;
	public static final int DIVISION_COOLDOWN = 400;

	/** A boss takes at most this share of its max health from one hit. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
}
