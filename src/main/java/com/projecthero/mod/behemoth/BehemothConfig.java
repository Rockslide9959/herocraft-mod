package com.projecthero.mod.behemoth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side balance config for The Abyssal Behemoth, at {@code config/projecthero_behemoth.json}.
 * Same hand-rolled GSON approach as {@link com.projecthero.mod.titan.TitanConfig} -- its own file since
 * this boss, like the Titan, is a standalone wilderness/dimension encounter rather than part of the
 * scripted raid/event framework.
 */
public final class BehemothConfig {
	private static BehemothConfig instance = new BehemothConfig();

	public Stats stats = new Stats();
	public Abilities abilities = new Abilities();
	public Phases phases = new Phases();
	public World world = new World();

	/** Body stats. */
	public static final class Stats {
		public double health = 3000.0;
		public double knockbackResistance = 0.95;
		public double meleeDamage = 80.0;
		public double meleeRange = 7.0;
		/** Hover/chase speed, blocks/tick. */
		public double flightSpeed = 11.0 / 20.0;
		public double aggressiveFlightSpeed = 16.0 / 20.0;
		public double detectionRange = 90.0;
		public double followRange = 110.0;
		/** How far above its target it prefers to hover while airborne and aggressive. */
		public double preferredAerialAltitude = 12.0;
	}

	/** Ability numbers -- one block per ability, matching the spec's own section numbering. */
	public static final class Abilities {
		// 1: Abyssal Fireball
		public float fireballDamage = 100.0f;
		public double fireballSpeed = 1.6;
		public float fireballExplosionRadius = 3.0f;
		public int fireballCooldownTicks = 5 * 20;

		// 2: Hellfire Barrage
		public int hellfireCount = 6;
		public float hellfireDamageEach = 22.0f;
		public double hellfireSpeed = 1.4;
		public int hellfireCooldownTicks = 10 * 20;

		// 3: Magma Rain
		public int magmaRainCount = 10;
		public float magmaRainDamage = 26.0f;
		public double magmaRainRadius = 16.0;
		public int magmaRainWarningTicks = 30; // 1.5s telegraph before impact
		public int magmaRainCooldownTicks = 16 * 20;

		// 4: Abyssal Beam
		public float beamDamagePerTick = 9.0f;
		public int beamChargeTicks = 40; // 2s
		public int beamFireTicks = 30; // 1.5s sweep
		public double beamRange = 40.0;
		public double beamWidth = 2.2;
		public int beamCooldownTicks = 20 * 20;

		// 5: Cinder Tether
		public float cinderTetherDamage = 18.0f;
		public int cinderTetherCount = 3;
		public double cinderTetherRange = 30.0;
		public int lowAltitudeTicks = 8 * 20;
		public int cinderTetherCooldownTicks = 22 * 20;

		// 6: Netherstorm
		public int netherstormDurationTicks = 13 * 20;
		public float netherstormStrikeDamage = 14.0f;
		public double netherstormRadius = 20.0;
		public int netherstormCooldownTicks = 40 * 20;

		// 7: Hellwind
		public float hellwindDamage = 30.0f;
		public double hellwindRadius = 14.0;
		public double hellwindKnockback = 3.2;
		public int hellwindCooldownTicks = 14 * 20;

		// 8: Sovereign Descent
		public float sovereignDescentDamage = 45.0f;
		public double sovereignDescentRadius = 10.0;
		public int groundAssaultTicks = 10 * 20;
		public int sovereignDescentCooldownTicks = 18 * 20;

		// 9: Skyfall
		public int skyfallCount = 4;
		public float skyfallDamageEach = 24.0f;
		public double skyfallSpeed = 1.5;
		/** A target this far above the ground (or the boss) for this long provokes Skyfall. */
		public double skyfallAltitudeTrigger = 14.0;
		public int skyfallAirborneTicksTrigger = 60; // 3s
		public int skyfallCooldownTicks = 12 * 20;

		/** No two abilities may start within this many ticks of each other. */
		public int globalAbilityLockTicks = 20;
	}

	/** Phase thresholds and their multipliers (spec sections 17-20). */
	public static final class Phases {
		public double phase2HealthFraction = 0.60;
		public double phase3HealthFraction = 0.25;
		public double phase2CooldownMultiplier = 0.75;
		public double phase2SpeedMultiplier = 1.20;
		public double phase2DamageMultiplier = 1.15;
		public double phase3CooldownMultiplier = 0.55;
		public double phase3SpeedMultiplier = 1.45;
		public double phase3DamageMultiplier = 1.30;
		/** Combat time before Abyssal Enrage kicks in (spec: ~5 minutes). */
		public int enrageTicks = 5 * 60 * 20;
		public double enrageCooldownMultiplier = 0.7;
		public double enrageSpeedMultiplier = 1.25;
	}

	/** Natural-spawning rules. */
	public static final class World {
		public boolean naturalSpawnEnabled = true;
		public int spawnCheckIntervalTicks = 20 * 20; // every 20s, a cheap per-player roll
		/** Tuned so an average server sees roughly one successful spawn every few hours of Nether play. */
		public double spawnChance = 0.0009;
		public double minPlayerDistance = 48.0;
		public double maxPlayerDistance = 128.0;
		public int maxActivePerDimension = 1;
		/** After a natural spawn, or a kill, no new one may appear in that Nether dimension for this long. */
		public int postSpawnCooldownTicks = 40 * 60 * 20; // 40 minutes
		public int postKillCooldownTicks = 90 * 60 * 20; // 90 minutes
	}

	private BehemothConfig() {
	}

	public static Stats stats() {
		return instance.stats;
	}

	public static Abilities abilities() {
		return instance.abilities;
	}

	public static Phases phases() {
		return instance.phases;
	}

	public static World world() {
		return instance.world;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("projecthero_behemoth.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				BehemothConfig loaded = gson.fromJson(Files.readString(path), BehemothConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			}
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | JsonSyntaxException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load Abyssal Behemoth config, using defaults", e);
		}
	}
}
