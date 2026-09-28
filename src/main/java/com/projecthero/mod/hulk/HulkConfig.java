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

	/** Damage, reach and cooldown of every Hulk ability. */
	public static final class Abilities {
		// ---- Thunderclap (R): a cone shockwave in front of him ----
		public float thunderclapDamage = 12.0f;
		public double thunderclapRange = 12.0;
		/** Full width of the cone in degrees. */
		public double thunderclapConeDegrees = 70.0;
		public double thunderclapKnockback = 2.2;
		public int thunderclapCooldownTicks = 160;

		// ---- Ground Smash (G): both fists into the ground, a ring all round him ----
		public float groundSmashDamage = 16.0f;
		public double groundSmashRadius = 7.0;
		public double groundSmashKnockback = 1.5;
		public double groundSmashLift = 0.75;
		public int groundSmashCooldownTicks = 200;

		// ---- Super Leap (X, hold then release) ----
		public int leapMaxChargeTicks = 30;
		public double leapMinBlocks = 10.0;
		public double leapMaxBlocks = 45.0;
		public int leapCooldownTicks = 120;
		public float leapLandingDamage = 8.0f;
		public double leapLandingRadius = 4.5;

		// ---- Sprint Smash (C toggles it for the player; world.sprintSmashEnabled is the server switch) ----
		/** Minimum horizontal speed (blocks / tick) before sprinting into a block breaks it. */
		public double sprintSmashMinSpeed = 0.2;
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
				}
			}
			instance.configVersion = 1;
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
	/** Ticks the body takes to grow (or shrink back); damage-proof while it happens. */
	public static final int GROWTH_TICKS = 30;
	/** Anti-spam gate on H. */
	public static final int TOGGLE_DEBOUNCE_TICKS = 10;
	/** Health healed on top of the carried-over health percentage when the Hulk comes out. */
	public static final float TRANSFORM_HEAL = 20.0f;
	/** After changing back: Weakness + Slowness for this long, and no rage can build. */
	public static final int EXHAUSTED_TICKS = 8 * 20;

	// ---------------- Hulk stats (fixed-id transient attribute modifiers) ----------------
	/** Scale bonus: 1 + 0.8 = 1.8x (a 3.24-block Hulk). */
	public static final double SCALE_BONUS = 0.8;
	public static final double ATTACK_BONUS = 12.0;
	public static final double HEALTH_BONUS = 40.0;
	public static final double KNOCKBACK_RESISTANCE = 0.9;
	public static final double ARMOR_TOUGHNESS_BONUS = 8.0;
	/** A bigger stride: steps straight up a full block. */
	public static final double STEP_HEIGHT_BONUS = 0.5;
	/** Longer arms for a bigger body. */
	public static final double REACH_BONUS = 1.5;
	/** Fast regeneration: {@link #REGEN_AMOUNT} HP every {@link #REGEN_INTERVAL_TICKS} (2 HP a second). */
	public static final int REGEN_INTERVAL_TICKS = 10;
	public static final float REGEN_AMOUNT = 1.0f;
}
