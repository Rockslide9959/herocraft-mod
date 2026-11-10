package com.projecthero.mod.undead;

/** v0.17.3: Undead's numbers in one place (ticks unless said otherwise). */
public final class UndeadConfig {
	private UndeadConfig() {
	}

	// ---------------------------------------------------------------- the body
	/** Part HP: head, body, right arm, left arm, right leg, left leg. */
	public static final float[] PART_HP = { 8f, 14f, 8f, 8f, 8f, 8f };
	/**
	 * v0.17.3 (user: regrowth free and fast like NBC Heroes' Regenerative Segmentation): a hurt part starts knitting
	 * {@link #KNIT_DELAY} after the last hit at {@link #KNIT_PER_SECOND} HP a second. Free: no hunger.
	 */
	public static final int KNIT_DELAY = 40;
	public static final float KNIT_PER_SECOND = 2.5f;
	/**
	 * v0.17.3 (user: "Players should regenerate 1 hp every 5 ticks with this ability"): {@link #HEAL_AMOUNT} health every
	 * {@link #HEAL_INTERVAL} ticks, always, while he has the power and is alive (on top of vanilla's food healing; hits don't pause it).
	 */
	public static final int HEAL_INTERVAL = 5;
	public static final float HEAL_AMOUNT = 1f;
	/** An explosion lands this share of its damage on every part. */
	public static final float BLAST_SHARE = 0.55f;

	// ---------------------------------------------------------------- regeneration (NBC Heroes' Regenerative Segmentation)
	/**
	 * v0.17.3 (user: "can we build our undead power the same way theyve built theirs? ... the regeneration to work the same ... at
	 * the same speed"): ten segments ({@code UndeadState}: head, body, both arms down to the wrist, both legs down to the ankle,
	 * both hands, both feet), each growing one FRAME a tick from 0 (gone) to its full count, starting the tick it may, free.
	 * Frames to whole, by segment (NBC's: head 17, body 20, arm 18, legs 20, hand 12, feet 11).
	 */
	public static final int[] FRAMES = { 17, 20, 18, 18, 20, 20, 12, 12, 11, 11 };
	/**
	 * The first frame of the flesh stage, by segment: below it the segment is bone, from it to {@link #FRAMES} flesh, at
	 * {@link #FRAMES} skin (NBC's stage boundaries: head 10, body 13, arm 11, legs 12, hand 7, feet 5).
	 */
	public static final int[] FLESH_FRAME = { 10, 13, 11, 11, 12, 12, 7, 7, 5, 5 };
	/**
	 * v0.17.3 (user: "make the delay before a lost body part starts to regenerate 1,5 seconds please"): a lost segment waits this
	 * long after it broke before its regrowth may start (times {@code UndeadRegenMods.delayMult}); its regrowth then takes
	 * {@link #FRAMES} ticks (times {@code UndeadRegenMods.durationMult}).
	 */
	public static final int REGROW_DELAY = 30;
	/**
	 * The order, checked every tick: a segment may start growing only once the one it grows from has this many frames (or is
	 * whole). v0.17.3 (user: "sometimes the hand will start regrowing at the same time as the arm so it looks like the hand is
	 * materializing midair"): a child never shows before its parent has grown out to where it attaches -- the parent's bone
	 * front has to reach the joint, which is the last bone frame ({@link #FLESH_FRAME} - 1): each hand waits for its arm to reach
	 * 10, each foot for its leg to reach 11, the legs for the body to reach 12 (the hips, its far end). The arms hang off the top
	 * of the body (rows 0-4 of 12), so they wait for the body to reach 7 (its bone front past the shoulder); the body hangs off
	 * the head's neck face (its first row), so it waits for the head to reach 4. The head grows from nothing.
	 */
	public static final int BODY_NEEDS_HEAD = 4;
	public static final int LIMB_NEEDS_BODY = 7;
	public static final int LEG_NEEDS_BODY = 12;
	public static final int HAND_NEEDS_ARM = 10;
	public static final int FOOT_NEEDS_LEG = 11;
	/**
	 * The three layers of the 3D look (harness-only now), as {start, length} shares of a segment's frames: the bone pushes out over
	 * the bone stage, the muscle builds round it and is in by the flesh stage, the skin closes over in the flesh stage.
	 */
	public static final float[] LAYER_BONE = { 0f, 0.45f };
	public static final float[] LAYER_MUSCLE = { 0.25f, 0.35f };
	public static final float[] LAYER_SKIN = { 0.6f, 0.4f };

	/** 0..1 through one layer at regrowth progress {@code p}. */
	public static float layer(float[] layer, float p) {
		return Math.max(0f, Math.min(1f, (p - layer[0]) / layer[1]));
	}

	public static float skinShare(float p) {
		return layer(LAYER_SKIN, p);
	}

	/**
	 * The bit of an arm the finger shots used up grows back like an arm segment (one frame a tick) over the share of its frames
	 * that is missing, at least {@link #CUT_MIN_SHARE}.
	 */
	public static final float CUT_MIN_SHARE = 0.35f;

	public static float cutRegrowTicks(float missing) {
		return FRAMES[com.projecthero.mod.undead.data.UndeadState.RIGHT_ARM] * Math.max(CUT_MIN_SHARE, Math.min(1f, missing));
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
	// v0.17.3 (tester: "have it be a spammy moveset with weak abilities (DPS)"): every hit a little lighter, the cooldowns shorter
	public static final float PARTS_BULLET_DAMAGE = 6f;
	public static final double FINGER_SPEED = 2.6;
	/** v0.17.3 (user: "remove the cooldown on parts bullet please"): none -- only the key (one shot a tap) and the arm it eats limit it. */
	public static final int PARTS_BULLET_COOLDOWN = 0;
	/** The finger shots and the thrown arm fly at what the crosshair is on, up to this far (beyond it: straight along the look). */
	public static final double AIM_RANGE = 64.0;
	public static final float CRIMSON_DAMAGE = 10f;
	public static final int CRIMSON_COOLDOWN = 15;
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
	/** Parts Bullet: Arm (tester: "Nerf arm throw, i think 20 is a bit much"). */
	public static final float PUNCH_DAMAGE = 14f;
	public static final int PUNCH_COOLDOWN = 15;
	public static final double PUNCH_SPEED = 2.2;

	// ---------------------------------------------------------------- G
	public static final float CRESCENT_DAMAGE = 11f;
	public static final double CRESCENT_REACH = 4.5;
	public static final int CRESCENT_COOLDOWN = 35;
	public static final float GREAT_CRESCENT_DAMAGE = 18f;
	public static final double GREAT_CRESCENT_RANGE = 18.0;
	public static final int GREAT_CRESCENT_COOLDOWN = 300;

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
	 * (from the bottom up) -- this many ticks each: v0.17.3 (user: "the right click boosted flight should last about 15 seconds before the player becomes just a head") 300 ticks (15 s) of boosting from a whole body down to the head. A stage
	 * whose parts are already gone is skipped.
	 */
	public static final int[] BURN_TICKS = { 105, 90, 105 };
	/** Shift + X held this long starts the flight (a shorter Shift tap does nothing). */
	public static final int FLIGHT_HOLD_TICKS = 4;

	// X tapped: Leg Launch; X held: Head Launch (UndeadLaunch)
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

	/** The arc is pushed for at most this long (it normally ends on landing first). */
	public static final int LEG_LAUNCH_MAX = 30;
	public static final int HEAD_LAUNCH_MAX = 60;
	/** v0.17.3: after a launch lets go in the air, how long at most he is safe from fall damage while momentum carries him (ends on landing). */
	public static final int LAUNCH_CARRY_SAFE = 200;
	/** Leg Launch: what the flying body does to whatever it passes through. (The Head Launch uses {@link #HEAD_BULLET_DAMAGE}.) */
	public static final float LEG_LAUNCH_DAMAGE = 6f;
	public static final int LEG_LAUNCH_COOLDOWN = 40;
	public static final int HEAD_LAUNCH_COOLDOWN = 160;

	// ---------------------------------------------------------------- Z
	public static final float VORTEX_DAMAGE = 13f;
	public static final double VORTEX_SPEED = 1.7;
	public static final int VORTEX_PIERCE = 3;
	public static final int VORTEX_COOLDOWN = 80;
	/** The head hits hardest of the thrown parts (tester: "the head throw makes sense for more damage"); the Head Launch too. */
	public static final float HEAD_BULLET_DAMAGE = 18f;
	public static final double HEAD_SPEED = 1.5;
	public static final int HEAD_BULLET_COOLDOWN = 160;

	// ---------------------------------------------------------------- Shift+V: the Bone Sword (UndeadBoneSword)
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
	/** A V released before this is a tap: Reform. */
	public static final int BONE_SWORD_TAP = 5;
	public static final int BONE_SWORD_REVEAL = 14;

	// ---------------------------------------------------------------- V: Reform
	/**
	 * v0.17.3 (user: ripping off your arm is useless, V should be another utility): Reform -- he forces his body back together at
	 * once. Every part that is missing or regrowing is whole on the spot, he heals {@link #REFORM_HEAL} and the fire and harmful
	 * effects on him go. Free (v0.17.3: regrowth is free, like NBC's); it works with auto-regrowth off too.
	 */
	public static final int REFORM_COOLDOWN = 400;
	public static final float REFORM_HEAL = 6f;
	/** How long the snap-together pose and flash last. */
	public static final int REFORM_ANIM = 8;

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
	/**
	 * v0.17.3 (tester: "Make deadline do damage less consistently"): each tick of contact rolls {@link #DEAD_LINE_MIN}..
	 * {@link #DEAD_LINE_MAX_ROLL} times this, and {@link #DEAD_LINE_MISS} of them miss outright: about 4 a hit on average, as before.
	 */
	public static final float DEAD_LINE_DAMAGE = 4.7f;
	public static final float DEAD_LINE_MIN = 0.5f, DEAD_LINE_MAX_ROLL = 1.5f, DEAD_LINE_MISS = 0.15f;
	public static final int DEAD_LINE_HIT_EVERY = 4;
	public static final int DEAD_LINE_MAX = 100;
	public static final int DEAD_LINE_COOLDOWN = 50;
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
