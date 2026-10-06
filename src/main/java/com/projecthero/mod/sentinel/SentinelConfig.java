package com.projecthero.mod.sentinel;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.15.1: the Sentinel Purge's tuning, at {@code config/projecthero_sentinel.json} (loaded through {@link VersionedConfig},
 * see {@code docs/CONFIGS.md}). Read before the entity types are registered: their default attributes come from here.
 *
 * <p>The natural trigger is a once-a-minute roll per Overworld player at night, and only for superhumans -- the
 * Sentinels hunt mutants. A player with a mutation (the Experimental powers) is the likeliest target, any other power
 * holder a less likely one, and an ordinary player is never targeted. A cooldown per dimension follows every purge.
 */
public final class SentinelConfig {
	public static final VersionedConfig<SentinelConfig> SPEC = VersionedConfig.builder(SentinelConfig.class, "projecthero_sentinel.json", SentinelConfig::new)
			.balance("waves", "drone", "sentinel", "masterMold", "rewards")
			.introduce(1, "v0.15.1 Sentinel Purge")
			.build();

	private static SentinelConfig instance = new SentinelConfig();

	public Integer configVersion;
	public Trigger trigger = new Trigger();
	public Waves waves = new Waves();
	public Drone drone = new Drone();
	public Sentinel sentinel = new Sentinel();
	public MasterMold masterMold = new MasterMold();
	public Rewards rewards = new Rewards();

	public static final class Trigger {
		public boolean naturalSpawnEnabled = true;
		/** Per player per minute, at night in the Overworld, for a player holding a mutation (Experimental power). */
		public double mutantChancePerMinute = 0.004;
		/** The same, for a player holding any other power (Hero-Tier, Symbiote, Speed Force). */
		public double superhumanChancePerMinute = 0.002;
		/** The world must be at least this many days old before a natural purge (gives new worlds a grace period). */
		public int minimumWorldDay = 3;
		/** No second natural purge in a dimension this soon after one started (minutes). */
		public int cooldownMinutes = 60;
	}

	public static final class Waves {
		/** Waves before Master Mold arrives (1-6). */
		public int waveCount = 4;
		/** Each extra fighter adds this fraction of every wave entry. */
		public double perExtraFighter = 0.5;
		/** Live Sentinel Program units at once (waves wait for room). */
		public int enemyCap = 30;
		public int detectionSeconds = 10;
		public int breatherSeconds = 8;
		/** Solo composition per wave: drones, then Sentinels (index 0 = wave 1). */
		public int[] drones = { 6, 5, 4, 4, 4, 4 };
		public int[] sentinels = { 0, 2, 3, 4, 5, 6 };
		/** Sentinels that escort Master Mold in. */
		public int bossEscorts = 2;
		/** How far from the purge centre units come down out of the sky (blocks). */
		public double arrivalRadiusMin = 10.0;
		public double arrivalRadiusMax = 22.0;
		/** How high above the ground a Sentinel starts its thruster descent (blocks). */
		public double arrivalHeight = 26.0;
	}

	public static final class Drone {
		public double health = 16.0;
		public double armor = 2.0;
		public float laserDamage = 4.0f;
		public int laserCooldownTicks = 40;
		public double laserRange = 24.0;
	}

	public static final class Sentinel {
		public double health = 90.0;
		public double armor = 10.0;
		public double armorToughness = 4.0;
		public double meleeDamage = 10.0;
		public double speed = 0.26;
		public float chestBeamDamage = 9.0f;
		public float palmBlastDamage = 7.0f;
		public int beamCooldownTicks = 60;
		public double beamRange = 32.0;
		public float grabDamage = 4.0f;
		public float slamDamage = 14.0f;
		public int grabCooldownTicks = 200;
		/** Chance a Sentinel is a carrier that can release Sentinel Drones. */
		public double carrierChance = 0.4;
		public int dronesPerDeploy = 2;
		public int deployCooldownTicks = 400;
	}

	public static final class MasterMold {
		public double health = 1400.0;
		public double healthPerExtraFighter = 400.0;
		public double maxHealth = 4000.0;
		public double armor = 14.0;
		public double armorToughness = 8.0;
		public double speed = 0.22;
		public float stompDamage = 18.0f;
		public double stompRadius = 9.0;
		public float sweepDamage = 20.0f;
		public double sweepReach = 12.0;
		/** Damage per beam tick (it ticks every 5 game ticks while firing). */
		public float beamDamage = 6.0f;
		public double beamRange = 56.0;
		/** Sentinels it may have fabricated and alive at once, per phase (1, 2, 3). */
		public int[] sentinelCap = { 2, 3, 4 };
		public int deployCooldownTicks = 600;
		public int attackCooldownTicks = 40;
	}

	public static final class Rewards {
		public int experiencePoints = 600;
		public int coresPerFighter = 1;
		public int circuitryMin = 2;
		public int circuitryMax = 5;
		/** Chance of a Mutagenic Serum (a random mutation) per rewarded fighter. */
		public double mutagenicSerumChance = 0.05;
		/** Scales the ores and gems; 0 gives none. */
		public double valuablesMultiplier = 1.0;
	}

	public static SentinelConfig get() {
		return instance;
	}

	public static Trigger trigger() {
		return instance.trigger;
	}

	public static Waves waves() {
		return instance.waves;
	}

	public static Drone drone() {
		return instance.drone;
	}

	public static Sentinel sentinel() {
		return instance.sentinel;
	}

	public static MasterMold masterMold() {
		return instance.masterMold;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	/** Master Mold's health for {@code fighters} fighters: the base plus a share per extra fighter, capped. */
	public static double masterMoldHealthFor(int fighters) {
		MasterMold m = instance.masterMold;
		return Math.min(m.maxHealth, m.health + m.healthPerExtraFighter * Math.max(0, fighters - 1));
	}

	public static void load() {
		instance = SPEC.load();
	}
}
