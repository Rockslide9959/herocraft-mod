package com.projecthero.mod.greenlantern.mech;

/**
 * v0.17.1: balance numbers for the Hard-Light Mech (Green Lantern's ultimate construct). Every damage number goes through
 * {@code GreenLanternRanks.power} (Oath + Lantern Rank + Willpower Surge) and every Ring Charge cost through
 * {@code GreenLanternEnergy.spend} (the Oath doubles it, the rank cuts it).
 */
public final class GreenLanternMechConfig {
	private GreenLanternMechConfig() {
	}

	// ---- forming / keeping it up
	/** Ring Charge to form the mech (of 10,000). */
	public static final float FORM_COST = 1500f;
	/** Ring Charge per second while it stands; when the ring can't pay it, the mech dissolves. */
	public static final float UPKEEP_PER_SEC = 15f;
	/** The mech assembling round the pilot (no moves, no walking). */
	public static final int ASSEMBLE_TICKS = 40;
	/**
	 * v0.17.1: the first part of the assembly in which it builds itself piece by piece, pixel by pixel (feet to head, the
	 * renderer's texel build); the rest is it straightening up. It can be piloted only once the assembly is over.
	 */
	public static final int BUILD_TICKS = 34;
	/** N: it dissolves over this long, then sets the pilot down. */
	public static final int DISSOLVE_TICKS = 20;
	/** Cooldown after leaving it (N, or the ring running dry) before it can be formed again. */
	public static final int REFORM_COOLDOWN_TICKS = 200;
	/** Cooldown after it is SHATTERED (its integrity hit 0). */
	public static final int SHATTER_COOLDOWN_TICKS = 1200;
	public static final String COOLDOWN_ID = "hard_light_mech";

	// ---- body
	public static final float WIDTH = 3.0f; // v0.17.1 Gurren rework: a slimmer, taller mech (was 3.2 x 6.5)
	public static final float HEIGHT = 6.95f; // to the crown of the small head (the drill horn and the spike tips stand above it)
	/** Integrity at Lantern Rank 1; +10% per rank above it. */
	public static final float BASE_INTEGRITY = 300f;
	public static final float INTEGRITY_PER_RANK = 0.10f;
	public static final double ARMOR = 8.0;
	/** Movement speed attribute: about 10 blocks a second on flat ground. */
	public static final double WALK_SPEED = 0.23;
	public static final double STEP_HEIGHT = 2.0;
	/** Space: the thrusters kick it up about 3.5 blocks. */
	public static final double JUMP_VELOCITY = 0.72;
	/** Where the pilot sits: 4.22 blocks up (feet of a sitting player 0.6 below this; v0.17.1 Gurren rework: was 3.66, the cockpit rose with the longer legs). */
	public static final double SEAT_Y = 4.22;
	public static final double SEAT_Z = 0.0;
	/** Speed while the shield is up / the beam is firing. */
	public static final float SHIELD_SPEED = 0.5f;
	public static final float BEAM_SPEED = 0.35f;

	// ---- R: punch combo (alternating fists; hold R to keep swinging)
	public static final float PUNCH_DAMAGE = 20f;
	public static final float PUNCH_COST = 8f;
	public static final int PUNCH_COOLDOWN_TICKS = 10;
	/** The fist lands this many ticks after the swing starts (the clip's impact frame). */
	public static final int PUNCH_IMPACT_TICKS = 4;
	public static final double PUNCH_REACH = 5.5;
	public static final double PUNCH_KNOCKBACK = 1.8;

	// ---- Shift+R: Rocket Fist
	public static final float ROCKET_DAMAGE = 30f;
	public static final float ROCKET_COST = 60f;
	public static final int ROCKET_COOLDOWN_TICKS = 120;
	public static final double ROCKET_RANGE = 24.0;
	public static final double ROCKET_SPEED = 2.6;
	public static final int ROCKET_LAUNCH_TICKS = 5;

	// ---- G: Ground Slam
	public static final float SLAM_DAMAGE = 28f;
	public static final float SLAM_COST = 80f;
	public static final int SLAM_COOLDOWN_TICKS = 160;
	public static final int SLAM_IMPACT_TICKS = 11;
	public static final double SLAM_RADIUS = 9.0;
	public static final int SLAM_WAVE_TICKS = 10;

	// ---- X: Thruster Dash
	public static final float DASH_DAMAGE = 14f;
	public static final float DASH_COST = 30f;
	public static final int DASH_COOLDOWN_TICKS = 50;
	public static final double DASH_SPEED = 2.4;
	public static final int DASH_HIT_TICKS = 10;

	// ---- Z (hold): Chest Lantern Beam
	public static final float BEAM_DAMAGE = 8f;
	public static final int BEAM_INTERVAL_TICKS = 5;
	public static final float BEAM_DRAIN_PER_SEC = 50f;
	public static final double BEAM_RANGE = 48.0;

	// ---- V (hold): Hard-Light Shield wall
	public static final float SHIELD_COST = 40f;
	public static final float SHIELD_DRAIN_PER_SEC = 20f;
	/** Hits from the front arc while the shield is up do only this fraction to the mech. */
	public static final float SHIELD_FRONT_FACTOR = 0.2f;

	// ---- C: Missile Pods
	public static final float MISSILE_DAMAGE = 9f;
	public static final float MISSILE_COST = 120f;
	public static final int MISSILE_COOLDOWN_TICKS = 200;
	public static final int MISSILE_COUNT = 12;
	public static final double MISSILE_TARGET_RANGE = 36.0;

	// ================================================================ v0.17.1 mech construct combos
	// Shift + the key a construct is bound to (outside the mech) -- see MechComboKeys for the rule.

	/** Willpower XP for a mech combo that connects (the GL construct combos give {@code COMBO_XP_BONUS} = 25). */
	public static final long COMBO_XP = 25L;
	public static final long GIGA_BREAK_XP = 60L;

	// ---- Giga Drill (Mining Drill combo): hold to spin up, let go to charge; a full spin ends in GIGA DRILL BREAK
	public static final float DRILL_START_COST = 120f;
	public static final float DRILL_SPIN_DRAIN_PER_SEC = 40f;
	/** Ticks of holding to a full spin (GIGA DRILL BREAK on release). */
	public static final int DRILL_SPIN_TICKS = 40;
	/** Let go sooner than this and the drill just winds down (no charge, a short cooldown). */
	public static final int DRILL_MIN_SPIN_TICKS = 6;
	/** Held this long, it lets go by itself. */
	public static final int DRILL_MAX_HOLD_TICKS = 140;
	/** Walking speed while it spins up. */
	public static final float DRILL_SPIN_SPEED = 0.35f;
	/** How far the charge goes: from {@code MIN} (barely spun) to {@code MAX} (full spin), in blocks. */
	public static final double DRILL_RANGE_MIN = 10.0;
	public static final double DRILL_RANGE_MAX = 26.0;
	/** Blocks a tick (about 23 a second). */
	public static final double DRILL_SPEED = 1.15;
	/** The bore runs at most this far ahead of the mech (the pilot's client moves it; this keeps them together). */
	public static final double DRILL_LEAD = 5.0;
	/** The tunnel is cut this far ahead of the drill's progress, so the mech never hits rock that is still there. */
	public static final double DRILL_BORE_AHEAD = 4.5;
	/** The round tunnel's radius round the mech's middle (it clears the 3.2 x 6.5 body). */
	public static final double DRILL_BORE_RADIUS = 3.7;
	/** The bore's axis, above the mech's feet. */
	public static final double DRILL_AXIS_Y = 3.25;
	/** Anything harder than this stops the drill (obsidian and up); bedrock, barriers and the like always do. */
	public static final float DRILL_MAX_HARDNESS = 25f;
	/** The share of bored blocks that drop as items (TNT-like; the rest is ground to light). */
	public static final float DRILL_DROP_CHANCE = 0.15f;
	/** A charge never runs longer than this. */
	public static final int DRILL_MAX_CHARGE_TICKS = 50;
	/** Enemies inside the drill take a hit this often, up to {@link #DRILL_MAX_HITS} each. */
	public static final int DRILL_HIT_INTERVAL = 3;
	public static final int DRILL_MAX_HITS = 8;
	public static final float DRILL_HIT_DAMAGE = 11f;
	/** How far from the charge's line an enemy is caught. */
	public static final double DRILL_HIT_RADIUS = 3.4;
	public static final int DRILL_COOLDOWN_TICKS = 300;
	/** A fizzled spin (let go too soon): this cooldown instead. */
	public static final int DRILL_FIZZLE_COOLDOWN_TICKS = 40;
	/** The drill shrinks back into the arm over this long. */
	public static final int DRILL_RECOVER_TICKS = 20;
	// GIGA DRILL BREAK (the full-spin finisher)
	public static final float GIGA_BREAK_DAMAGE = 70f;
	public static final double GIGA_BREAK_REACH = 9.0;
	public static final double GIGA_BREAK_RADIUS = 5.0;
	public static final double GIGA_BREAK_BORE_RADIUS = 4.6;

	// ---- Hard-Light Katana (Energy Blade combo): toggle to draw / sheathe; changes R, Shift+R and G while drawn
	public static final float KATANA_DRAW_COST = 60f;
	/** Ring Charge a second while it is drawn (on top of the mech's own upkeep). */
	public static final float KATANA_UPKEEP_PER_SEC = 6f;
	public static final int KATANA_DRAW_TICKS = 12;
	public static final int KATANA_SHEATH_TICKS = 10;
	public static final int KATANA_TOGGLE_COOLDOWN_TICKS = 10;
	/** R: the three-cut chain (diagonal down, rising reverse, wide horizontal finisher). */
	public static final float[] SLASH_DAMAGE = { 24f, 24f, 38f };
	public static final float[] SLASH_COST = { 10f, 10f, 16f };
	public static final int[] SLASH_COOLDOWN_TICKS = { 9, 9, 16 };
	/** The edge lands this many ticks into each cut's clip. */
	public static final int[] SLASH_IMPACT_TICKS = { 4, 4, 6 };
	/** Half the arc each cut covers, degrees either side of straight ahead. */
	public static final float[] SLASH_ARC_DEG = { 70f, 70f, 100f };
	public static final double SLASH_REACH = 7.2;
	/** No R for this long and the chain starts again from the first cut. */
	public static final int SLASH_CHAIN_RESET_TICKS = 24;
	/** A cut that connects freezes the swing this long (hit-stop). */
	public static final int HIT_STOP_TICKS = 3;
	/** Shift+R: the iaido draw-cut -- a crescent of light flies forward, through enemies; it cuts grass and leaves, not terrain. */
	public static final float IAIDO_DAMAGE = 34f;
	public static final float IAIDO_COST = 90f;
	public static final int IAIDO_COOLDOWN_TICKS = 100;
	public static final int IAIDO_LAUNCH_TICKS = 6;
	public static final double IAIDO_SPEED = 1.8;
	public static final double IAIDO_RANGE = 32.0;
	/** The crescent's half-width (and how far round its line it catches enemies). */
	public static final double IAIDO_HALF_WIDTH = 3.6;
	public static final double IAIDO_HIT_RADIUS = 3.0;
	/** G: the spinning sweep round the mech. */
	public static final float SPIN_DAMAGE = 30f;
	public static final float SPIN_COST = 70f;
	public static final int SPIN_COOLDOWN_TICKS = 120;
	public static final int SPIN_IMPACT_TICKS = 7;
	public static final double SPIN_RADIUS = 7.5;

	// ---- v0.17.1 round 2 (user: "Give the mech other mech construct combinations too, at least 3 more"): one-shot moves

	/** Walking pace while a combo move plays (x), and while the Wrecking Whirl spins. */
	public static final float MOVE_SPEED = 0.25f;
	public static final float WHIRL_SPEED = 0.55f;

	/**
	 * Emerald Mace -> METEOR MACE: a spiked mace of light forms in the right fist, is raised overhead in both hands and
	 * brought down in front: a crater-flash shockwave, a heavy hit and everything launched up and back.
	 */
	public static final int MACE_TICKS = 30;
	/** The tick the head lands (matches the {@code mace_smash} clip, 0.72 s, plus the clip's lag on the client). */
	public static final int MACE_IMPACT_TICKS = 15;
	public static final float MACE_COST = 140f;
	public static final float MACE_DAMAGE = 48f;
	public static final double MACE_RADIUS = 6.0;
	/** How far ahead of the mech the head lands. */
	public static final double MACE_REACH = 5.2;
	public static final int MACE_COOLDOWN_TICKS = 220;

	/**
	 * Wrecking Ball -> WRECKING WHIRL: a giant spiked ball of light on a chain, swung round the mech twice -- every few
	 * ticks it hits everything in the ring and hurls it outward.
	 */
	public static final int WHIRL_TICKS = 40;
	/** The ball swings out at this tick and hits every {@link #WHIRL_HIT_EVERY} ticks until {@link #WHIRL_END_TICKS}. */
	public static final int WHIRL_START_TICKS = 8;
	public static final int WHIRL_END_TICKS = 34;
	public static final int WHIRL_HIT_EVERY = 4;
	public static final float WHIRL_COST = 150f;
	public static final float WHIRL_DAMAGE = 14f;
	public static final double WHIRL_RADIUS = 8.5;
	public static final int WHIRL_COOLDOWN_TICKS = 240;

	/**
	 * Emerald Cannon -> LANTERN CANNON: the left forearm becomes a huge hard-light cannon, charges for about a second (the
	 * aim follows the camera) and fires one massive shell along the aim -- a blast where it lands. Never breaks blocks.
	 */
	public static final int CANNON_TICKS = 32;
	public static final int CANNON_FIRE_TICKS = 18;
	public static final float CANNON_COST = 160f;
	public static final float CANNON_DAMAGE = 60f;
	public static final double CANNON_RANGE = 64.0;
	public static final double CANNON_BLAST_RADIUS = 4.5;
	public static final int CANNON_COOLDOWN_TICKS = 260;
	/** Ticks the shell's tracer and blast are drawn for. */
	public static final int CANNON_SHOT_TICKS = 12;

	/** From the Construct Menu: the Giga Drill spins up to full before it lets go by itself (a GIGA DRILL BREAK). */
	public static final int MENU_DRILL_SPIN_TICKS = DRILL_SPIN_TICKS + 2;
}
