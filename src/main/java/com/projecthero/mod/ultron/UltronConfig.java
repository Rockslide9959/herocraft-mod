package com.projecthero.mod.ultron;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.15.12: the Ultron Uprising's tuning, at {@code config/projecthero_ultron.json} (loaded through {@link VersionedConfig},
 * see {@code docs/CONFIGS.md}). Read before the entity types are registered: their default attributes come from here.
 *
 * <p>Everything the raid does by the numbers lives here -- the arena and the relay pylons, the five waves, every robot's
 * health and damage, Ultron's three bodies, the damage multipliers, the rewards -- plus the one server toggle: the
 * natural JARVIS trigger, off by default.
 */
public final class UltronConfig {
	public static final VersionedConfig<UltronConfig> SPEC = VersionedConfig.builder(UltronConfig.class, "projecthero_ultron.json", UltronConfig::new)
			.balance("arena", "waves", "drone", "sentinelDrone", "heavy", "sniper", "prime", "sentry", "damage", "rewards",
					"trigger.dailyChance", "trigger.minimumMarks")
			.introduce(1, "v0.15.12 Ultron Uprising")
			.build();

	private static UltronConfig instance = new UltronConfig();

	public Integer configVersion;
	public Trigger trigger = new Trigger();
	public Arena arena = new Arena();
	public Waves waves = new Waves();
	public Drone drone = new Drone();
	public SentinelDrone sentinelDrone = new SentinelDrone();
	public Heavy heavy = new Heavy();
	public Sniper sniper = new Sniper();
	public Prime prime = new Prime();
	public Sentry sentry = new Sentry();
	public Damage damage = new Damage();
	public Rewards rewards = new Rewards();

	public static final class Trigger {
		/**
		 * The natural trigger: a Tony Stark who has built {@link #minimumMarks} Iron Man marks (and so owns a Stark
		 * Fabricator) has a small daily chance of JARVIS flagging "an unauthorised process in the Fabricator network" --
		 * and an Ultron Beacon turns up in their inventory. Off by default.
		 */
		public boolean naturalTriggerEnabled = false;
		/** Chance per in-game day (rolled once a minute as dailyChance / 20). */
		public double dailyChance = 0.05;
		public int minimumMarks = 3;
	}

	public static final class Arena {
		/** The border ring (blocks from the uplink). Fighters are kept inside; anyone who walks in joins. */
		public double radius = 40.0;
		/** The relay pylons rise on a ring this far out. */
		public double pylonRing = 22.0;
		public int pylons = 3;
		public double pylonHealth = 300.0;
		public double pylonHealthPerExtraFighter = 100.0;
		/** Ultron units within this many blocks of a standing pylon repair themselves. */
		public double repairRadius = 16.0;
		public double repairPerSecond = 2.0;
		/** A destroyed pylon's EMP stuns robots within this radius for empSeconds. */
		public double empRadius = 12.0;
		public int empSeconds = 3;
		/** Pylons Ultron Prime re-raises at the relay sites when he arrives (spare bodies for the body jump). */
		public int reservePylons = 2;
		/** A reserve pylon's health, as a fraction of a relay pylon's. */
		public double reservePylonHealthFraction = 0.5;
	}

	public static final class Waves {
		public int waveCount = 5;
		/** Each extra fighter adds this fraction of every wave entry. */
		public double perExtraFighter = 0.5;
		/** Live Ultron units at once (waves wait for room). */
		public int enemyCap = 30;
		public int countdownSeconds = 6;
		public int breatherSeconds = 8;
		/** Solo composition per wave (index 0 = wave 1). */
		public int[] drones = { 5, 7, 5, 5, 6 };
		public int[] sentinelDrones = { 0, 0, 4, 3, 4 };
		public int[] heavies = { 0, 0, 0, 1, 2 };
		public int[] snipers = { 0, 0, 0, 2, 2 };
	}

	public static final class Drone {
		public double health = 30.0;
		public double armor = 2.0;
		public float boltDamage = 4.0f;
		public int boltCooldownTicks = 40;
		public double boltRange = 32.0;
	}

	public static final class SentinelDrone {
		public double health = 45.0;
		public double armor = 4.0;
		public double clawDamage = 5.0;
		public double speed = 0.34;
		public float explosionDamage = 6.0f;
		public double explosionRadius = 3.5;
	}

	public static final class Heavy {
		public double health = 120.0;
		public double armor = 10.0;
		public double meleeDamage = 9.0;
		public double speed = 0.2;
		public float rocketDamage = 6.0f;
		public float rocketSplash = 3.0f;
		public int barrageCooldownTicks = 140;
		public float stompDamage = 8.0f;
		public double stompRadius = 4.0;
	}

	public static final class Sniper {
		public double health = 40.0;
		public double armor = 2.0;
		public float shotDamage = 14.0f;
		/** Laser-sight time before the shot (ticks): break line of sight to dodge. */
		public int aimTicks = 30;
		public int cooldownTicks = 50;
		public double range = 56.0;
	}

	public static final class Prime {
		public double health = 900.0;
		public double healthPerExtraFighter = 300.0;
		public double maxHealth = 3000.0;
		public double armor = 12.0;
		public double armorToughness = 6.0;
		public double meleeDamage = 10.0;
		public float barrageDamage = 5.0f;
		public int barrageBeams = 5;
		/** Encephalo-beam damage per second while it sweeps across you. */
		public float encephaloDamagePerSecond = 12.0f;
		public int encephaloTelegraphTicks = 20;
		public int encephaloSweepTicks = 50;
		public int swarmBaseDrones = 4;
		public float pullPunchDamage = 10.0f;
		public double pullRange = 24.0;
		public float diveDamage = 14.0f;
		public double diveRadius = 6.0;
		public int attackCooldownTicks = 50;
		/** Health a new body starts with after a body jump (fraction of max). */
		public double bodyJumpHealth = 0.5;
	}

	public static final class Sentry {
		public double health = 1500.0;
		public double healthPerExtraFighter = 400.0;
		public double maxHealth = 4000.0;
		public double armor = 14.0;
		public double armorToughness = 8.0;
		public double speed = 0.2;
		public float slamDamage = 16.0f;
		public double slamRadius = 4.5;
		/** Chest cannon damage per hit (it hits every 5 ticks while firing). */
		public float cannonDamage = 6.0f;
		public int cannonChargeTicks = 40;
		public int cannonFireTicks = 25;
		public double cannonRange = 40.0;
		public int missileCount = 8;
		public float missileDamage = 7.0f;
		public float missileSplash = 4.0f;
		public int shieldDrones = 3;
		public int attackCooldownTicks = 50;
	}

	public static final class Damage {
		/** Lightning / electricity (Thor, Electrokinesis) against any Ultron robot. */
		public double electricMultiplier = 1.5;
		/** Hulk, or any direct melee hit of {@link #heavyMeleeThreshold}+ damage. */
		public double heavyMeleeMultiplier = 1.25;
		public double heavyMeleeThreshold = 12.0;
		/** Ultron's hits wear an Iron Man suit's integrity this much harder ("he's in your systems"). */
		public double ironManDrainBonus = 0.10;
	}

	public static final class Rewards {
		/** Vibranium Plating = plateBase + fighters. */
		public int plateBase = 2;
		public int netheriteMin = 1;
		public int netheriteMax = 3;
		public int experienceBottlesMin = 12;
		public int experienceBottlesMax = 20;
		/** A Mind Stone for every fighter clearing Ultron for the first time. */
		public boolean mindStoneOnFirstClear = true;
		/** Scales the ores and gems; 0 gives none. */
		public double valuablesMultiplier = 1.0;
	}

	public static UltronConfig get() {
		return instance;
	}

	public static Trigger trigger() {
		return instance.trigger;
	}

	public static Arena arena() {
		return instance.arena;
	}

	public static Waves waves() {
		return instance.waves;
	}

	public static Drone drone() {
		return instance.drone;
	}

	public static SentinelDrone sentinelDrone() {
		return instance.sentinelDrone;
	}

	public static Heavy heavy() {
		return instance.heavy;
	}

	public static Sniper sniper() {
		return instance.sniper;
	}

	public static Prime prime() {
		return instance.prime;
	}

	public static Sentry sentry() {
		return instance.sentry;
	}

	public static Damage damage() {
		return instance.damage;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	/** Ultron Prime's health for {@code fighters} fighters: the base plus a share per extra fighter, capped. */
	public static double primeHealthFor(int fighters) {
		Prime p = instance.prime;
		return Math.min(p.maxHealth, p.health + p.healthPerExtraFighter * Math.max(0, fighters - 1));
	}

	/** The Ultron Sentry's health for {@code fighters} fighters. */
	public static double sentryHealthFor(int fighters) {
		Sentry s = instance.sentry;
		return Math.min(s.maxHealth, s.health + s.healthPerExtraFighter * Math.max(0, fighters - 1));
	}

	/** A relay pylon's health for {@code fighters} fighters. */
	public static double pylonHealthFor(int fighters) {
		Arena a = instance.arena;
		return a.pylonHealth + a.pylonHealthPerExtraFighter * Math.max(0, fighters - 1);
	}

	public static void load() {
		instance = SPEC.load();
	}
}
