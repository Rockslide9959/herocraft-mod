package com.projecthero.mod.titanshifter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Balance config for the Titan Shifter Hero-Tier power (v0.12.31), at
 * {@code config/projecthero_titan_shifter.json}. Same hand-rolled GSON approach as
 * {@link com.projecthero.mod.titan.TitanConfig}. Every value here is a default for the
 * {@link TitanType#GENERIC_TITAN} form; later Titan types read their own stats from {@link TitanType}.
 *
 * <p>Ticks: 20 per second. Two things are baked in at registration and need a full game restart to change:
 * the Titan's hit-box ({@link Stats#heightBlocks}/{@link Stats#widthBlocks}) and nothing else -- every other
 * value is re-read live.
 */
public final class TitanShifterConfig {
	private static TitanShifterConfig instance = new TitanShifterConfig();

	/**
	 * Config schema, bumped whenever a default changes in a way a stale file must not override (see {@link #load}).
	 * Deliberately a nullable {@link Integer} with no initialiser: GSON builds the object through the no-arg
	 * constructor, so an {@code int} defaulting to 2 would read as "2" for a v0.12.31 file that has no such key.
	 */
	public Integer configVersion;

	public Stats stats = new Stats();
	public Damage damage = new Damage();
	public Abilities abilities = new Abilities();
	public Transformation transformation = new Transformation();
	public Resistances resistances = new Resistances();
	public Effects effects = new Effects();
	public World world = new World();
	public Energy energy = new Energy();

	public static final class Stats {
		/** Titan hit-box height in blocks -- a regular player, scaled up to 11 blocks tall (v0.12.32). */
		public double heightBlocks = 11.0;
		/** A player's 0.6 x 1.8 hit-box scaled by 11 / 1.8 = 6.11 keeps its proportions: 0.6 * 6.11 = 3.67. */
		public double widthBlocks = 3.67;
		public double maxHealth = 500.0;
		public double armor = 25.0;
		public double armorToughness = 8.0;
		public double knockbackResistance = 1.0;
		/** The MOVEMENT_SPEED attribute (spec: ~0.45). */
		public double movementSpeed = 0.45;
		/** Ridden speed = attribute x this. 0.55 keeps a heavy, ~8 blocks/s stride rather than a 14 blocks/s sprint. */
		public double riddenSpeedFactor = 0.55;
		public double stepHeight = 2.5;
		/** Plain jump (Space) launch velocity; a player's is 0.42. */
		public double jumpVelocity = 0.75;
	}

	public static final class Damage {
		public double punch = 20.0;
		public double heavyPunch = 35.0;
		public double kick = 30.0;
		public double stomp = 25.0;
		/** Heavy Smash (Ability 2). */
		public double heavySmash = 50.0;
		public double leapLanding = 20.0;
		/** Multipliers on every Titan hit by target type. */
		public double playerFactor = 0.6;
		public double mobFactor = 1.0;
		/** A boss (max health above {@link #bossHealthThreshold}) loses at most this fraction of its max health per hit. */
		public double bossMaxFractionPerHit = 0.06;
		public double bossHealthThreshold = 300.0;
		public double punchKnockback = 1.6;
		public double smashKnockback = 3.2;
	}

	public static final class Abilities {
		public int punchCooldown = 16;        // 0.8 s
		public int smashCooldown = 160;       // 8 s
		public int smashChargeTicks = 20;
		public double smashSlowFactor = 0.25;
		public double smashRadius = 3.5;
		public int stompCooldown = 120;       // 6 s
		public double stompRadius = 6.0;
		public int leapCooldown = 100;        // 5 s
		public double leapVertical = 1.26;    // ~3x a normal jump
		public double leapHorizontal = 3.0;   // ~20 blocks of flight (measured)
		public double leapLandingRadius = 5.0;
		public int roarCooldown = 300;        // 15 s
		public double roarRadius = 12.0;
		public int roarEffectTicks = 200;
		/** Bosses only get this fraction of the roar's effect duration and are not knocked back. */
		public double roarBossResistance = 0.25;
		public double regenPerSecond = 10.0;
		public int regenTicks = 200;          // 10 s
		public int regenCooldown = 900;       // 45 s
		public double hardenDamageReduction = 0.6;
		public int hardenTicks = 160;         // 8 s
		public int hardenCooldown = 600;      // 30 s
	}

	public static final class Transformation {
		public int transformTicks = 60;
		public int revertTicks = 40;
		public int defeatTicks = 60;
		public int recoveryTicks = 100;
		/** Cooldown after a normal reversion, a defeat or a forced end. v0.12.32: none -- the Titan Energy bar is the gate. */
		public int cooldownTicks = 0;
		/** Weak blocks (leaves, plants, glass...) inside the Titan's footprint are burst apart when it forms. */
		public boolean clearWeakBlocksOnTransform = true;
		/** The Titan Serum only unlocks the power; it never transforms the player by itself. */
		public boolean serumTransformsImmediately = false;
	}

	public static final class Resistances {
		public double fireDamageFactor = 0.2;
		public double explosionDamageFactor = 0.5;
		public double fallDamageFactor = 0.1;
		/** Hits below this many damage are cut to {@link #minorHitFactor} of their value. */
		public double minorHitThreshold = 4.0;
		public double minorHitFactor = 0.25;
	}

	public static final class Effects {
		public boolean footsteps = true;
		public double strideBlocks = 5.5;
		public boolean cameraShake = true;
		public double shakeRadius = 40.0;
		public double steamHealthFraction = 0.35;
	}

	public static final class World {
		/** The Titan tramples leaves/plants/glass in front of it as it walks. */
		public boolean trampleWeakBlocks = true;
		/** Punches, stomps and landings break weak blocks in their area. Off by default: no terrain damage. */
		public boolean abilityBlockDestruction = false;
		public double weakBlockMaxHardness = 0.6;
		public int maxBlocksPerAction = 60;
	}

	/** The Titan Energy bar (v0.12.32): fills while human, is spent by the Titan's base regeneration, empties on reverting. */
	public static final class Energy {
		public double max = 100.0;
		/** Energy gained per second while NOT a Titan (1% of a 100 bar). */
		public double regenPerSecond = 1.0;
		/** A transformation needs at least this fraction of the bar. */
		public double transformMinFraction = 0.9;
		/** Base Titan regeneration: hit points restored per second whenever the Titan is not at full health ... */
		public double baseRegenHpPerSecond = 3.0;
		/** ... paid for with this much Titan Energy per second (the regeneration stops when the bar is empty). */
		public double baseRegenEnergyPerSecond = 2.0;
	}

	public static TitanShifterConfig get() {
		return instance;
	}

	public static Stats stats() {
		return instance.stats;
	}

	public static Damage damage() {
		return instance.damage;
	}

	public static Abilities abilities() {
		return instance.abilities;
	}

	public static Transformation transformation() {
		return instance.transformation;
	}

	public static Resistances resistances() {
		return instance.resistances;
	}

	public static Effects effects() {
		return instance.effects;
	}

	public static World world() {
		return instance.world;
	}

	public static Energy energy() {
		return instance.energy;
	}

	private TitanShifterConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("projecthero_titan_shifter.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				TitanShifterConfig loaded = gson.fromJson(Files.readString(path), TitanShifterConfig.class);
				if (loaded != null) {
					instance = loaded;
					if (instance.stats == null) instance.stats = new Stats();
					if (instance.damage == null) instance.damage = new Damage();
					if (instance.abilities == null) instance.abilities = new Abilities();
					if (instance.transformation == null) instance.transformation = new Transformation();
					if (instance.resistances == null) instance.resistances = new Resistances();
					if (instance.effects == null) instance.effects = new Effects();
					if (instance.world == null) instance.world = new World();
					if (instance.energy == null) instance.energy = new Energy();
					if (loaded.configVersion == null || loaded.configVersion < 2) {
						// v0.12.32 changed the body (regular player, 3.67 wide) and dropped the 60 s cooldown for the energy bar:
						// a config file written by v0.12.31 must not keep the old values.
						instance.stats.widthBlocks = new Stats().widthBlocks;
						instance.transformation.cooldownTicks = new Transformation().cooldownTicks;
					}
				}
			}
			instance.configVersion = 2;
			// always rewrite so newly added keys appear in existing files
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | RuntimeException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load Titan Shifter config, using defaults", e);
			instance = new TitanShifterConfig();
		}
	}
}
