package com.projecthero.mod.ultron;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.15.12: the Ultron Uprising's tuning, at {@code config/projecthero_ultron.json} (loaded through {@link VersionedConfig},
 * see {@code docs/CONFIGS.md}). Read before the entity types are registered: their default attributes come from here.
 *
 * <p>Everything the raid does by the numbers lives here -- the arena and the relay pylons, the seven waves, every robot's
 * health and damage, Ultron's two bodies, the damage multipliers, the rewards -- plus the one server toggle: the
 * natural JARVIS trigger, off by default.
 *
 * <p>v0.16.1 (config v2): the harder Uprising -- 7 waves, 4 pylons on a 24-block ring with guard shields, tougher units,
 * the Lancer and the Hijacker, bigger bosses with new moves, and every Ultron hit x1.3. The v2 step rewrites every
 * changed key on an old file (the stale-config trap); there is no damage adaptation anywhere.
 */
public final class UltronConfig {
	public static final VersionedConfig<UltronConfig> SPEC = VersionedConfig.builder(UltronConfig.class, "projecthero_ultron.json", UltronConfig::new)
			.balance("arena", "waves", "drone", "sentinelDrone", "heavy", "sniper", "lancer", "hijacker", "prime", "sentry", "damage",
					"rewards", "trigger.dailyChance", "trigger.minimumMarks")
			.introduce(1, "v0.15.12 Ultron Uprising")
			.reset(2, "v0.16.1 harder Ultron Uprising (7 waves, 4 shielded pylons, tougher units and bosses, x1.3 damage)",
					"arena.pylons", "arena.pylonRing", "arena.pylonHealth", "arena.pylonHealthPerExtraFighter", "arena.repairPerSecond",
					"arena.reservePylonHealthFraction",
					"waves.waveCount", "waves.enemyCap", "waves.drones", "waves.sentinelDrones", "waves.heavies", "waves.snipers",
					"drone.health", "drone.boltDamage",
					"sentinelDrone.health",
					"heavy.health", "heavy.barrageMissiles",
					"sniper.shotDamage",
					"prime.health", "prime.healthPerExtraFighter", "prime.maxHealth",
					"sentry.health", "sentry.healthPerExtraFighter", "sentry.maxHealth", "sentry.shieldDrones",
					"damage.globalMultiplier")
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
	public Lancer lancer = new Lancer();
	public Hijacker hijacker = new Hijacker();
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
		public double pylonRing = 24.0;
		public int pylons = 4;
		public double pylonHealth = 450.0;
		public double pylonHealthPerExtraFighter = 150.0;
		/** Ultron units within this many blocks of a standing pylon repair themselves. */
		public double repairRadius = 16.0;
		public double repairPerSecond = 3.0;
		/** A destroyed pylon's EMP stuns robots within this radius for empSeconds. */
		public double empRadius = 12.0;
		public int empSeconds = 3;
		/** Pylons Ultron Prime re-raises at the relay sites when he arrives (spare bodies for the body jump). */
		public int reservePylons = 2;
		/** A reserve pylon's health, as a fraction of a relay pylon's (2/3 of 450 = 300 solo). */
		public double reservePylonHealthFraction = 2.0 / 3.0;
		/** v0.16.1: a pylon is invulnerable (a red shell) while this many Ultron units stand within pylonShieldRadius of it. */
		public int pylonShieldGuards = 3;
		public double pylonShieldRadius = 8.0;
		/** v0.16.1: Prime's reserve pylons are shielded while his health is above this fraction. */
		public double reserveShieldAbove = 0.5;
	}

	public static final class Waves {
		public int waveCount = 7;
		/** Each extra fighter adds this fraction of every wave entry. */
		public double perExtraFighter = 0.5;
		/** Live Ultron units at once (waves wait for room). */
		public int enemyCap = 36;
		public int countdownSeconds = 6;
		public int breatherSeconds = 8;
		/** Solo composition per wave (index 0 = wave 1). */
		public int[] drones = { 6, 8, 6, 6, 6, 8, 8 };
		public int[] sentinelDrones = { 0, 2, 4, 4, 4, 5, 6 };
		public int[] heavies = { 0, 0, 1, 1, 2, 2, 3 };
		public int[] snipers = { 0, 0, 0, 2, 2, 2, 3 };
		public int[] lancers = { 0, 0, 0, 1, 1, 2, 2 };
		public int[] hijackers = { 0, 0, 0, 0, 1, 1, 2 };
	}

	public static final class Drone {
		public double health = 40.0;
		public double armor = 2.0;
		public float boltDamage = 5.0f;
		public int boltCooldownTicks = 40;
		public double boltRange = 32.0;
	}

	public static final class SentinelDrone {
		public double health = 60.0;
		public double armor = 4.0;
		public double clawDamage = 5.0;
		public double speed = 0.34;
		public float explosionDamage = 6.0f;
		public double explosionRadius = 3.5;
	}

	public static final class Heavy {
		public double health = 180.0;
		public double armor = 10.0;
		public double meleeDamage = 9.0;
		public double speed = 0.2;
		public float rocketDamage = 6.0f;
		public float rocketSplash = 3.0f;
		/** Missiles per barrage. */
		public int barrageMissiles = 4;
		public int barrageCooldownTicks = 140;
		public float stompDamage = 8.0f;
		public double stompRadius = 4.0;
	}

	public static final class Sniper {
		public double health = 40.0;
		public double armor = 2.0;
		public float shotDamage = 16.0f;
		/** Laser-sight time before the shot (ticks): break line of sight to dodge. */
		public int aimTicks = 30;
		public int cooldownTicks = 50;
		public double range = 56.0;
		/** v0.16.1: after each shot a perched sniper jumps to another free pylon. */
		public boolean relocateAfterShot = true;
	}

	/** v0.16.1: the Ultron Lancer -- a front energy shield and an energy lance. */
	public static final class Lancer {
		public double health = 90.0;
		public double armor = 8.0;
		public double speed = 0.25;
		public float lanceDamage = 10.0f;
		public double lanceReach = 4.0;
		/** Wind-up before the lunge (ticks). */
		public int lungeWindupTicks = 10;
		public int lungeCooldownTicks = 50;
		/** The shield drops for this long after each lunge. */
		public int shieldDownTicks = 40;
		/** Half-angle of the shield's cover (degrees either side of straight ahead). */
		public double shieldHalfAngle = 70.0;
	}

	/** v0.16.1: the Ultron Hijacker -- cloaked until close, then latches on and jams a power. */
	public static final class Hijacker {
		public double health = 50.0;
		public double armor = 2.0;
		public double speed = 0.36;
		/** It stays cloaked (a shimmer) until it is this close to its target. */
		public double decloakRange = 6.0;
		/** Longest latch (ticks). Hitting it breaks the latch sooner. */
		public int latchTicks = 80;
		/** How long the jammed ability key stays dead (ticks). */
		public int jamTicks = 120;
		/** v0.16.1 (Iron Man suit energy is gone): fraction of a worn suit's MAX integrity a latch tears off... */
		public double ironManIntegrityDrain = 0.05;
		/** ...and how long the hijacked suit is locked out (no flight, no abilities), in ticks. Replaces ironManEnergyDrain. */
		public int ironManLockoutTicks = 40;
		public int latchCooldownTicks = 100;
	}

	public static final class Prime {
		public double health = 1600.0;
		public double healthPerExtraFighter = 450.0;
		public double maxHealth = 5000.0;
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
		/** v0.16.1 Overclock (below 66%): this long, this much faster, and the barrage fires overclockBarrageBeams. */
		public int overclockTicks = 120;
		public double overclockSpeedMultiplier = 1.5;
		public int overclockBarrageBeams = 8;
		/** v0.16.1 Network Override: the reticle shows this long, then the target's abilities are jammed for overrideJamTicks. */
		public int overrideTelegraphTicks = 20;
		public int overrideJamTicks = 80;
		public double overrideRange = 48.0;
	}

	public static final class Sentry {
		public double health = 2600.0;
		public double healthPerExtraFighter = 600.0;
		public double maxHealth = 7000.0;
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
		/** v0.16.1: the beam turns toward its target at this many degrees a second while it fires. */
		public double cannonTurnDegreesPerSecond = 20.0;
		public int missileCount = 8;
		public float missileDamage = 7.0f;
		public float missileSplash = 4.0f;
		public int shieldDrones = 4;
		/** v0.16.1: the shield comes back once more at this health fraction (with shieldDrones new carriers). */
		public double reshieldAt = 0.25;
		public int attackCooldownTicks = 50;
		/** v0.16.1 Fist Rocket: a homing forearm, shootable. */
		public float fistRocketDamage = 18.0f;
		public int fistRocketFlightTicks = 40;
		public double fistRocketHealth = 60.0;
		/** v0.16.1 Reconstruct (at 50% and 25%): a channel during which every drone in the arena flies into him. */
		public int reconstructTicks = 120;
		/** Max health healed per drone absorbed (0.02 = 2%). */
		public double reconstructHealPerDrone = 0.02;
		/** Drones he calls in from the arena's edge as the channel starts (on top of any already flying). */
		public int reconstructCallDrones = 4;
	}

	public static final class Damage {
		/** Lightning / electricity (Thor, Electrokinesis) against any Ultron robot. */
		public double electricMultiplier = 1.5;
		/** Hulk, or any direct melee hit of {@link #heavyMeleeThreshold}+ damage. */
		public double heavyMeleeMultiplier = 1.25;
		public double heavyMeleeThreshold = 12.0;
		/** Ultron's hits wear an Iron Man suit's integrity this much harder ("he's in your systems"). */
		public double ironManDrainBonus = 0.10;
		/** v0.16.1: every hit an Ultron robot (or its missile / rocket) deals is multiplied by this. */
		public double globalMultiplier = 1.3;
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

	public static Lancer lancer() {
		return instance.lancer;
	}

	public static Hijacker hijacker() {
		return instance.hijacker;
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
