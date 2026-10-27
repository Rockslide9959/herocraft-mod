package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.7, v0.17.1 (the Quirks revamp, {@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.1): every Hellflame (Endeavor) number. Ticks
 * unless the name says otherwise; Body Heat is the Quirk meter (0..100). Heat costs (the {@code *_HEAT} numbers and the
 * per-second rates) are RAW: {@link HellflameMeter#BUILD} paces every one of them (the user: "i dont want like heat or
 * darkness or whatever to build up too quick"), and the pacing gametest drives the same numbers.
 *
 * <p>The Body Heat rates live in a file, {@code config/projecthero_quirk_hellflame.json} ({@link #heat()}), so a server can
 * tune how long Endeavor can keep burning; a file written before the v0.16.7 revamp is migrated by the
 * {@link VersionedConfig}. Everything else is a constant here.
 */
public final class HellflameConfig {
	private HellflameConfig() {
	}

	// ---------------------------------------------------------------- the ability ids (kept ids keep their cooldown saves)
	/** R: Hell Spider. */
	public static final String SPIDER = "quirk_hellflame_hell_spider";
	/** Shift+R: Hell Spider: Mesh. */
	public static final String MESH = "quirk_hellflame_hell_spider_net";
	/** G: Flashfire Fist (a three-punch combo). */
	public static final String FIST = "quirk_hellflame_flashfire_fist";
	/** Shift+G: Vanishing Fist. */
	public static final String VANISH = "quirk_hellflame_vanishing_fist";
	/** Hold G: Flashfire Fist: Hellblast (charge, then the sustained beam). */
	public static final String HELLBLAST = "quirk_hellflame_hellblast";
	/** X: Jet Burst (a flame-thrust dash). */
	public static final String BURST = "quirk_hellflame_jet_burst";
	/** Shift+X: Flashfire Fist: Jet Burn (the id is the old Jet Burn's, so saved cooldowns line up). */
	public static final String JET = "quirk_hellflame_jet_burn";
	/** Hold X: Fire Jet Flight -- the movement key. */
	public static final String FLIGHT = "quirk_hellflame_fire_jet_flight";
	/** Z: Hellflame Barrage. */
	public static final String BARRAGE = "quirk_hellflame_hellflame_barrage";
	/** Shift+Z: Hellflame Pillar. */
	public static final String PILLAR = "quirk_hellflame_fire_pillar";
	/** C: Flame Wave. */
	public static final String WAVE = "quirk_hellflame_flame_wave";
	/** Shift+C: Flame Cloak (12 s). */
	public static final String CLOAK = "quirk_hellflame_flame_cloak";
	/** V: Heat Vent (works while overheated) -- the utility key. The id is the old Cool Down's. */
	public static final String COOL = "quirk_hellflame_cool_down";
	/** Shift+V: Fire Wall. */
	public static final String WALL = "quirk_hellflame_fire_wall";
	/** The Plus Ultra ultimate (hold C at a full gauge): Prominence Burn. Its lang key; no key move of its own. */
	public static final String PROMINENCE = "quirk_hellflame_prominence_burn";

	// ---------------------------------------------------------------- the file: Body Heat
	public static final VersionedConfig<HeatFile> SPEC = VersionedConfig.builder(HeatFile.class, "projecthero_quirk_hellflame.json", HeatFile::new)
			.balance("heat")
			.reset(1, "v0.16.7 Quirk revamp -- Body Heat drives Fire Jet Flight, Prominence Burn and Plus Ultra", "heat")
			// v0.17.1 Quirks revamp: at 100 he pushes past it for 5 s first, then the burnout is 8 s (was 6)
			.reset(2, "v0.17.1 Quirks revamp -- the Overheat burnout after the push past 100 is 8 s", "heat.overheatTicks")
			.build();

	private static HeatFile file = new HeatFile();

	/** The file's root. */
	public static final class HeatFile {
		public Integer configVersion;
		public Heat heat = new Heat();
	}

	/** Body Heat, per second unless the name says ticks (raw: x {@link HellflameMeter#BUILD}). */
	public static final class Heat {
		/** Heat lost a second once no fire has burned for {@link #ventDelayTicks}. */
		public float ventPerSecond = 4.0f;
		public int ventDelayTicks = 30;
		/** In water or rain he cools this many times faster. */
		public float wetMultiplier = 2.5f;
		/** The Overheat burnout (after the push past 100): no fire moves for this long while the Heat drains to {@link #overheatRecoverTo}. */
		public int overheatTicks = 160;
		public float overheatRecoverTo = 40.0f;
		/** Burning up from the inside while burnt out: damage a second. */
		public float overheatDamagePerSecond = 1.0f;
		/** Fire Jet Flight: Heat a second hovering / flying / sprint-flying. */
		public float flightHoverPerSecond = 2.4f;
		public float flightPerSecond = 3.6f;
		public float flightSprintPerSecond = 5.0f;
		/** Flame Cloak: Heat a second while it burns. */
		public float cloakPerSecond = 2.4f;
		/** Unused since v0.17.1 (the beam is Hellblast now: {@link HellflameConfig#HELLBLAST_HEAT_PER_SECOND}); kept so old files still read. */
		public float prominencePerSecond = 32.0f;
		/** Unused since v0.17.1 (Plus Ultra is the shared ultimate gauge); kept so old files still read. */
		public int plusUltraSeconds = 15;
		/** The burnout after the Prominence Burn ultimate (Heat pinned at 100, no fire moves). */
		public int burnoutSeconds = 10;
	}

	public static Heat heat() {
		return file.heat;
	}

	/** Reads (migrating) {@code config/projecthero_quirk_hellflame.json}; called from {@code HellflameMod.initialize}. */
	public static void load() {
		file = SPEC.load();
	}

	// ---------------------------------------------------------------- shared
	/** A key held this long (ticks) is its HOLD move; released sooner it is the TAP move (fired on the release). */
	public static final int HOLD_TICKS = 6;
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most this fraction of their max health a hit, and no knockback. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
	/** Above this Heat the HUD warns (the Inferno band). */
	public static final float HEAT_WARN = 80f;

	// ---------------------------------------------------------------- the bands (HellflameMeter)
	/** Inferno (80+): he takes this much a second (never below 1 HP). */
	public static final float INFERNO_HP_PER_SECOND = 0.5f;
	/** At 100 the Overheat push: this long at x1.5 and 1 HP a second, then the burnout ({@link Heat#overheatTicks}). */
	public static final int OVERHEAT_PUSH_TICKS = 100;
	public static final float OVERHEAT_HP_PER_SECOND = 1.0f;
	/** The full Endeavor suit: Heat builds this much slower (+15% headroom). */
	public static final float SUIT_BUILD = 0.87f;
	/** Hot hands: from Blaze (40 Heat) a punch sets the target alight this long. */
	public static final float HOT_HANDS_FROM = 40f;
	public static final int HOT_HANDS_SECONDS = 3;
	/** In Inferno he melts the snow and ice this close every half second. */
	public static final int MELT_RADIUS = 2;

	// ---------------------------------------------------------------- X: Jet Burst / Shift+X: Jet Burn / hold X: Fire Jet Flight
	/** Jet Burst: blocks a tick for this long (about 10 blocks), no damage. */
	public static final double BURST_SPEED = 1.45;
	public static final int BURST_TICKS = 7;
	public static final float BURST_HEAT = 6f;
	public static final int BURST_COOLDOWN = 24;
	/** Jet Burn: the dash punch through everything in a 14-block line. */
	public static final double JET_DASH_SPEED = 2.0;
	public static final int JET_DASH_TICKS = 7;
	public static final double JET_REACH = 2.0;
	public static final float JET_DAMAGE = 16f;
	public static final double JET_KNOCKBACK = 1.4;
	public static final int JET_BURN_SECONDS = 5;
	public static final float JET_HEAT = 12f;
	public static final int JET_COOLDOWN = 60;
	/** Flight cruise, blocks a tick (18 b/s); Sprint makes it {@link #FLIGHT_SPRINT_SPEED} (26 b/s). */
	public static final double FLIGHT_SPEED = 0.9;
	public static final double FLIGHT_SPRINT_SPEED = 1.3;
	public static final double FLIGHT_VERTICAL = 0.45;
	public static final double FLIGHT_ACCEL = 0.22;
	public static final int FLIGHT_COOLDOWN = 20;

	// ---------------------------------------------------------------- R: Hell Spider / Shift+R: Mesh
	public static final double SPIDER_RANGE = 14.0;
	public static final int SPIDER_THREADS = 5;
	public static final double SPIDER_SPREAD_DEGREES = 6.0;
	public static final double SPIDER_SWEEP_DEGREES = 22.0;
	public static final int SPIDER_SWEEP_STEPS = 5;
	public static final int SPIDER_DRAW_TICKS = 12;
	public static final int SPIDER_SWEEP_TICKS = 4;
	public static final double SPIDER_THREAD_RADIUS = 0.6;
	public static final float SPIDER_DAMAGE = 9f;
	public static final int SPIDER_BURN_SECONDS = 4;
	public static final float SPIDER_HEAT = 10f;
	public static final int SPIDER_COOLDOWN = 30;

	public static final double MESH_RANGE = 24.0;
	public static final double MESH_RADIUS = 3.5;
	public static final int MESH_TICKS = 80;
	public static final float MESH_BURN_DAMAGE = 2.5f;
	public static final int MESH_BURN_INTERVAL = 10;
	public static final float MESH_HEAT = 18f;
	public static final int MESH_COOLDOWN = 240;

	// ---------------------------------------------------------------- G: Flashfire Fist / Shift+G: Vanishing Fist / hold G: Hellblast
	/** Flashfire Fist: jab, cross, then a jet-assisted straight (each G tap is the next punch while the chain is open). */
	public static final float[] FIST_DAMAGE = { 6f, 6f, 10f };
	public static final double FIST_REACH = 3.4;
	public static final double FIST_FINISH_REACH = 4.6;
	public static final double FIST_HALF_ANGLE = 40.0;
	public static final double FIST_LUNGE = 0.95;
	public static final int FIST_BURN_SECONDS = 3;
	public static final float FIST_HEAT = 3f;
	/** Between the punches; after the third the full cooldown. A tap later than {@link #FIST_WINDOW} starts over. */
	public static final int FIST_STEP_COOLDOWN = 5;
	public static final int FIST_COOLDOWN = 24;
	public static final int FIST_WINDOW = 22;
	/** Hellblast: charged while G is held (fires at the release, or by itself when full), then the beam. */
	public static final int HELLBLAST_MIN_CHARGE = 10;
	public static final int HELLBLAST_MAX_CHARGE = 40;
	/** The beam burns this long plus the charge (1.5 - 3 s). */
	public static final int HELLBLAST_BASE_TICKS = 20;
	public static final double HELLBLAST_RANGE = 48.0;
	public static final double HELLBLAST_RADIUS = 1.6;
	public static final float HELLBLAST_DAMAGE = 7f;
	public static final int HELLBLAST_HIT_INTERVAL = 5;
	public static final double HELLBLAST_PUSH = 0.35;
	public static final int HELLBLAST_BURN_SECONDS = 6;
	public static final float HELLBLAST_HEAT_PER_SECOND = 12f;
	public static final int HELLBLAST_COOLDOWN = 240;

	public static final double VANISH_RANGE = 16.0;
	public static final int VANISH_DECOY_TICKS = 40;
	public static final int VANISH_STRIKE_DELAY = 3;
	public static final float VANISH_DAMAGE = 16f;
	public static final double VANISH_KNOCKBACK = 1.4;
	public static final int VANISH_BURN_SECONDS = 5;
	public static final float VANISH_HEAT = 20f;
	public static final int VANISH_COOLDOWN = 200;

	// ---------------------------------------------------------------- Z: Hellflame Barrage / Shift+Z: Hellflame Pillar
	public static final int BARRAGE_JABS = 8;
	public static final int BARRAGE_INTERVAL = 4;
	public static final double BARRAGE_RANGE = 5.5;
	public static final double BARRAGE_HALF_ANGLE = 22.0;
	public static final float BARRAGE_DAMAGE = 3.5f;
	public static final int BARRAGE_BURN_SECONDS = 2;
	public static final double BARRAGE_PUSH = 0.25;
	/** The last jab launches. */
	public static final double BARRAGE_LAUNCH = 1.3;
	public static final double BARRAGE_LAUNCH_LIFT = 0.75;
	public static final float BARRAGE_HEAT_PER_JAB = 2f;
	public static final int BARRAGE_COOLDOWN = 100;

	public static final double PILLAR_RANGE = 24.0;
	public static final int PILLAR_WARN_TICKS = 14;
	/** The column keeps burning this long after the eruption (2 a second). */
	public static final int PILLAR_BURN_TICKS = 60;
	public static final int PILLAR_LINGER_INTERVAL = 10;
	public static final double PILLAR_RADIUS = 2.0;
	public static final double PILLAR_HEIGHT = 9.0;
	public static final float PILLAR_DAMAGE = 14f;
	public static final float PILLAR_LINGER_DAMAGE = 1f;
	public static final double PILLAR_LIFT = 1.1;
	public static final int PILLAR_BURN_SECONDS = 6;
	public static final float PILLAR_HEAT = 18f;
	public static final int PILLAR_COOLDOWN = 180;

	// ---------------------------------------------------------------- C: Flame Wave / Shift+C: Flame Cloak
	public static final double WAVE_RANGE = 10.0;
	public static final double WAVE_HALF_ANGLE = 45.0;
	public static final float WAVE_DAMAGE = 11f;
	public static final int WAVE_BURN_SECONDS = 4;
	public static final double WAVE_KNOCKBACK = 1.2;
	public static final float WAVE_HEAT = 14f;
	public static final int WAVE_COOLDOWN = 120;

	/** The Cloak: 12 s, everything this close burns ({@link #CLOAK_AURA_DAMAGE} every {@link #CLOAK_AURA_INTERVAL}: 3 a second). */
	public static final int CLOAK_TICKS = 240;
	public static final double CLOAK_RADIUS = 3.0;
	public static final float CLOAK_AURA_DAMAGE = 1.5f;
	public static final int CLOAK_AURA_INTERVAL = 10;
	public static final int CLOAK_IGNITE_SECONDS = 3;
	public static final double CLOAK_ARMOR = 2.0;
	public static final int CLOAK_COOLDOWN = 240;

	// ---------------------------------------------------------------- V: Heat Vent / Shift+V: Fire Wall
	/** Heat Vent dumps up to this much Heat. */
	public static final float COOL_MAX_VENT = 60f;
	public static final float COOL_BASE_DAMAGE = 4f;
	public static final float COOL_DAMAGE_PER_HEAT = 0.18f;
	/** Radius 3, growing to 4 with a full vent. */
	public static final double COOL_BASE_RADIUS = 3.0;
	public static final double COOL_RADIUS_PER_HEAT = 1.0 / 60.0;
	public static final double COOL_KNOCKBACK = 1.0;
	public static final int COOL_BURN_SECONDS = 3;
	/** It puts out allies this close and evaporates the water in this radius round his feet (3x3). */
	public static final double COOL_ALLY_RADIUS = 4.0;
	public static final int COOL_WATER_RADIUS = 1;
	public static final int COOL_COOLDOWN = 200;

	/** Half the wall's length (7 blocks long), its height, how far in front of him it rises. */
	public static final double WALL_HALF_LENGTH = 3.5;
	public static final double WALL_HEIGHT = 3.0;
	public static final double WALL_DISTANCE = 3.0;
	public static final double WALL_THICKNESS = 0.9;
	public static final int WALL_TICKS = 160;
	public static final float WALL_DAMAGE = 4f;
	public static final int WALL_HIT_INTERVAL = 10;
	public static final int WALL_BURN_SECONDS = 5;
	public static final float WALL_HEAT = 15f;
	public static final int WALL_COOLDOWN = 240;

	// ---------------------------------------------------------------- Plus Ultra: Prominence Burn
	/** The ignition: a white-hot sun, the Heat pinned at 100. */
	public static final int PROM_IGNITE_TICKS = 30;
	/** The comet: this fast along the aim, at most this long (40 blocks). */
	public static final double PROM_SPEED = 2.0;
	public static final int PROM_MAX_TICKS = 20;
	public static final double PROM_HIT_RADIUS = 2.6;
	public static final float PROM_PASS_DAMAGE = 20f;
	public static final float PROM_BLAST_DAMAGE = 45f;
	public static final double PROM_BLAST_RADIUS = 8.0;

	// ---------------------------------------------------------------- survival: the Forge Hand
	/** Sneak + use with an empty main hand: the off hand's raw food / ore is cooked / smelted, this much Heat each. */
	public static final float FORGE_HEAT = 4f;
	public static final int FORGE_COOLDOWN = 8;
}
