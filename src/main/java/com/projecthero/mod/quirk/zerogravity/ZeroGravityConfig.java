package com.projecthero.mod.quirk.zerogravity;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.7: every Zero Gravity number (ticks unless noted). Ability ids are {@code quirk_zero_gravity_<move>}.
 *
 * <p>v0.16.7 revamp ({@code docs/QUIRK_REVAMP_V0167.md}): the kit is built round TOUCH TO FLOAT / RELEASE TO DROP. The Nausea
 * numbers live in a file, {@code config/projecthero_quirk_zero_gravity.json} ({@link #nausea()}), migrated by the
 * {@link VersionedConfig}; everything else is a constant here.
 */
public final class ZeroGravityConfig {
	private ZeroGravityConfig() {
	}

	private static final String P = "quirk_zero_gravity_";

	// ---------------------------------------------------------------- ability ids (X = movement, V = utility, the rest attacks)
	// v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.7): the keys were re-laid out; the ids are kept (saves, cooldowns).
	//   R  Comet Toss        Shift+R  Debris Barrage
	//   G  Gunhead Martial   Shift+G  Zero Gravity Slam
	//   X  Zero-G Leap       Shift+X  Weightless Push    hold X  Float Self
	//   Z  Zero Gravity Field Shift+Z Debris Shield
	//   C  Meteor Shower     Shift+C  Gravity Bomb       hold C  Full Release (Plus Ultra)
	//   V  Touch             Shift+V  Release            hold V  Catch, Shift+hold V  Settle
	/** C: Meteor Shower (C again: rain). */
	public static final String SHOWER = P + "meteor_shower";
	/** Shift+Z: Debris Shield. */
	public static final String SHIELD = P + "debris_shield";
	/** R: Comet Toss. */
	public static final String COMET = P + "comet_toss";
	/** Shift+G: Zero Gravity Slam. */
	public static final String SLAM = P + "zero_gravity_slam";
	/** hold X: Float Self (as long as X is held) -- the movement key. */
	public static final String SELF = P + "zero_g_self";
	/** X: Zero-G Leap. */
	public static final String LEAP = P + "float_leap";
	/** G: Gunhead Martial Arts. */
	public static final String GUNHEAD = P + "gunhead_martial_arts";
	/** Z: Zero Gravity Field. */
	public static final String FIELD = P + "zero_gravity_field";
	/** V: Zero Gravity Touch -- the utility key. */
	public static final String TOUCH = P + "float_touch";
	/** Shift+V: Release. */
	public static final String RELEASE = P + "release";
	/** hold V: Catch (as long as V is held). */
	public static final String CATCH = P + "catch";
	/** Shift + hold V: Settle (a safe release of everything, then she vents Nausea; works while sick). */
	public static final String SETTLE = P + "settle";
	/** v0.17.1 Shift+R: Debris Barrage. */
	public static final String BARRAGE = P + "debris_barrage";
	/** v0.17.1 Shift+X: Weightless Push. */
	public static final String PUSH = P + "weightless_push";
	/** v0.17.1 Shift+C: Gravity Bomb (Shift+C again: drop it). */
	public static final String BOMB = P + "gravity_bomb";

	// ---------------------------------------------------------------- the file: Nausea
	public static final VersionedConfig<NauseaFile> SPEC = VersionedConfig.builder(NauseaFile.class, "projecthero_quirk_zero_gravity.json", NauseaFile::new)
			.balance("nausea")
			.reset(1, "v0.16.7 Quirk revamp -- Nausea from floated weight, Settle venting", "nausea")
			.build();

	private static NauseaFile file = new NauseaFile();

	public static final class NauseaFile {
		public Integer configVersion;
		public Nausea nausea = new Nausea();
	}

	/** Nausea, per second unless the name says ticks. */
	public static final class Nausea {
		/** Per unit of floated weight (a zombie weighs about 1, a block 0.4, an item 0.15, herself {@link #selfWeight}). */
		public float perWeightPerSecond = 0.5f;
		/** The longer things float without a break, the faster it climbs: up to this many times faster, after {@link #timeBonusTicks}. */
		public float timeBonusMax = 1.5f;
		public int timeBonusTicks = 600;
		/** Lost a second while nothing floats. */
		public float decayPerSecond = 8f;
		/** A one-off sting every time she floats something. */
		public float perFloat = 3f;
		/** Her own body counts as this much weight while it floats (Float Self, the leap). */
		public float selfWeight = 1.5f;
		/** At 100: everything drops, she is sick (vanilla Nausea) and has no moves (but Settle) for this long. */
		public int sickTicks = 100;
		/** Settle: lost a second while she settles on the ground. */
		public float settlePerSecond = 14f;
	}

	public static Nausea nausea() {
		return file.nausea;
	}

	/** Reads (migrating) the file; called from {@code ZeroGravityMod.initialize}. */
	public static void load() {
		file = SPEC.load();
	}

	/** The warning shows once she is this sick. */
	public static final float NAUSEA_WARN = 75f;

	/** Weight of one floated creature: bounding box volume x this, clamped. */
	public static final double WEIGHT_PER_VOLUME = 1.5;
	public static final double WEIGHT_MIN = 0.5;
	public static final double WEIGHT_MAX = 6.0;
	public static final double WEIGHT_RUBBLE = 0.4;
	public static final double WEIGHT_ITEM = 0.15;

	// ---------------------------------------------------------------- floating
	/** A touched creature rises this far, then hangs there drifting. */
	public static final double FLOAT_RISE = 1.6;
	public static final double FLOAT_LIFT_SPEED = 0.22;
	/** A floater further than this from her drops (and anything in another world). */
	public static final double FLOAT_LEASH = 64.0;
	/** Most creatures she can float at once by touch... */
	public static final int TOUCH_MAX = 5;
	/** Most blocks she can hold up by touch at once... */
	public static final int TOUCH_BLOCKS_MAX = 6;
	/** ...x this in the Queasy band and past (v0.17.1: +25% float capacity, rounded). */
	public static final float QUEASY_CAPACITY = 1.25f;
	/** v0.17.1 passive "Light on her feet": no fall damage while Nausea is below this (the Queasy band). */
	public static final float LIGHT_FEET_BELOW = 50f;
	/** v0.17.1 passive "Float pickup": dropped items this close drift to her. */
	public static final double PICKUP_RADIUS = 6.0;
	public static final double PICKUP_PULL = 0.07;
	/** v0.17.1 survival "Herding": a floated animal follows her this long, this far behind, then is set down softly. */
	public static final int HERD_TICKS = 400;
	public static final double HERD_DISTANCE = 2.6;
	/** v0.17.1: everything floated turns slowly (degrees a tick). */
	public static final float FLOAT_SPIN_DEGREES = 1.6f;
	/** v0.17.1: a key held this long is its hold move (X: Float Self, V: Catch, Shift+V: Settle); shorter is the tap. */
	public static final int HOLD_TICKS = 5;
	/** A touched squadmate lands softly on Release (Slow Falling). */
	public static final int ALLY_SLOW_FALL_TICKS = 80;

	// ---------------------------------------------------------------- V: Touch / Shift+V: Release
	public static final double TOUCH_REACH = 4.0;
	public static final double TOUCH_ASSIST_DEGREES = 18.0;
	public static final float TOUCH_DAMAGE = 2f;
	public static final int TOUCH_COOLDOWN = 10;
	/** A touched block hangs this far above where it was. */
	public static final double BLOCK_RISE = 1.6;
	/** The hardest block she can lift (obsidian is 50). */
	public static final float BLOCK_MAX_HARDNESS = 5.0f;
	/** A lifted block's hole is refilled by itself after this long at the latest (it is refilled when the block lands). */
	public static final int BLOCK_SAFETY_TICKS = 1200;
	public static final int RELEASE_COOLDOWN = 10;
	/** A dropped block hits for this (and a little less round it). */
	public static final float BLOCK_FALL_DAMAGE = 5f;
	public static final double BLOCK_SPLASH = 1.3;
	/** v0.17.1 survival "Crane": Release sets a touched block down where she points, this far at most (placed, never broken). */
	public static final double CRANE_RANGE = 24.0;
	public static final double CRANE_SPEED = 0.6;
	public static final int CRANE_MAX_TICKS = 100;

	// ---------------------------------------------------------------- R: Meteor Shower / Shift+R: Debris Shield
	/** Rubble lifted from the loose ground within this radius. */
	public static final int RUBBLE_RADIUS = 6;
	public static final int SHOWER_PIECES = 12;
	/** The cloud hangs this high over her, drifting toward where she aims (up to this far). */
	public static final double CLOUD_HEIGHT = 9.0;
	public static final double CLOUD_AIM_RANGE = 24.0;
	public static final double CLOUD_RADIUS = 3.0;
	/** The cloud rains by itself after this long. */
	public static final int CLOUD_MAX_TICKS = 400;
	public static final float SHOWER_DAMAGE = 6f;
	public static final double SHOWER_SPLASH = 1.6;
	public static final int SHOWER_COOLDOWN = 200;

	public static final int SHIELD_PIECES = 6;
	public static final int SHIELD_TICKS = 160;
	public static final double SHIELD_RADIUS = 1.8;
	public static final double SHIELD_CATCH_RADIUS = 2.8;
	public static final int SHIELD_COOLDOWN = 400;

	/** Falling rubble: gravity a tick and the fastest it falls; a piece that never lands goes after this long. */
	public static final double FALL_GRAVITY = 0.08;
	public static final double FALL_MAX_SPEED = 2.0;
	public static final int FALL_LIFE = 200;

	// ---------------------------------------------------------------- G: Comet Toss / Shift+G: Zero Gravity Slam
	/** Picks the floated thing nearest the crosshair within this many degrees and blocks. */
	public static final double COMET_PICK_DEGREES = 35.0;
	public static final double COMET_PICK_RANGE = 24.0;
	public static final double COMET_SPEED = 2.2;
	public static final double COMET_BLOCK_SPEED = 1.8;
	/** A thrown creature (or block) hits what it smashes into for this (v0.17.1: 10, from 8), and what is round it for {@link #COMET_SPLASH_DAMAGE}. */
	public static final float COMET_DAMAGE = 10f;
	public static final float COMET_SPLASH_DAMAGE = 4f;
	/** v0.17.1: Comet Toss with nothing afloat rips up a chunk of the ground under her and throws that. */
	public static final float COMET_CHUNK_SCALE = 1.0f;
	/** v0.17.1 Shift+R Debris Barrage: every piece of orbiting / hanging debris fires along her aim as a shotgun. */
	public static final float BARRAGE_DAMAGE = 4f;
	public static final double BARRAGE_SPEED = 2.1;
	public static final double BARRAGE_SPREAD_DEGREES = 7.0;
	public static final int BARRAGE_COOLDOWN = 100;
	public static final double COMET_SPLASH = 1.8;
	public static final int COMET_WATCH_TICKS = 40;
	public static final int COMET_COOLDOWN = 30;

	public static final double SLAM_REACH = 5.0;
	/** v0.17.1: 8 blocks up (from 7). */
	public static final double SLAM_HEIGHT = 8.0;
	public static final double SLAM_RISE_SPEED = 0.6;
	public static final int SLAM_LIFT_TICKS = 16;
	public static final double SLAM_SPEED = 1.6;
	public static final float SLAM_DAMAGE = 8f;
	public static final double SLAM_SPLASH_RADIUS = 2.0;
	public static final float SLAM_SPLASH_DAMAGE = 4f;
	public static final int SLAM_WATCH_TICKS = 60;
	public static final int SLAM_COOLDOWN = 160;

	// ---------------------------------------------------------------- X: Float Self / Shift+X: Zero-G Leap
	public static final double SELF_RISE_SPEED = 0.28;
	public static final double SELF_SINK_SPEED = 0.32;
	/** With no input she glides down this slowly (blocks a tick). */
	public static final double SELF_GLIDE_SINK = 0.045;
	public static final double SELF_HOVER_DRAG = 0.85;
	public static final double SELF_MAX_SPEED = 0.6;
	public static final int SELF_COOLDOWN = 10;
	/** Her gravity during the leap is cut by this fraction (1 = none at all). */
	public static final double LEAP_GRAVITY_CUT = 0.85;
	public static final double LEAP_FORWARD = 1.5;
	public static final double LEAP_UP = 1.1;
	public static final int LEAP_MAX_TICKS = 160;
	public static final int LEAP_COOLDOWN = 120;
	/** v0.17.1 Shift+X Weightless Push: an air dash off nothing, about 8 blocks, twice before she lands. */
	public static final double PUSH_SPEED = 1.55;
	public static final int PUSH_TICKS = 5;
	public static final int PUSH_AIR_USES = 2;
	public static final int PUSH_COOLDOWN = 16;
	/** Its Nausea sting, x the touch's ({@code perFloat}). */
	public static final float PUSH_STING = 0.5f;

	// ---------------------------------------------------------------- Z: Gunhead Martial Arts / Shift+Z: Field
	public static final double GUNHEAD_REACH = 3.5;
	/** Palm strike, the touch, then the arm-lock throw, this many ticks apart (v0.17.1: 4 / 4 / 8, the throw floats it). */
	public static final int GUNHEAD_GAP = 6;
	public static final float[] GUNHEAD_DAMAGE = { 4f, 4f, 8f };
	/** The throw sends it over her shoulder (behind her): back this fast, up this fast; a floating target much further. */
	public static final double GUNHEAD_THROW_BACK = 1.0;
	public static final double GUNHEAD_THROW_UP = 0.6;
	public static final double GUNHEAD_FLOAT_THROW = 2.0;
	public static final int GUNHEAD_COOLDOWN = 80;

	public static final double FIELD_RANGE = 10.0;
	/** v0.17.1: radius 5 for 4 s. */
	public static final double FIELD_RADIUS = 5.0;
	public static final int FIELD_TICKS = 80;
	public static final double FIELD_RISE = 1.4;
	public static final int FIELD_COOLDOWN = 400;

	// ---------------------------------------------------------------- Shift+C: Gravity Bomb (v0.17.1)
	/** A 5 x 3 x 5 slab of ground in front of her floats up overhead... */
	public static final int BOMB_WIDTH = 5;
	public static final int BOMB_DEPTH = 3;
	public static final double BOMB_AHEAD = 4.5;
	public static final double BOMB_HEIGHT = 7.0;
	/** ...hangs at most this long (Shift+C again drops it sooner), then falls on her aim: 20 in radius 4 (x the band). */
	public static final int BOMB_HOLD_TICKS = 100;
	public static final double BOMB_AIM_RANGE = 28.0;
	public static final float BOMB_DAMAGE = 20f;
	public static final double BOMB_RADIUS = 4.0;
	public static final int BOMB_COOLDOWN = 400;
	/** Its Nausea sting, x the touch's ({@code perFloat}): the whole slab's weight in one go. */
	public static final float BOMB_STING = 2f;

	// ---------------------------------------------------------------- hold V: Catch / Shift + hold V: Settle
	/** v0.17.1: hold to catch everyone falling within 12 blocks (from 16, a one-shot). */
	public static final double CATCH_RADIUS = 12.0;
	/** Falling: this far already, or faster than this. */
	public static final double CATCH_MIN_FALL = 2.5;
	public static final double CATCH_MIN_SPEED = 0.6;
	public static final int CATCH_SLOW_FALL_TICKS = 100;
	public static final float CATCH_NAUSEA = 3f;
	public static final int CATCH_COOLDOWN = 120;

	/** A catch this far into a fall (or a squadmate) is a rescue: Plus Ultra's ally-save credit. */
	public static final float CATCH_SAVE_FALL = 4f;

	// ---------------------------------------------------------------- Plus Ultra: Full Release (v0.17.1)
	/** Fingertips spread: every mob, loose block and item within this floats up... */
	public static final double FR_RADIUS = 16.0;
	public static final double FR_RISE = 6.0;
	public static final int FR_PIECES = 18;
	/** ...for this long (bosses: rooted and lifted 3 blocks)... */
	public static final int FR_FLOAT_TICKS = 80;
	public static final double FR_BOSS_LIFT = 3.0;
	/** ..."Release!": everything crashes down -- a hit by height on landing plus the rubble's Meteor Shower -- */
	public static final float FR_SLAM_DAMAGE = 6f;
	public static final double FR_SLAM_SPEED = 1.8;
	/** ...and this long after the clap she is sick (Nausea 100). */
	public static final int FR_SICK_DELAY = 30;

	/** Settle lasts at most this long (it ends sooner if she leaves the ground or uses a move). */
	public static final int SETTLE_TICKS = 120;
	public static final int SETTLE_COOLDOWN = 200;

	// ---------------------------------------------------------------- bosses
	/** Bosses are never floated or thrown, and a hit takes at most this much of their max health (like All Might). */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
}
