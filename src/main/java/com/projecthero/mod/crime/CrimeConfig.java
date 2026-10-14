package com.projecthero.mod.crime;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.2: street crime's tuning, at {@code config/projecthero_crime.json} (loaded through {@link VersionedConfig}, see
 * {@code docs/CONFIGS.md}). {@code trigger} (how often, how far, which kinds) is the server's; every other section is
 * balance the mod tunes. Times are in real minutes / seconds of play (20 ticks a second).
 */
public final class CrimeConfig {
	public static final VersionedConfig<CrimeConfig> SPEC = VersionedConfig.builder(CrimeConfig.class, "projecthero_crime.json", CrimeConfig::new)
			.balance("mugging", "breakIn", "getaway", "arson", "rewards")
			.introduce(1, "v0.16.2 crime events")
			.build();

	private static CrimeConfig instance = new CrimeConfig();

	public Integer configVersion;
	public Trigger trigger = new Trigger();
	public Mugging mugging = new Mugging();
	public BreakIn breakIn = new BreakIn();
	public Getaway getaway = new Getaway();
	public Arson arson = new Arson();
	public Rewards rewards = new Rewards();

	/** When and where crimes happen on their own. */
	public static final class Trigger {
		/** Natural crimes on/off. The {@code /projecthero crime} commands work either way. */
		public boolean enabled = true;
		/** How often (seconds) every player is checked for a nearby village. */
		public int checkIntervalSeconds = 10;
		/** A player within this many blocks of a village's bell counts as near it. */
		public int villageSearchRadius = 96;
		/** Minutes between crimes in one village: a random time between these two. */
		public double minIntervalMinutes = 10.0;
		public double maxIntervalMinutes = 20.0;
		/** Minutes after a crime starts near a player before another can start near the same player. */
		public double playerCooldownMinutes = 8.0;
		/** Minutes after a player first comes online (or a world loads) before the first crime near them. */
		public double firstCrimeDelayMinutes = 4.0;
		/** Relative odds of each kind. 0 turns a kind off. Arson also needs the mobGriefing gamerule. */
		public int weightMugging = 35;
		public int weightBreakIn = 30;
		public int weightGetaway = 20;
		public int weightArson = 15;
		/** Players within this many blocks of a crime get the alert, the boss bar and the direction hint. */
		public int alertRadius = 96;
		/** Two crimes never run closer together than this. */
		public int minDistanceBetweenCrimes = 96;
		/** With no player within {@link #alertRadius} + 32 for this long, the crooks clear off and the crime ends. */
		public int abandonSeconds = 60;
	}

	/** A villager cornered by 1-3 thugs. */
	public static final class Mugging {
		public int minThugs = 1;
		public int maxThugs = 3;
		/** Hits the victim can take before being knocked out: this, plus {@link #victimHitsPerThug} per thug. */
		public int victimHits = 24;
		public int victimHitsPerThug = 8;
		/** A knocked-out villager can't move for this long, then recovers (never killed). */
		public int knockoutSeconds = 45;
		/** The thugs give up and leave after this long. */
		public int timeLimitSeconds = 180;
	}

	/** 2-4 crooks rob a house chest (or the bell square) and run with a loot sack each. */
	public static final class BreakIn {
		public int minCrooks = 2;
		public int maxCrooks = 4;
		/** How far out the crooks start from what they rob. */
		public int approachDistance = 22;
		/** Seconds of rummaging before they run. */
		public int lootSeconds = 15;
		/** A crook this far from the village bell has escaped. */
		public int escapeRadius = 72;
		/** Movement-speed multiplier while running with the sack. */
		public double fleeSpeed = 0.95;
		public int timeLimitSeconds = 240;
	}

	/** One crook loads up at the bell square, then sprints off while 1-2 gunmen cover him. */
	public static final class Getaway {
		public int minGunmen = 1;
		public int maxGunmen = 2;
		/** Seconds he spends grabbing goods before the run starts. */
		public int lootSeconds = 20;
		/** He has got away this far from the bell. */
		public int escapeRadius = 80;
		/** Movement-speed multiplier on the run (his base speed is a thug's). A sprinting player is a bit quicker. */
		public double runnerSpeed = 1.0;
		public int timeLimitSeconds = 240;
	}

	/** A firebug sets small fires around the village. */
	public static final class Arson {
		/** Seconds between fires. */
		public int fireIntervalSeconds = 8;
		/** He gets away (the crime fails) after lighting this many. */
		public int maxFires = 8;
		/** After he is down, any of his fires still burning this long later go out on their own. */
		public int fireOutGraceSeconds = 30;
		public int timeLimitSeconds = 180;
	}

	/** What stopping a crime pays. Everyone who hurt a crook gets the base; whoever did the most gets the rest. */
	public static final class Rewards {
		public int emeraldsMin = 3;
		public int emeraldsMax = 6;
		public int topHelperBonusEmeralds = 2;
		public int experience = 20;
		/** Hero of the Village (better trades) for the top helper. 0 turns it off. */
		public int heroOfTheVillageSeconds = 180;
		/** Villager gossip added for the top helper / every other helper (MINOR_POSITIVE, villagers near the bell). */
		public int gossipTopHelper = 25;
		public int gossipHelpers = 10;
	}

	public static CrimeConfig get() {
		return instance;
	}

	public static Trigger trigger() {
		return instance.trigger;
	}

	public static Mugging mugging() {
		return instance.mugging;
	}

	public static BreakIn breakIn() {
		return instance.breakIn;
	}

	public static Getaway getaway() {
		return instance.getaway;
	}

	public static Arson arson() {
		return instance.arson;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	public static CrimeConfig fresh() {
		return SPEC.fresh();
	}

	public static void load() {
		instance = SPEC.load();
	}

	/** Tests only: run against {@code config} instead of the loaded file. */
	public static void setForTest(CrimeConfig config) {
		instance = config;
	}
}
