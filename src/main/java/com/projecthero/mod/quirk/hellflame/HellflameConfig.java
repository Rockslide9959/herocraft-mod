package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.7: every Hellflame (Endeavor) number. Ticks unless the name says otherwise; Body Heat is the Quirk meter (0..100).
 *
 * <p>v0.16.7 revamp ({@code docs/QUIRK_REVAMP_V0167.md}): the kit is built round BODY HEAT -- the sustained moves (Fire Jet
 * Flight, the Flame Cloak, Prominence Burn) heat him every tick, and at 100 he overheats. The Body Heat numbers live in a
 * file, {@code config/projecthero_quirk_hellflame.json} ({@link #heat()}), so a server can tune how long Endeavor can keep
 * burning; a file written before the revamp is migrated by the {@link VersionedConfig}. Everything else is a constant here.
 */
public final class HellflameConfig {
	private HellflameConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	/** Shift+X: Flashfire Fist: Jet Burn (the id is the old Jet Burn's, so saved cooldowns line up). */
	public static final String JET = "quirk_hellflame_jet_burn";
	/** X (hold): Fire Jet Flight -- the movement key. */
	public static final String FLIGHT = "quirk_hellflame_fire_jet_flight";
	/** R: Hell Spider. */
	public static final String SPIDER = "quirk_hellflame_hell_spider";
	/** Shift+R: Hell Spider: Mesh. */
	public static final String MESH = "quirk_hellflame_hell_spider_net";
	/** C: Fire Wall. */
	public static final String WALL = "quirk_hellflame_fire_wall";
	/** Shift+C: Flame Cloak (toggle). */
	public static final String CLOAK = "quirk_hellflame_flame_cloak";
	/** Z: Hellflame Barrage. */
	public static final String BARRAGE = "quirk_hellflame_hellflame_barrage";
	/** Shift+Z: Fire Pillar. */
	public static final String PILLAR = "quirk_hellflame_fire_pillar";
	/** G (hold): Flashfire Fist: Prominence Burn. */
	public static final String PROMINENCE = "quirk_hellflame_prominence_burn";
	/** Shift+G: Vanishing Fist. */
	public static final String VANISH = "quirk_hellflame_vanishing_fist";
	/** V: Cool Down (works while overheated) -- the utility key. */
	public static final String COOL = "quirk_hellflame_cool_down";
	/** Shift+V: Plus Ultra: Full Power. */
	public static final String PLUS = "quirk_hellflame_plus_ultra";

	// ---------------------------------------------------------------- the file: Body Heat
	public static final VersionedConfig<HeatFile> SPEC = VersionedConfig.builder(HeatFile.class, "projecthero_quirk_hellflame.json", HeatFile::new)
			.balance("heat")
			// v0.16.7 revamp: Body Heat is the heart of the kit (sustained flight / cloak / beam heat, Overheat until cooled,
			// the Plus Ultra burnout). A file from before has none of these tuned: today's numbers.
			.reset(1, "v0.16.7 Quirk revamp -- Body Heat drives Fire Jet Flight, Prominence Burn and Plus Ultra", "heat")
			.build();

	private static HeatFile file = new HeatFile();

	/** The file's root. */
	public static final class HeatFile {
		public Integer configVersion;
		public Heat heat = new Heat();
	}

	/** Body Heat, per second unless the name says ticks. */
	public static final class Heat {
		/** Heat lost a second once no fire has burned for {@link #ventDelayTicks}. */
		public float ventPerSecond = 4.0f;
		public int ventDelayTicks = 30;
		/** In water or rain he cools this many times faster. */
		public float wetMultiplier = 2.5f;
		/** Overheat (100 Heat): no fire moves for this long while the Heat drains to {@link #overheatRecoverTo}. */
		public int overheatTicks = 120;
		public float overheatRecoverTo = 40.0f;
		/** Burning up from the inside while overheated (or burnt out): damage a second. */
		public float overheatDamagePerSecond = 1.0f;
		/** Fire Jet Flight: Heat a second hovering / flying / sprint-flying. */
		public float flightHoverPerSecond = 2.4f;
		public float flightPerSecond = 3.6f;
		public float flightSprintPerSecond = 5.0f;
		/** Flame Cloak: Heat a second while it burns. */
		public float cloakPerSecond = 2.4f;
		/** Prominence Burn: Heat a second while the beam fires (from 0, about 3 s to Overheat). */
		public float prominencePerSecond = 32.0f;
		/** Plus Ultra: the full-power window, then the burnout (Heat pinned at 100, no moves). */
		public int plusUltraSeconds = 15;
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
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most this fraction of their max health a hit, and no knockback. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;
	/** Above this Heat the HUD warns (the bar turns red-hot). */
	public static final float HEAT_WARN = 75f;

	// ---------------------------------------------------------------- Plus Ultra multipliers
	/** Damage of every fire move during Plus Ultra. */
	public static final float PLUS_DAMAGE_MULT = 2.0f;
	/** Size (radius, length, width) of every fire move during Plus Ultra. */
	public static final double PLUS_SIZE_MULT = 1.4;
	/** Cooldowns during Plus Ultra are multiplied by this. */
	public static final float PLUS_COOLDOWN_MULT = 0.5f;
	public static final int PLUS_COOLDOWN = 2400;

	// ---------------------------------------------------------------- X (hold): Fire Jet Flight / Shift+X: Jet Burn
	/** Dash speed, blocks a tick, for {@link #JET_DASH_TICKS} (about 12 blocks). */
	public static final double JET_DASH_SPEED = 1.7;
	public static final int JET_DASH_TICKS = 7;
	/** Whatever is this close to the fist during the dash is punched (and the dash ends). */
	public static final double JET_REACH = 1.9;
	public static final float JET_DAMAGE = 16f;
	public static final double JET_KNOCKBACK = 1.8;
	public static final int JET_BURN_SECONDS = 5;
	public static final float JET_HEAT = 12f;
	public static final int JET_COOLDOWN = 50;

	/** Flight cruise, blocks a tick (15 b/s); Sprint makes it {@link #FLIGHT_SPRINT_SPEED}. */
	public static final double FLIGHT_SPEED = 0.75;
	public static final double FLIGHT_SPRINT_SPEED = 1.2;
	public static final double FLIGHT_VERTICAL = 0.45;
	/** Fraction of the gap to the wanted velocity closed a tick. */
	public static final double FLIGHT_ACCEL = 0.22;
	public static final int FLIGHT_COOLDOWN = 20;

	// ---------------------------------------------------------------- R: Hell Spider / Shift+R: Mesh
	public static final double SPIDER_RANGE = 18.0;
	public static final int SPIDER_THREADS = 5;
	/** Degrees between neighbouring strings. */
	public static final double SPIDER_SPREAD_DEGREES = 6.0;
	/** The fan sweeps this many degrees to each side (right to left, the flick of the fingers). */
	public static final double SPIDER_SWEEP_DEGREES = 22.0;
	public static final int SPIDER_SWEEP_STEPS = 5;
	/** The strings are drawn this long (the sweep runs over the first {@link #SPIDER_SWEEP_TICKS}). */
	public static final int SPIDER_DRAW_TICKS = 12;
	public static final int SPIDER_SWEEP_TICKS = 4;
	/** A target this close to a string is cut (once, however many strings cross it). */
	public static final double SPIDER_THREAD_RADIUS = 0.6;
	public static final float SPIDER_DAMAGE = 9f;
	public static final int SPIDER_BURN_SECONDS = 4;
	public static final float SPIDER_HEAT = 10f;
	public static final int SPIDER_COOLDOWN = 30;

	public static final double MESH_RANGE = 24.0;
	public static final double MESH_RADIUS = 3.5;
	public static final int MESH_TICKS = 60;
	/** Burn damage every {@link #MESH_BURN_INTERVAL} while trapped. */
	public static final float MESH_BURN_DAMAGE = 2.5f;
	public static final int MESH_BURN_INTERVAL = 10;
	public static final float MESH_HEAT = 18f;
	public static final int MESH_COOLDOWN = 240;

	// ---------------------------------------------------------------- C: Fire Wall / Shift+C: Flame Cloak
	/** Half the wall's length (7 blocks long), its height, how far in front of him it rises. */
	public static final double WALL_HALF_LENGTH = 3.5;
	public static final double WALL_HEIGHT = 3.0;
	public static final double WALL_DISTANCE = 3.0;
	public static final double WALL_THICKNESS = 0.9;
	public static final int WALL_TICKS = 120;
	public static final float WALL_DAMAGE = 3f;
	public static final int WALL_HIT_INTERVAL = 10;
	public static final int WALL_BURN_SECONDS = 5;
	public static final float WALL_HEAT = 15f;
	public static final int WALL_COOLDOWN = 200;

	/** The Cloak's aura: everything this close burns. */
	public static final double CLOAK_RADIUS = 2.6;
	public static final float CLOAK_AURA_DAMAGE = 2f;
	public static final int CLOAK_AURA_INTERVAL = 10;
	public static final int CLOAK_IGNITE_SECONDS = 3;
	public static final int CLOAK_COOLDOWN = 20;

	// ---------------------------------------------------------------- Z: Hellflame Barrage / Shift+Z: Fire Pillar
	public static final int BARRAGE_JABS = 8;
	public static final int BARRAGE_INTERVAL = 4;
	public static final double BARRAGE_RANGE = 5.5;
	public static final double BARRAGE_HALF_ANGLE = 22.0;
	public static final float BARRAGE_DAMAGE = 3.5f;
	public static final int BARRAGE_BURN_SECONDS = 2;
	public static final double BARRAGE_PUSH = 0.25;
	public static final float BARRAGE_HEAT_PER_JAB = 2f;
	public static final int BARRAGE_COOLDOWN = 100;

	public static final double PILLAR_RANGE = 24.0;
	/** The ground glows this long before the pillar erupts. */
	public static final int PILLAR_WARN_TICKS = 14;
	/** The column keeps burning this long after the eruption. */
	public static final int PILLAR_BURN_TICKS = 24;
	public static final double PILLAR_RADIUS = 2.0;
	public static final double PILLAR_HEIGHT = 9.0;
	public static final float PILLAR_DAMAGE = 14f;
	public static final float PILLAR_LINGER_DAMAGE = 2f;
	public static final double PILLAR_LIFT = 1.1;
	public static final int PILLAR_BURN_SECONDS = 6;
	public static final float PILLAR_HEAT = 18f;
	public static final int PILLAR_COOLDOWN = 180;

	// ---------------------------------------------------------------- G: Prominence Burn (hold) / Shift+G: Vanishing Fist
	/** The fist is drawn back and flame gathers this long before the beam fires. */
	public static final int PROMINENCE_CHARGE_TICKS = 12;
	/** A tap still fires this long. */
	public static final int PROMINENCE_MIN_TICKS = 10;
	/** The beam gives out on its own after this long (or at Overheat). */
	public static final int PROMINENCE_MAX_TICKS = 160;
	public static final double PROMINENCE_RANGE = 48.0;
	/** The hit width (the visible beam is wider at its flaming edge). */
	public static final double PROMINENCE_RADIUS = 1.6;
	public static final float PROMINENCE_DAMAGE = 7f;
	public static final int PROMINENCE_HIT_INTERVAL = 5;
	public static final double PROMINENCE_PUSH = 0.35;
	public static final int PROMINENCE_BURN_SECONDS = 6;
	public static final int PROMINENCE_COOLDOWN = 240;

	public static final double VANISH_RANGE = 16.0;
	/** The flame decoy burns where he stood this long. */
	public static final int VANISH_DECOY_TICKS = 40;
	/** He reappears behind the target and strikes this many ticks later. */
	public static final int VANISH_STRIKE_DELAY = 3;
	public static final float VANISH_DAMAGE = 16f;
	public static final double VANISH_KNOCKBACK = 1.4;
	public static final int VANISH_BURN_SECONDS = 5;
	public static final float VANISH_HEAT = 20f;
	public static final int VANISH_COOLDOWN = 200;

	// ---------------------------------------------------------------- V: Cool Down / Shift+V: Plus Ultra
	public static final float COOL_BASE_DAMAGE = 4f;
	/** Extra damage per Heat point dumped (100 Heat: 4 + 18 = 22). */
	public static final float COOL_DAMAGE_PER_HEAT = 0.18f;
	public static final double COOL_BASE_RADIUS = 3.0;
	/** Extra radius per Heat point (100 Heat: 3 + 4 = 7 blocks). */
	public static final double COOL_RADIUS_PER_HEAT = 0.04;
	public static final double COOL_KNOCKBACK = 1.0;
	public static final int COOL_BURN_SECONDS = 3;
	public static final int COOL_COOLDOWN = 300;
}
