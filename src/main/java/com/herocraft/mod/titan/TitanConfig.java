package com.herocraft.mod.titan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.herocraft.mod.HeroCraftMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side balance config for the Titan boss, at {@code config/herocraft_titan.json}. Same
 * hand-rolled GSON approach as {@link com.herocraft.mod.hero.HeroConfig}/{@code EventConfig} --
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
		public double health = 1500.0;
		/** Target height in blocks (15-20 per spec, default 18). Adjust and the hitbox/eye height follow. */
		public double heightBlocks = 18.0;
		public double knockbackResistance = 0.95;
		public double normalSpeed = 4.5 / 20.0; // blocks/tick from blocks/sec
		public double aggressiveSpeed = 5.0 / 20.0;
		public double chargeSpeed = 7.5 / 20.0;
		public double detectionRange = 64.0;
		public double followRange = 96.0;
	}

	/** Attack damages and areas of effect. */
	public static final class Attacks {
		public double punchDamage = 18.0;
		public double punchRange = 6.0;
		public double stompDamage = 30.0;
		public double stompRadius = 4.0;
		public double stompWindupTicks = 16; // 0.8s
		public double slamDamage = 24.0;
		public double slamRadius = 6.0;
		public double grabDamage = 6.0;
		public double holdDamage = 4.0;
		public double throwDamage = 10.0;
		public double throwHorizontalMin = 15.0;
		public double throwHorizontalMax = 25.0;
		public double throwVerticalMin = 6.0;
		public double throwVerticalMax = 10.0;
		public double boulderDamage = 28.0;
		public double boulderImpactRadius = 4.0;
		public double chargeDamage = 32.0;
		public double chargeThrowMin = 8.0;
		public double chargeThrowMax = 12.0;
		public double chargeMaxDistance = 40.0;
	}

	/** Per-attack cooldowns, in ticks. */
	public static final class Cooldowns {
		public int punch = 20;          // 1s
		public int stomp = 100;         // 5s
		public int groundSlam = 140;    // 7s
		public int grab = 200;          // 10s
		public int boulder = 160;       // 8s
		public int charge = 240;        // 12s
		/** Minimum ticks between ANY two attacks, so the Titan telegraphs one move at a time. */
		public int globalAttackDelay = 30;
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
		Path path = FabricLoader.getInstance().getConfigDir().resolve("herocraft_titan.json");
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
			HeroCraftMod.LOGGER.warn("[HeroCraft] could not load Titan config, using defaults", e);
			instance = new TitanConfig();
		}
	}
}
