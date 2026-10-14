package com.projecthero.mod.city;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.2 City part B: the City's systems, at {@code config/projecthero_city.json} (loaded through {@link VersionedConfig},
 * see {@code docs/CONFIGS.md}). {@code repair.enabled} and the bank switches are the server's; the numbers are balance.
 * Times are in ticks (20 a second, 24000 an in-game day) unless the name says otherwise.
 */
public final class CityConfig {
	public static final VersionedConfig<CityConfig> SPEC = VersionedConfig.builder(CityConfig.class, "projecthero_city.json", CityConfig::new)
			.balance("cops", "doctor", "firefighter", "bulletin")
			.introduce(1, "v0.16.2 City part B: repair, cops, doctor, firefighter, bank, Daily Bulletin")
			.build();

	private static CityConfig instance = new CityConfig();

	public Integer configVersion;
	public Repair repair = new Repair();
	public Cops cops = new Cops();
	public Doctor doctor = new Doctor();
	public Firefighter firefighter = new Firefighter();
	public Bank bank = new Bank();
	public Bulletin bulletin = new Bulletin();

	/** The self-repairing city (damage tracking, Repair Worker trips). */
	public static final class Repair {
		/** Repairs on/off. Damage is still not recorded while off. The {@code projectheroCityRepair} gamerule also gates it. */
		public boolean enabled = true;
		/** A damaged 16x16 column waits this long after its latest damage before a worker comes (1200-72000). */
		public int repairDelayTicks = 12000;
		/** A worker rebuilds at most this many blocks per trip, then walks home. */
		public int blocksPerTrip = 512;
		/** Ticks between two rebuilt blocks. */
		public int ticksPerBlock = 10;
		/** Repair Workers per City. With fewer alive, the depot hires one every {@link #hireDelayTicks}. */
		public int workersPerCity = 2;
		public int hireDelayTicks = 24000;
		/** A worker that hasn't got within reach of the next block after this long repairs it remotely. */
		public int reachTimeoutTicks = 600;
		/** A due area that no worker has taken for this long fills in on its own, one block every {@link #selfRepairTicksPerBlock}. */
		public int selfRepairAfterTicks = 24000;
		public int selfRepairTicksPerBlock = 40;
		/** A repaired block drops nothing if broken again within this long (anti-dupe). */
		public int noDropTicks = 24000;
		/** A player block placed within this long of blueprint damage in the same 4x4x4 cell marks the cell "renovated". */
		public int renovationWindowTicks = 2400;
		/** The audit sweep: every this many ticks one loaded City chunk is diffed against the blueprint. */
		public int sweepIntervalTicks = 40;
		/** A chunk is swept again only after this long. */
		public int sweepChunkEveryTicks = 48000;
		/** Repair positions protected by vanilla spawn protection (dedicated servers) too. */
		public boolean repairInsideSpawnProtection = false;
		/** Repairs only happen in villager work hours (day time 2000-9000). */
		public boolean workHoursOnly = true;
	}

	/** City Cop villagers. */
	public static final class Cops {
		public double maxHealth = 40.0;
		public double armor = 8.0;
		/** Baton damage against a monster (a crook takes {@link #crookDamageMultiplier} of it). */
		public float attackDamage = 6.0f;
		public int attackCooldownTicks = 20;
		public double attackReach = 2.4;
		/** Monsters within this range are attacked. */
		public int monsterRange = 12;
		/** Patrol radius around the Precinct Desk during work hours. */
		public int patrolRadius = 32;
		/** Cops within this range of a crime in their City answer it. */
		public int crimeResponseRange = 64;
		/** Cops only engage a crime's crooks once the crime has run this long, so a player gets the first chance. */
		public int crimeEngageDelaySeconds = 20;
		/** Damage multiplier against crime crooks (cops help, the hero still matters). */
		public float crookDamageMultiplier = 0.5f;
		/** Below this fraction of health a cop stops fighting and flees like any villager. */
		public double fleeHealthFraction = 0.25;
		/** Out-of-combat healing, HP a second. */
		public float regenPerSecond = 0.5f;
	}

	/** Clerics working in the City hospital. */
	public static final class Doctor {
		/** Healing splash at hurt players / villagers within this range of the doctor, once per {@link #splashCooldownTicks}. */
		public int splashRange = 8;
		public int splashCooldownTicks = 1200;
		/** Regeneration amplifier (1 = Regeneration II) for everyone in the hospital ward, refreshed every 2 s. */
		public int wardRegenAmplifier = 1;
		/** Sneak + use a doctor holding emeralds: pay this much for a full heal (clears poison, wither, hunger). */
		public int healCostEmeralds = 2;
	}

	/** Leatherworkers working in the City fire station. */
	public static final class Firefighter {
		/** Fire blocks within this range of the firefighter are put out, one every {@link #ticksPerFire}. */
		public int extinguishRadius = 3;
		public int ticksPerFire = 10;
		/** Fires within this range of the station are answered. */
		public int responseRange = 128;
	}

	/** The City Bank's Safe Deposit (per-player emerald vault). */
	public static final class Bank {
		public boolean interestEnabled = true;
		/** Interest per in-game day, in percent of the vault's emerald value. */
		public double interestPercentPerDay = 1.0;
		/** Total interest one account can ever earn, in emeralds. */
		public int interestCapEmeralds = 64;
	}

	/** The Daily Bulletin newspaper. */
	public static final class Bulletin {
		/** Crimes and world events within this range are reported. */
		public int reportRange = 1024;
		public int priceEmeralds = 1;
	}

	public static CityConfig get() {
		return instance;
	}

	public static Repair repair() {
		return instance.repair;
	}

	public static Cops cops() {
		return instance.cops;
	}

	public static Doctor doctor() {
		return instance.doctor;
	}

	public static Firefighter firefighter() {
		return instance.firefighter;
	}

	public static Bank bank() {
		return instance.bank;
	}

	public static Bulletin bulletin() {
		return instance.bulletin;
	}

	public static CityConfig fresh() {
		return SPEC.fresh();
	}

	public static void load() {
		instance = SPEC.load();
		instance.repair.repairDelayTicks = Math.max(1200, Math.min(72000, instance.repair.repairDelayTicks));
	}

	/** Tests only: run against {@code config} instead of the loaded file. */
	public static void setForTest(CityConfig config) {
		instance = config;
	}
}
