package com.projecthero.mod.ultron.contagion;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.1: <b>The Ultron Contagion</b>'s tuning, at {@code config/projecthero_contagion.json} (VersionedConfig v1, see
 * {@code docs/CONFIGS.md}). The toggles ({@link #enabled}, {@link #terrainCorruption}, {@link Resurgence#enabled},
 * {@link Finale#craterDecal}) are the server's; every number section is balance-owned.
 */
public final class ContagionConfig {
	public static final VersionedConfig<ContagionConfig> SPEC = VersionedConfig.builder(ContagionConfig.class, "projecthero_contagion.json",
			ContagionConfig::new)
			.balance("delayDaysMin", "delayDaysMax", "saturation", "nexus", "assimilation", "hijack", "hunters", "beacon", "purge", "finale.minDistance",
					"finale.maxDistance", "finale.waitTicks", "finale.arriveRadius", "finale.height", "finale.radius", "finale.chargeTicks",
					"finale.engineChargeRemoval", "finale.engineHealth", "finale.fallTicks", "finale.fallDamage", "finale.craterRevertTicks", "omega",
					"rewards", "resurgence.chance", "resurgence.intervalDays", "resurgence.unitHealthPerVersion", "resurgence.extraNexusPerVersion",
					"resurgence.omegaHealthPerVersion", "resurgence.omegaDamagePerVersion", "resurgence.maxVersion", "mindBeam")
			.introduce(1, "v0.16.1 Ultron Contagion")
			.reset(2, "v0.16.7 perf: revert 300 -> 48 blocks a tick (300 forced chunk rebuilds a tick stuttered)", "nexus.revertPerTick")
			.build();

	private static ContagionConfig instance = new ContagionConfig();

	public Integer configVersion;
	/** The whole Contagion. Never runs on Peaceful either way. */
	public boolean enabled = true;
	/** In-game days between Ultron's first defeat and the Contagion (picked at random in this range). */
	public int delayDaysMin = 3;
	public int delayDaysMax = 6;
	/** Off = the Nexus spires only spread red particles: no block is ever changed. */
	public boolean terrainCorruption = true;

	public Saturation saturation = new Saturation();
	public Nexus nexus = new Nexus();
	public Assimilation assimilation = new Assimilation();
	public Hijack hijack = new Hijack();
	public Hunters hunters = new Hunters();
	public Beacon beacon = new Beacon();
	public Purge purge = new Purge();
	public Finale finale = new Finale();
	public Omega omega = new Omega();
	public Rewards rewards = new Rewards();
	public Resurgence resurgence = new Resurgence();
	public MindBeam mindBeam = new MindBeam();

	public static final class Saturation {
		/** Percent per active (loaded) Nexus per {@link #riseIntervalTicks}. */
		public double risePerNexus = 1.0;
		public int riseIntervalTicks = 2400;
		/** Percent off when a Nexus falls. */
		public double purgeDrop = 5.0;
		/** Percent off per fuelled Firewall Beacon per {@link #beaconIntervalTicks}, while the Saturation is under {@link #beaconCeiling}. */
		public double beaconDrop = 1.0;
		public int beaconIntervalTicks = 2400;
		public double beaconCeiling = 90.0;
		/** Thresholds (percent). */
		public double fastSpawnAt = 25.0;
		public double golemsAt = 50.0;
		public double huntersAt = 75.0;
		/** What the Saturation drops back to after a lost or escaped finale. */
		public double resetTo = 50.0;
	}

	public static final class Nexus {
		public int maxNexus = 4;
		public int spawnIntervalTicks = 30000;
		public int fastSpawnIntervalTicks = 18000;
		public double minDistance = 64.0;
		public double maxDistance = 160.0;
		public double health = 800.0;
		public double armor = 8.0;
		/** Ticks the spire takes to grow out of the ground (the red beam shows meanwhile). */
		public int growTicks = 2400;
		public int maxRadius = 32;
		/** Ticks per +1 block of corruption radius. */
		public int radiusGrowTicks = 200;
		/** Corruption columns examined per Nexus per tick, and the cap on blocks changed per tick (all Nexus spires together). */
		public int columnsPerTick = 6;
		public int blocksPerTick = 24;
		/** Recorded blocks put back per tick while reverting. */
		public int revertPerTick = 48; // v0.16.7 perf: was 300
		/** Blocks kept clear of any block entity (chests, furnaces, beds...): a base is never corrupted. */
		public int blockEntityClearance = 4;
		/** The Override capture. */
		public int captureTicks = 1200;
		public double captureRadius = 10.0;
		/** Two or more players inside: the timer runs this many times as fast. */
		public double groupSpeed = 2.0;
		/** A Mind Stone in the off hand inside the circle: this much faster. */
		public double mindStoneBonus = 0.4;
		public int waveIntervalTicks = 300;
		public int waveCap = 14;
		public double echoHealth = 600.0;
		public int scrapMin = 3;
		public int scrapMax = 6;
	}

	public static final class Assimilation {
		public double armor = 4.0;
		public float boltDamage = 4.0f;
		public double boltRange = 16.0;
		public int boltCooldownTicks = 60;
		/** Extra max health (fraction) for Assimilated mobs once the Saturation passes {@link Saturation#golemsAt}. */
		public double saturationHealthBonus = 0.25;
		public int golemConvertTicks = 200;
		public double golemHealth = 150.0;
		public float golemSlamDamage = 10.0f;
		public int golemSlamCooldownTicks = 120;
		public int creeperFuse = 15;
		public double empRadius = 5.0;
		/** v0.16.1 (Iron Man suit energy is gone): a Proximity Mine's EMP locks a worn suit out for this many ticks
		 *  (no flight, no abilities). Replaces empEnergyDrain. */
		public int empLockoutTicks = 60;
		public int machineDroneIntervalTicks = 1200;
		public int machineMaxDrones = 3;
		public double scrapChance = 0.2;
		/** Ticks between the slow sweep that assimilates mobs already standing in the corruption. */
		public int sweepTicks = 40;
	}

	public static final class Hijack {
		public int attemptTicks = 80;
		public int breakPresses = 6;
		public int lockTicks = 100;
		public int glitchMinTicks = 600;
		public int glitchMaxTicks = 1200;
	}

	public static final class Hunters {
		public int intervalTicks = 12000;
		public int drones = 3;
		public int heavies = 1;
	}

	public static final class Beacon {
		public double radius = 48.0;
		/** One redstone block runs the beacon this long (one in-game day). */
		public int ticksPerRedstoneBlock = 24000;
		public int maxFuelBlocks = 64;
	}

	public static final class Purge {
		public double radius = 6.0;
		public int stunTicks = 60;
	}

	public static final class Finale {
		public double minDistance = 300.0;
		public double maxDistance = 600.0;
		/** The marked site starts on its own after this long, or as soon as a player is within {@link #arriveRadius}. */
		public int waitTicks = 24000;
		public double arriveRadius = 64.0;
		/** Blocks above the ground. */
		public int height = 90;
		public int radius = 25;
		/** The Vibranium Core's 0 -> 100% charge. */
		public int chargeTicks = 14400;
		public double engineChargeRemoval = 0.15;
		public double engineHealth = 500.0;
		public int fallTicks = 40;
		public float fallDamage = 6.0f;
		public int craterRevertTicks = 6000;
		/** Off = the crash leaves particles only (no decal blocks at all). */
		public boolean craterDecal = true;
	}

	public static final class Omega {
		public double health = 4000.0;
		public double healthPerExtraFighter = 900.0;
		public double maxHealth = 12000.0;
		public double armor = 16.0;
		public double armorToughness = 10.0;
		public float[] strikeDamage = { 8f, 8f, 14f };
		public float blastDamage = 12.0f;
		public double blastRange = 10.0;
		public float latticeDamage = 4.0f;
		public double latticeLength = 22.0;
		public int attackCooldownTicks = 50;
		public int siphonIntervalTicks = 1200;
		public double siphonCharge = 0.05;
		public double siphonInterruptFraction = 0.08;
		/** Legion: decoys beside the real one. */
		public int decoys = 3;
		public double decoyDamageFraction = 0.5;
		public double decoyHealFraction = 0.005;
		public double legionCollapseFraction = 0.10;
		/** Iron Legion. */
		public int legionBase = 3;
		public double legionHealth = 150.0;
		public float repulsorDamage = 5.0f;
		public float unibeamDamage = 14.0f;
		public int unibeamTelegraphTicks = 40;
		public int legionShieldAt = 3;
		/** Upload. */
		public double antennaHealth = 300.0;
		public double antennaHealthPerExtraFighter = 100.0;
		public int uploadTicks = 700;
		public double strandedMultiplier = 1.5;
		/** The Vision moment. */
		public int visionBeamTicks = 60;
		public int selfDestructTicks = 200;
		public int selfDestructTelegraphTicks = 60;
		public double selfDestructRadius = 6.0;
		public float selfDestructDamage = 20.0f;
	}

	public static final class Rewards {
		public int ingotBase = 2;
		public int netheriteMin = 2;
		public int netheriteMax = 4;
		public int scrapMin = 6;
		public int scrapMax = 12;
		public double valuablesMultiplier = 1.0;
		public boolean empoweredMindStone = true;
		public boolean firewallProtocol = true;
	}

	public static final class Resurgence {
		public boolean enabled = true;
		public double chance = 0.15;
		public int intervalDays = 20;
		public double unitHealthPerVersion = 0.20;
		public int extraNexusPerVersion = 1;
		public double omegaHealthPerVersion = 0.10;
		public double omegaDamagePerVersion = 0.10;
		public int maxVersion = 5;
	}

	public static final class MindBeam {
		/** The Empowered Mind Stone's own beam: Sneak + hold right-click. */
		public int beamTicks = 40;
		public float damage = 6.0f;
		public int damageInterval = 5;
		public int cooldownTicks = 1200;
		public double range = 32.0;
	}

	public static ContagionConfig get() {
		return instance;
	}

	public static Saturation saturation() {
		return instance.saturation;
	}

	public static Nexus nexus() {
		return instance.nexus;
	}

	public static Assimilation assimilation() {
		return instance.assimilation;
	}

	public static Hijack hijack() {
		return instance.hijack;
	}

	public static Hunters hunters() {
		return instance.hunters;
	}

	public static Beacon beacon() {
		return instance.beacon;
	}

	public static Purge purge() {
		return instance.purge;
	}

	public static Finale finale() {
		return instance.finale;
	}

	public static Omega omega() {
		return instance.omega;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	public static Resurgence resurgence() {
		return instance.resurgence;
	}

	public static MindBeam mindBeam() {
		return instance.mindBeam;
	}

	/** Contagion unit health multiplier for Ultron {@code version} (v1 = 1.0). */
	public static double unitHealthScale(int version) {
		return 1.0 + instance.resurgence.unitHealthPerVersion * Math.max(0, Math.min(version, instance.resurgence.maxVersion) - 1);
	}

	/** Ultron Omega's health for {@code fighters} fighters at Ultron {@code version}. */
	public static double omegaHealthFor(int fighters, int version) {
		Omega o = instance.omega;
		double base = Math.min(o.maxHealth, o.health + o.healthPerExtraFighter * Math.max(0, fighters - 1));
		return base * (1.0 + instance.resurgence.omegaHealthPerVersion * Math.max(0, Math.min(version, instance.resurgence.maxVersion) - 1));
	}

	/** Ultron Omega's damage multiplier at {@code version}. */
	public static float omegaDamageScale(int version) {
		return (float) (1.0 + instance.resurgence.omegaDamagePerVersion * Math.max(0, Math.min(version, instance.resurgence.maxVersion) - 1));
	}

	/** Max Nexus spires at {@code version}. */
	public static int maxNexusFor(int version) {
		return instance.nexus.maxNexus + instance.resurgence.extraNexusPerVersion * Math.max(0, Math.min(version, instance.resurgence.maxVersion) - 1);
	}

	public static void load() {
		instance = SPEC.load();
	}

	/** Test hook: put today's defaults back without touching the file. */
	public static void resetForTest() {
		instance = new ContagionConfig();
	}
}
