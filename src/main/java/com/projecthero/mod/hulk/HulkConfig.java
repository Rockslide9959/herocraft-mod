package com.projecthero.mod.hulk;

/**
 * Every Hulk tunable. The core rage / transform loop (Phase 1) is static finals like {@code AllMightConfig};
 * the abilities and the world rules (Phase 2) live in a JSON file, {@code config/projecthero_hulk.json}, re-read
 * at start-up and rewritten so new keys appear -- same hand-rolled GSON approach as {@code TitanShifterConfig}.
 * Ticks are 20 per second.
 */
public final class HulkConfig {
	private static HulkConfig instance = new HulkConfig();

	/** Schema version (nullable on purpose -- see {@code TitanShifterConfig#configVersion}). */
	public Integer configVersion;
	public Abilities abilities = new Abilities();
	public World world = new World();
	public Control control = new Control();
	public Calm calm = new Calm();

	/** Damage, reach and cooldown of every Hulk ability (v0.13.14 kit: R G Z X C V). */
	public static final class Abilities {
		// ---- R Power Punch: a 3-block-wide, 6-block-long punch ----
		public float powerPunchDamage = 30.0f;
		public double powerPunchRange = 6.0;
		public double powerPunchWidth = 3.0;
		public double powerPunchKnockback = 2.6;
		public int powerPunchCooldownTicks = 100;

		// ---- G Ground Smash: both fists into the ground, a ring all round him and a crater ----
		public float groundSmashDamage = 30.0f;
		public double groundSmashRadius = 7.0;
		public double groundSmashKnockback = 1.5;
		public double groundSmashLift = 0.75;
		public double groundSmashCraterRadius = 3.0;
		public int groundSmashCooldownTicks = 100;

		// ---- Z Thunderclap: a cone shockwave in front of him ----
		public float thunderclapDamage = 22.0f;
		public double thunderclapRange = 12.0;
		/** Full width of the cone in degrees. */
		public double thunderclapConeDegrees = 70.0;
		public double thunderclapKnockback = 2.2;
		public int thunderclapCooldownTicks = 160;

		// ---- Shift+Z HULK SMASH: hold 5 s, then everything in front and around him ----
		public int hulkSmashChargeTicks = 100;
		public float hulkSmashDamage = 100.0f;
		public double hulkSmashForwardRange = 25.0;
		public double hulkSmashForwardWidth = 8.0;
		public double hulkSmashRadius = 30.0;
		public double hulkSmashCraterRadius = 6.0;
		public int hulkSmashCooldownTicks = 1200;

		// ---- X Super Leap (hold then release) ----
		public int leapMaxChargeTicks = 30;
		public double leapMinBlocks = 15.0;
		public double leapMaxBlocks = 70.0;
		public int leapCooldownTicks = 40;
		public float leapLandingDamage = 8.0f;
		public double leapLandingRadius = 4.5;

		// ---- C Charge: runs forward on his own for 8 s ----
		public int chargeTicks = 160;
		/** Blocks per tick (0.6 = 12 blocks a second). */
		public double chargeSpeed = 0.6;
		public float chargeDamage = 20.0f;
		public double chargeHitRadius = 2.4;
		/** Counted from when the run ends. */
		public int chargeCooldownTicks = 240;

		// ---- V Grab / Throw / Crush, Shift+V Earth Chunk ----
		public double grabRange = 6.0;
		public float crushDamage = 26.0f;
		public double throwSpeed = 2.0;
		public float throwImpactDamage = 16.0f;
		public float boulderDamage = 30.0f;
		public double boulderRadius = 4.0;
		public double boulderSpeed = 1.7;
		/** Counted from the throw / crush. */
		public int grabCooldownTicks = 160;

		// ---- Sprint Smash (always on while sprinting as the Hulk; world.sprintSmashEnabled is the server switch) ----
		/** Minimum horizontal speed (blocks / tick) before sprinting into a block breaks it. */
		public double sprintSmashMinSpeed = 0.2;
	}

	/** Keeping control of the Hulk: stop dealing damage and he starts to take over. */
	public static final class Control {
		public boolean enabled = true;
		/** Ticks without dealing damage before control starts to slip. */
		public int graceTicks = 160;
		public float drainPerSecond = 6.0f;
		/** While control is slipping, a key prompt appears this often... */
		public int promptEveryTicks = 50;
		/** ...and must be answered within this long. */
		public int promptWindowTicks = 30;
		public float promptRestore = 25.0f;
		public float promptPenalty = 15.0f;
		/** How long a rampage lasts before the player wrestles control back. */
		public int rampageTicks = 300;
		/** Control the player gets back when a rampage ends. */
		public float rampageRestore = 60.0f;
	}

	/** The calm-down minigame (hold N). */
	public static final class Calm {
		public int holdTicks = 40;
		/** Out of combat this long before it can start. */
		public int outOfCombatTicks = 60;
		public float hitDrain = 14.0f;
		public float missRage = 4.0f;
	}

	/** What the Hulk is allowed to do to the world. */
	public static final class World {
		/** Master switch for every block the Hulk breaks (Thunderclap's fragile blocks, Sprint Smash). */
		public boolean blockBreaking = true;
		/** Server switch for Sprint Smash, so worlds don't get wrecked. */
		public boolean sprintSmashEnabled = true;
		/** Sprint Smash only breaks blocks at or below this hardness (dirt 0.5, wood 2, stone 1.5, ores 3, obsidian 50). */
		public float maxBreakableHardness = 3.0f;
		/** Broken blocks drop their items. */
		public boolean dropBrokenBlocks = true;
		/** If true, the mobGriefing gamerule also stops the Hulk breaking blocks. */
		public boolean respectMobGriefing = false;
		/** Camera shake from Ground Smash, Thunderclap and leap landings. */
		public boolean screenShake = true;
	}

	private HulkConfig() {
	}

	public static HulkConfig get() {
		return instance;
	}

	public static Abilities abilities() {
		return instance.abilities;
	}

	public static World world() {
		return instance.world;
	}

	public static Control control() {
		return instance.control;
	}

	public static Calm calm() {
		return instance.calm;
	}

	public static void load() {
		java.nio.file.Path path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("projecthero_hulk.json");
		com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
		try {
			if (java.nio.file.Files.exists(path)) {
				HulkConfig loaded = gson.fromJson(java.nio.file.Files.readString(path), HulkConfig.class);
				if (loaded != null) {
					instance = loaded;
					if (instance.abilities == null) instance.abilities = new Abilities();
					if (instance.world == null) instance.world = new World();
					if (instance.control == null) instance.control = new Control();
					if (instance.calm == null) instance.calm = new Calm();
					if (loaded.configVersion == null || loaded.configVersion < 2) {
						// v0.13.14 rebuilt the kit (new keys, new damage and cooldowns): a 0.13.13 file must not keep the old numbers
						instance.abilities = new Abilities();
					}
				}
			}
			instance.configVersion = 2;
			java.nio.file.Files.createDirectories(path.getParent());
			java.nio.file.Files.writeString(path, gson.toJson(instance));
		} catch (java.io.IOException | RuntimeException e) {
			com.projecthero.mod.ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load Hulk config, using defaults", e);
			instance = new HulkConfig();
		}
	}

	// ---------------- rage ----------------
	public static final float RAGE_MAX = 100.0f;
	/** H transforms by hand from this much rage; at {@link #RAGE_MAX} the change is forced. */
	public static final float MANUAL_TRANSFORM_RAGE = 75.0f;
	/** Rage per point of damage TAKEN as Banner (10 hearts of damage = +50). */
	public static final float RAGE_PER_DAMAGE_TAKEN = 2.5f;
	/** Rage per point of damage DEALT to a mob as Banner (fighting builds it too, slower than getting hurt). */
	public static final float RAGE_PER_DAMAGE_DEALT = 0.8f;
	/** Rage per point of damage TAKEN as the Hulk -- the angrier he gets, the longer he stays. */
	public static final float HULK_RAGE_PER_DAMAGE_TAKEN = 1.5f;
	/** Rage the Hulk burns every second. 100 rage = 100 s of Hulk without being hurt. */
	public static final float HULK_DRAIN_PER_SECOND = 1.0f;
	/** Banner calms down when left alone: rage bleeds off this fast once {@link #CALM_DELAY_TICKS} pass without a fight. */
	public static final float CALM_DECAY_PER_SECOND = 0.5f;
	public static final int CALM_DELAY_TICKS = 15 * 20;
	/** v0.13.12: a Gamma Reactor within this many blocks feeds a Gamma player's rage... */
	public static final int REACTOR_RADIUS = 4;
	/** ...by this much a second (also counts as not being calm, so it never bleeds off beside one). */
	public static final float REACTOR_RAGE_PER_SECOND = 3.0f;

	// ---------------- the change ----------------
	/** Ticks the body takes to grow (or shrink back) -- the change the player chose with H, and every change back. */
	public static final int GROWTH_TICKS = 30;
	/**
	 * v0.13.15: the unwilling change (rage hit the top, or the death save) is slower and happens on his knees: Banner drops for
	 * {@link #FORCED_KNEEL_TICKS}, grows over {@link #FORCED_GROWTH_TICKS} while the Hulk takes him over, then rises and roars over
	 * {@link #FORCED_RISE_TICKS}. He cannot move or use abilities until it is over.
	 */
	public static final int FORCED_KNEEL_TICKS = 20;
	public static final int FORCED_GROWTH_TICKS = 60;
	public static final int FORCED_RISE_TICKS = 24;
	public static final int FORCED_CHANGE_TICKS = FORCED_KNEEL_TICKS + FORCED_GROWTH_TICKS + FORCED_RISE_TICKS;
	/** Anti-spam gate on H. */
	public static final int TOGGLE_DEBOUNCE_TICKS = 10;
	/** Health healed on top of the carried-over health percentage when the Hulk comes out. */
	public static final float TRANSFORM_HEAL = 20.0f;
	/** After changing back: Weakness + Slowness for this long, and no rage can build. */
	public static final int EXHAUSTED_TICKS = 8 * 20;

	// ---------------- Hulk stats (fixed-id transient attribute modifiers) ----------------
	/** Scale bonus: 1 + 0.8 = 1.8x (a 3.24-block Hulk). */
	public static final double SCALE_BONUS = 0.8;
	/** v0.13.14: punches land 20 (1 base + 19). */
	public static final double ATTACK_BONUS = 19.0;
	/** v0.13.14: punches throw things much further. */
	public static final double ATTACK_KNOCKBACK_BONUS = 1.5;
	/** v0.13.14: +50% movement -- a 1.8x body at normal speed looked like it was wading. */
	public static final double SPEED_BONUS = 0.5;
	/** v0.13.14: diamond-level protection built in (a full diamond set: 20 armour, 8 toughness). */
	public static final double ARMOR_BONUS = 20.0;
	public static final double HEALTH_BONUS = 40.0;
	public static final double KNOCKBACK_RESISTANCE = 0.9;
	public static final double ARMOR_TOUGHNESS_BONUS = 8.0;
	/** A bigger stride: steps straight up a full block. */
	public static final double STEP_HEIGHT_BONUS = 0.5;
	/** Longer arms for a bigger body. */
	public static final double REACH_BONUS = 1.5;
	/** v0.13.14: fast regeneration -- {@link #REGEN_AMOUNT} HP every {@link #REGEN_INTERVAL_TICKS} (3 HP a second). */
	public static final int REGEN_INTERVAL_TICKS = 5;
	public static final float REGEN_AMOUNT = 0.75f;
	/** v0.13.14: armour worn when he changes takes this much durability and falls off. */
	public static final int ARMOUR_TEAR_DAMAGE = 50;
	/** v0.13.14: lava and explosions only do this fraction of their damage to the Hulk (fire, arrows and falls: none). */
	public static final float LAVA_FACTOR = 0.25f;
	public static final float EXPLOSION_FACTOR = 0.5f;
	/** v0.13.14: "the Hulk refuses to die" -- once every 3 minutes. */
	public static final int DEATH_SAVE_COOLDOWN_TICKS = 3 * 60 * 20;
}
