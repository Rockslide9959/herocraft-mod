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

	public Stats stats = new Stats();
	public Damage damage = new Damage();
	public Abilities abilities = new Abilities();
	public Transformation transformation = new Transformation();
	public Resistances resistances = new Resistances();
	public Effects effects = new Effects();
	public World world = new World();
	public Controls controls = new Controls();

	public static final class Stats {
		/** Titan hit-box height in blocks (spec: 10-12). */
		public double heightBlocks = 11.0;
		public double widthBlocks = 4.0;
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
		/** Cooldown after a normal reversion, a defeat or a forced end. */
		public int cooldownTicks = 1200;      // 60 s
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

	public static final class Controls {
		/** false: Ability 6 = Titan Regeneration, Utility 1 (H) = Titan Hardening. true swaps them. */
		public boolean slot6IsHardening = false;
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

	public static Controls controls() {
		return instance.controls;
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
					if (instance.controls == null) instance.controls = new Controls();
				}
			}
			// always rewrite so newly added keys appear in existing files
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | RuntimeException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load Titan Shifter config, using defaults", e);
			instance = new TitanShifterConfig();
		}
	}
}
