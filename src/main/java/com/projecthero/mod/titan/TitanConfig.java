package com.projecthero.mod.titan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side balance config for the Titan boss, at {@code config/projecthero_titan.json}. Same
 * hand-rolled GSON approach as {@link com.projecthero.mod.hero.HeroConfig}/{@code EventConfig} --
 * deliberately its own file since the Titan isn't part of the wave/event framework.
 */
public final class TitanConfig {
	private static TitanConfig instance = new TitanConfig();

	public Stats stats = new Stats();
	public Attacks attacks = new Attacks();
	public Cooldowns cooldowns = new Cooldowns();
	public World world = new World();

	/** Titan body stats. */
	public static final class Stats {
		public double disguisedHealth = 80.0;
		public double health = 1800.0;
		/** Target height in blocks (15-20 per spec, default 18). Adjust and the hitbox/eye height follow. */
		public double heightBlocks = 18.0;
		public double knockbackResistance = 1.0;
		public double normalSpeed = 6.0 / 20.0; // blocks/tick from blocks/sec
		/** Chase speed while a target is in sight -- fast enough to run down a sprinting player. */
		public double aggressiveSpeed = 9.5 / 20.0;
		public double chargeSpeed = 20.0 / 20.0;
		/** v0.9.22: the Titan locks onto and chases players from this far away (was 80). */
		public double detectionRange = 150.0;
		/** Must stay comfortably above {@link #detectionRange} -- it drives the FOLLOW_RANGE attribute,
		 *  which bounds both vanilla target retention and how long a navigation path may be. */
		public double followRange = 176.0;
		/** v0.9.22: STEP_HEIGHT attribute -- the Titan auto-steps up ledges this tall (vanilla cap is 10). */
		public double stepHeight = 10.0;
	}

	/** Attack damages and areas of effect. */
	public static final class Attacks {
		// v0.10.11: damage across the board lowered ~20-25%.
		public double punchDamage = 20.0;
		public double punchRange = 7.0;
		/** Basic melee swing -- an always-available hit whenever a player is within reach, on its own
		 *  short cooldown, independent of the telegraphed PUNCH/STOMP/SLAM state machine. */
		public double meleeDamage = 16.0;
		public double meleeRange = 8.0;
		public int meleeCooldownTicks = 16;
		/** v0.10.11: wide backhand sweep -- a mid-range group knockback. */
		public double sweepDamage = 22.0;
		public double sweepRange = 10.0;
		public double stompDamage = 30.0;
		public double stompRadius = 4.5;
		public double stompWindupTicks = 16; // 0.8s
		public double slamDamage = 26.0;
		public double slamRadius = 6.0;
		/** v0.10.11: ground shockwave -- a large radial pulse, damage falls off toward the edge. */
		public double shockwaveDamage = 30.0;
		public double shockwaveRadius = 14.0;
		public double grabDamage = 6.0;
		public double holdDamage = 4.0;
		public double throwDamage = 10.0;
		public double throwHorizontalMin = 15.0;
		public double throwHorizontalMax = 25.0;
		public double throwVerticalMin = 6.0;
		public double throwVerticalMax = 10.0;
		public double boulderDamage = 28.0;
		public double boulderImpactRadius = 4.0;
		/** The boulder's blast: everything within this radius of the impact takes {@link #boulderDamage},
		 *  falling off with distance. This is what makes the ranged attack an AoE. */
		public double boulderAoeRadius = 7.0;
		public double chargeDamage = 34.0;
		public double chargeThrowMin = 8.0;
		public double chargeThrowMax = 12.0;
		public double chargeMaxDistance = 40.0;
	}

	/** Per-attack cooldowns, in ticks. */
	public static final class Cooldowns {
		public int punch = 20;          // 1s
		public int sweep = 70;          // 3.5s
		public int stomp = 80;          // 4s
		public int groundSlam = 110;    // 5.5s
		public int shockwave = 150;     // 7.5s
		public int grab = 160;          // 8s
		public int boulder = 120;       // 6s
		public int charge = 180;        // 9s
		/** Minimum ticks between ANY two telegraphed attacks, so the Titan reads one move at a time. */
		public int globalAttackDelay = 16;
	}

	/** Natural spawning and terrain-destruction toggles. */
	public static final class World {
		public boolean naturalSpawnEnabled = true;
		/** Chance rolled per online overworld player, per {@link #spawnCheckIntervalTicks}. */
		public double spawnChance = 0.01;
		public int spawnCheckIntervalTicks = 6000; // 5 minutes, same cadence as PillagerSpySpawner
		/** No new disguised Titan may spawn within this many blocks of an existing encounter. */
		public double minDistanceBetweenEncounters = 300.0;
		/** Hard ceiling on simultaneously active Titan encounters (disguised or transformed) per world. */
		public int maxActiveTitans = 1;

		public boolean blockDestructionEnabled = true;
		public boolean passiveWalkingDestructionEnabled = true;
		public boolean chargeDestructionEnabled = true;
		public double maxDestructionHardness = 50.0;
		public double maxDestructionRadius = 8.0;
		/** v0.9.22: fraction of blocks the Titan destroys that actually drop an item. The rest are
		 *  removed with no drop, so breaking hundreds of blocks in a fight no longer buries the area
		 *  in item entities and tanks the tick rate. */
		public double blockDropChance = 0.08;
		/** v0.9.22: hard ceiling on blocks removed by any single attack/impact, for the same reason. */
		public int maxBlocksPerDestruction = 240;
		public List<String> blockBlacklist = new ArrayList<>();
	}

	public static TitanConfig get() {
		return instance;
	}

	public static Stats stats() {
		return instance.stats;
	}

	public static Attacks attacks() {
		return instance.attacks;
	}

	public static Cooldowns cooldowns() {
		return instance.cooldowns;
	}

	public static World world() {
		return instance.world;
	}

	private TitanConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("projecthero_titan.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				TitanConfig loaded = gson.fromJson(Files.readString(path), TitanConfig.class);
				if (loaded != null) {
					instance = loaded;
					if (instance.stats == null) {
						instance.stats = new Stats();
					}
					if (instance.attacks == null) {
						instance.attacks = new Attacks();
					}
					if (instance.cooldowns == null) {
						instance.cooldowns = new Cooldowns();
					}
					if (instance.world == null) {
						instance.world = new World();
					}
					if (instance.world.blockBlacklist == null) {
						instance.world.blockBlacklist = new ArrayList<>();
					}
				}
			}
			// Always (re)write so new keys appear after a mod update, exactly like HeroConfig/EventConfig.
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | JsonSyntaxException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load Titan config, using defaults", e);
			instance = new TitanConfig();
		}
	}
}
