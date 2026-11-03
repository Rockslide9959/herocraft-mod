package com.projecthero.mod.doomsday;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.17.2: Doomsday's tuning, at {@code config/projecthero_doomsday.json} (loaded through {@link VersionedConfig}, see
 * {@code docs/CONFIGS.md}). Read before the entity types are registered: their default attributes come from here.
 * The design (and why every number is what it is) is {@code docs/DOOMSDAY_DESIGN.md}.
 */
public final class DoomsdayConfig {
	public static final VersionedConfig<DoomsdayConfig> SPEC = VersionedConfig.builder(DoomsdayConfig.class, "projecthero_doomsday.json",
			DoomsdayConfig::new)
			.balance("arena", "boss", "adaptation", "revival", "moves", "projector", "rewards", "trigger.chancePerDay")
			.introduce(1, "v0.17.2 Doomsday")
			.reset(2, "v0.17.2 stagger stance + more aggressive (breather 30 -> 20)", "moves.breather")
			.build();

	private static DoomsdayConfig instance = new DoomsdayConfig();

	public Integer configVersion;
	public Trigger trigger = new Trigger();
	public Arena arena = new Arena();
	public Boss boss = new Boss();
	public Adaptation adaptation = new Adaptation();
	public Revival revival = new Revival();
	public Moves moves = new Moves();
	public Projector projector = new Projector();
	public Rewards rewards = new Rewards();

	public static final class Trigger {
		/** The whole event (the natural impact, the Calatonian Casket and the command). */
		public boolean enabled = true;
		/** Once the world has beaten Galactus or the Apokolips Invasion: a nightly roll for a casket impact near a player. */
		public boolean naturalTriggerEnabled = true;
		public double chancePerDay = 0.04;
		public int naturalOffsetMin = 30;
		public int naturalOffsetMax = 50;
	}

	public static final class Arena {
		public double radius = 72.0;
		/** Seconds from the casket landing to him tearing his way out. */
		public int impactSeconds = 8;
		public int borderPoints = 120;
	}

	public static final class Boss {
		/** Health of each life (+ per extra fighter). Three lives. */
		public double health = 3000.0;
		public double healthPerExtraFighter = 750.0;
		/** The enraged 4th life (only if the banishment window is missed). */
		public double enragedHealth = 1500.0;
		public double armor = 12.0;
		public double toughness = 8.0;
		public double speed = 0.3;
		public double baseScale = 2.4;
		public double scalePerLife = 0.25;
		/** Health per second in sunlight / otherwise. */
		public double regenSun = 4.0;
		public double regenDark = 1.0;
		/** Threat multiplier for a Kryptonian-power fighter (he hunts Kryptonians). */
		public double kryptonianThreat = 1.5;
		/** Damage multiplier for kryptonite (a kryptonite shard in the attacker's hand). */
		public double kryptoniteMultiplier = 1.5;
		/**
		 * v0.17.2 (user: "i want to nerf doomsday a bit, make him slightly more aggressive but also give him a staggered stance where he takes 300% more damage, make the staggered stance last 15 seconds please"): once per life, when he drops to this fraction of it, he staggers -- no moves, no regeneration, no
		 * adapting -- and takes this many times the damage (4 = +300%) for this long.
		 */
		public double staggerAt = 0.5;
		public int staggerSeconds = 15;
		public double staggerDamageMultiplier = 4.0;
	}

	public static final class Adaptation {
		/** A damage bucket that has dealt this fraction of the current life's health earns one resistance step. */
		public double learnFraction = 0.20;
		public double stepResistance = 0.25;
		public double maxResistance = 0.75;
		/** At most this many buckets at the max resistance or immune at once (the oldest learned one decays). */
		public int maxHardened = 4;
	}

	public static final class Revival {
		public int daySeconds = 20;
		public int darkSeconds = 40;
		/** The banishment window after the third death: the revival time x this. */
		public double banishWindowMultiplier = 2.0;
	}

	public static final class Moves {
		public float hammerDamage = 14f;
		public float leapDamage = 26f;
		public double leapRadius = 5.0;
		public double leapMin = 12.0;
		public double leapMax = 60.0;
		public float shatterDamage = 16f;
		public double shatterRange = 40.0;
		public float eruptionDamage = 18f;
		public float spikeLineDamage = 12f;
		public float volleyDamage = 7f;
		public float heatDamage = 6f;
		public double heatRange = 40.0;
		public float slamDamage = 10f;
		public float grabBreakDamage = 60f;
		public double breathRange = 14.0;
		public float blitzDamage = 20f;
		public double blitzLength = 30.0;
		public double roarRadius = 16.0;
		/** Ticks of rest between moves (x0.75 in life 3, x0.6 enraged). */
		public int breather = 20; // v0.17.2: 30 -> 20, slightly more aggressive
	}

	public static final class Projector {
		/** Hold right-click this long on his corpse to banish him. */
		public int useTicks = 60;
		public double range = 12.0;
	}

	public static final class Rewards {
		public boolean firstClearItems = true;
		public double valuablesMultiplier = 1.0;
		public int books = 3;
		public int netheriteMin = 3;
		public int netheriteMax = 5;
		/** Bone Gauntlet: +damage per mob type it has killed, and how many types it remembers. */
		public double gauntletBonusPerType = 1.0;
		public int gauntletMaxTypes = 4;
	}

	public static DoomsdayConfig get() {
		return instance;
	}

	public static Trigger trigger() {
		return instance.trigger;
	}

	public static Arena arena() {
		return instance.arena;
	}

	public static Boss boss() {
		return instance.boss;
	}

	public static Adaptation adaptation() {
		return instance.adaptation;
	}

	public static Revival revival() {
		return instance.revival;
	}

	public static Moves moves() {
		return instance.moves;
	}

	public static Projector projector() {
		return instance.projector;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	/** One life's health for {@code fighters} fighters. */
	public static double lifeHealth(int fighters) {
		return instance.boss.health + instance.boss.healthPerExtraFighter * Math.max(0, fighters - 1);
	}

	public static void load() {
		instance = SPEC.load();
	}
}
