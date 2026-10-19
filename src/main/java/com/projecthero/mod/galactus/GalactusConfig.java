package com.projecthero.mod.galactus;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.1: Galactus: The Devourer's tuning, at {@code config/projecthero_galactus.json} (loaded through
 * {@link VersionedConfig}, see {@code docs/CONFIGS.md}). Read before the entity types are registered: their default
 * attributes come from here. Every number the event uses lives here.
 */
public final class GalactusConfig {
	public static final VersionedConfig<GalactusConfig> SPEC = VersionedConfig.builder(GalactusConfig.class, "projecthero_galactus.json",
			GalactusConfig::new)
			.balance("arena", "herald", "ally", "converters", "punisher", "consumption", "galactus", "eyeBeam", "grab", "transmute", "wave",
					"summons", "pylons", "weakPoints", "nullifier", "rewards", "board", "powerCosmic", "trigger.chancePerDay")
			.introduce(1, "v0.16.1 Galactus: The Devourer")
			.reset(2, "v0.16.4 Galactus takes full damage on his body once the pylons are down, 4000 health (+1000 a fighter)", "galactus.bodyDamageMultiplier",
					"galactus.health", "galactus.healthPerExtraFighter")
			.reset(3, "v0.16.7 perf: revert 160 -> 64 blocks a tick", "converters.revertPerTick")
			.build();

	private static GalactusConfig instance = new GalactusConfig();

	public Integer configVersion;
	public Trigger trigger = new Trigger();
	public Arena arena = new Arena();
	public Herald herald = new Herald();
	public Ally ally = new Ally();
	public Converters converters = new Converters();
	public Punisher punisher = new Punisher();
	public Consumption consumption = new Consumption();
	public Colossus galactus = new Colossus();
	public EyeBeam eyeBeam = new EyeBeam();
	public Grab grab = new Grab();
	public Transmute transmute = new Transmute();
	public Wave wave = new Wave();
	public Summons summons = new Summons();
	public Pylons pylons = new Pylons();
	public WeakPoints weakPoints = new WeakPoints();
	public Nullifier nullifier = new Nullifier();
	public Rewards rewards = new Rewards();
	public Board board = new Board();
	public PowerCosmic powerCosmic = new PowerCosmic();

	public static final class Trigger {
		/** The whole event (natural signal, Herald's Beacon and the command). On by default. */
		public boolean enabled = true;
		/**
		 * The cosmic signal: once the world has beaten the Ultron Contagion finale or a Frost Giant Assault, once per in-game
		 * day this chance starts Galactus near a random online player (never on Peaceful, never while one is running).
		 */
		public boolean naturalTriggerEnabled = true;
		public double chancePerDay = 0.05;
		/** The signal centres the arena this far (blocks) from the chosen player. */
		public int naturalOffsetMin = 12;
		public int naturalOffsetMax = 28;
		/** The Herald's Beacon only answers at night. */
		public boolean beaconNightOnly = true;
	}

	public static final class Arena {
		/** The border ring (blocks from the centre). Fighters are kept inside; anyone who walks in joins. */
		public double radius = 80.0;
		/** Seconds of the arrival (the sky turns, the Herald descends) before the duel. */
		public int arrivalSeconds = 10;
		/** Height of the Worldship above the centre (render-only). */
		public double worldshipHeight = 110.0;
		/** Particles per framework tick on the border (cap). */
		public int borderPoints = 128;
		/** The terrain-damage switch: false = converters and Matter Transmutation are particles only. */
		public boolean terrainDamage = true;
	}

	public static final class Herald {
		public double health = 600.0;
		public double healthPerExtraFighter = 150.0;
		public double armor = 8.0;
		public double flySpeed = 0.55;
		/** He turns (stops fighting, becomes your ally) at this fraction of his health. */
		public double turnAtFraction = 0.10;
		public int moveCooldownTicks = 40;
		public float blastDamage = 9.0f;
		public double blastSpeed = 1.4;
		public double blastRange = 40.0;
		public float dashDamage = 14.0f;
		public double dashLength = 28.0;
		public int dashTicks = 6;
		/** v0.16.4: after a dash he sinks to the ground, winded, for this long (the melee window). */
		public int windedTicks = 70;
		/** Ticks the reflective shield lasts; projectile damage is returned, melee reduced. */
		public int shieldTicks = 60;
		public double shieldMeleeReduction = 0.7;
		public double shieldReflectFraction = 0.5;
		/** Cosmic Lift: Levitation of this amplifier for this long (then the fall). */
		public int liftAmplifier = 9;
		public int liftTicks = 24;
		public double preferredRange = 14.0;
		public double hoverHeight = 7.0;
		/** Ticks the turn (dialogue) takes before he fights at your side. */
		public int turnTicks = 120;
	}

	public static final class Ally {
		/** Acts 2-3: he blasts the nearest Punisher every this many ticks. */
		public int blastIntervalTicks = 40;
		public float blastDamage = 16.0f;
		/** And bubbles a fighter under {@link #shieldBelowHealth} of their health (Resistance + Absorption), on this cooldown. */
		public int shieldCooldownTicks = 600;
		public double shieldBelowHealth = 0.3;
		public int shieldTicks = 100;
		public double orbitRadius = 6.0;
	}

	public static final class Converters {
		/** Three Element Converters, +1 per two extra fighters, at most five. */
		public int base = 3;
		public int perTwoExtraFighters = 1;
		public int max = 5;
		public double ring = 46.0;
		public double health = 400.0;
		public double healthPerExtraFighter = 100.0;
		public double armor = 10.0;
		/** Ticks a converter takes to crash down from the Worldship. */
		public int fallTicks = 40;
		/** Punishers guarding each one (they are shielded while any guard lives). */
		public int guards = 2;
		/** The consumed ground grows to this radius round each converter. */
		public int maxRadius = 16;
		/** Its radius grows one block every this many ticks. */
		public int growTicks = 30;
		/** Columns a converter visits per server tick, and the most blocks all of them change per tick together. */
		public int columnsPerTick = 6;
		public int blocksPerTick = 48;
		/** Blocks put back per server tick while reverting (the "un-consuming" wave). */
		public int revertPerTick = 64; // v0.16.7 perf: was 160
		/** Nothing within this many blocks of a block entity (chests, furnaces, beds) is ever consumed. */
		public int blockEntityClearance = 3;
		/** Ground blocks deep a column is consumed. */
		public int depth = 2;
	}

	public static final class Punisher {
		public double health = 220.0;
		public double healthPerExtraFighter = 40.0;
		public double armor = 10.0;
		public double speed = 0.24;
		public float punchDamage = 12.0f;
		public float blastDamage = 8.0f;
		public double blastRange = 28.0;
		public int blastCooldownTicks = 70;
		public float slamDamage = 14.0f;
		public double slamRadius = 5.5;
		public int slamCooldownTicks = 120;
		/** Guards stay within this many blocks of their converter unless chasing a fighter right next to it. */
		public double leash = 18.0;
	}

	public static final class Consumption {
		/** Percent per minute each landed, living converter adds to the world-consumption bar. */
		public double perConverterPerMinute = 5.0;
		/** Act 3: percent per minute each standing Power Pylon adds. */
		public double perPylonPerMinute = 1.5;
		/** The loss cinematic (ticks) before the world is put back and the event ends. */
		public int lossCinematicTicks = 140;
	}

	public static final class Colossus {
		public double health = 4000.0;
		public double healthPerExtraFighter = 1000.0;
		/** Damage to his body (not a weak point) is multiplied by this once the Power Pylons are down. */
		public double bodyDamageMultiplier = 1.0;
		/** v0.16.4: blocks a tick he walks (he wanders slowly round the arena's edge between moves). 0 = he stands still. */
		public double walkSpeed = 0.06;
		/** Each walk is this many blocks, then he stops for a while. */
		public double walkLegMin = 4.0;
		public double walkLegMax = 9.0;
		public int walkPauseMinTicks = 80;
		public int walkPauseMaxTicks = 200;
		/** He keeps between these distances from the arena's centre (clear of the Power Pylons, inside the border). */
		public double walkInnerDistance = 52.0;
		public double walkOuterDistance = 74.0;
		/** The model's render height (blocks); his hit-box is smaller (render scale only). */
		public double renderHeight = 60.0;
		/** He stands this far from the centre, on the arena's edge. */
		public double standDistance = 70.0;
		public int moveCooldownTicks = 70;
		/** The Nullifier moment: every weak point broken, or his health at or under this fraction. */
		public double nullifierAtFraction = 0.2;
		/** Regeneration per second for each standing Power Pylon. */
		public double regenPerPylonPerSecond = 6.0;
		/** Range of his attention (the threat table). */
		public double range = 140.0;
	}

	public static final class EyeBeam {
		public int telegraphTicks = 40;
		public float damage = 16.0f;
		public double width = 2.5;
		public double length = 44.0;
		public int setFireSeconds = 4;
	}

	public static final class Grab {
		/** The hand's shadow grows for this long; anyone still under it when it closes is grabbed. */
		public double radius = 3.5;
		public double gripHealth = 80.0;
		public double gripHealthPerExtraFighter = 30.0;
		public int liftTicks = 40;
		public int holdTicks = 30;
		public float squeezeDamagePerSecond = 3.0f;
		public float flingDamage = 10.0f;
		public double flingSpeed = 2.6;
	}

	public static final class Transmute {
		/** Patches under up to this many fighters. */
		public int maxPatches = 3;
		public int radius = 3;
		public int lifeTicks = 200;
		public float damagePerSecond = 3.0f;
	}

	public static final class Wave {
		public float damage = 12.0f;
		/** Blocks a tick the ring grows. */
		public double speed = 0.9;
		/** The ring reaches this far (blocks from his feet). */
		public double reach = 150.0;
		/** Anyone this high above the ground at the ring is over it. */
		public double clearHeight = 1.25;
	}

	public static final class Summons {
		public int perCast = 2;
		/** Live Punishers (guards and summons together) at most. */
		public int cap = 6;
	}

	public static final class Pylons {
		public int count = 4;
		public double ring = 32.0;
		public double health = 300.0;
		public double healthPerExtraFighter = 80.0;
	}

	public static final class WeakPoints {
		public double emblemHealth = 600.0;
		public double crestHealth = 500.0;
		public double gauntletHealth = 450.0;
		public double healthPerExtraFighter = 120.0;
		/** v0.16.4: hits on a weak point are multiplied by this (they are where he is vulnerable). */
		public double damageMultiplier = 2.0;
		/** Damage to a weak point also comes off Galactus at this rate. */
		public double passThrough = 1.0;
		/** Breaking one takes this fraction of his max health. */
		public double breakDamageFraction = 0.08;
	}

	public static final class Nullifier {
		/** Ticks of holding right-click on him to use it. */
		public int useTicks = 60;
		/** It must be aimed at him from no further than this. */
		public double range = 160.0;
		/** A missing part (lost to lava or the void) is put back near the Surfer after this many ticks. */
		public int lostPartTicks = 200;
	}

	public static final class Rewards {
		public double valuablesMultiplier = 1.0;
		public int netheriteMin = 2;
		public int netheriteMax = 4;
		public int books = 3;
		/** First clear: the Silver Surfer's Board and the Power Cosmic. */
		public boolean firstClearItems = true;
	}

	public static final class Board {
		public double flySpeed = 1.1;
		public double boostSpeed = 2.4;
		public double acceleration = 0.18;
	}

	public static final class PowerCosmic {
		public float blastDamage = 10.0f;
		public double blastRange = 32.0;
		public int blastCooldownTicks = 40;
		public int shieldTicks = 60;
		public int shieldCooldownTicks = 400;
	}

	// ---------------------------------------------------------------- access

	public static GalactusConfig get() {
		return instance;
	}

	public static Trigger trigger() {
		return instance.trigger;
	}

	public static Arena arena() {
		return instance.arena;
	}

	public static Herald herald() {
		return instance.herald;
	}

	public static Ally ally() {
		return instance.ally;
	}

	public static Converters converters() {
		return instance.converters;
	}

	public static Punisher punisher() {
		return instance.punisher;
	}

	public static Consumption consumption() {
		return instance.consumption;
	}

	public static Colossus galactus() {
		return instance.galactus;
	}

	public static EyeBeam eyeBeam() {
		return instance.eyeBeam;
	}

	public static Grab grab() {
		return instance.grab;
	}

	public static Transmute transmute() {
		return instance.transmute;
	}

	public static Wave wave() {
		return instance.wave;
	}

	public static Summons summons() {
		return instance.summons;
	}

	public static Pylons pylons() {
		return instance.pylons;
	}

	public static WeakPoints weakPoints() {
		return instance.weakPoints;
	}

	public static Nullifier nullifier() {
		return instance.nullifier;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	public static Board board() {
		return instance.board;
	}

	public static PowerCosmic powerCosmic() {
		return instance.powerCosmic;
	}

	private static double scaled(double base, double per, int fighters) {
		return base + per * Math.max(0, fighters - 1);
	}

	public static double heraldHealthFor(int fighters) {
		return scaled(instance.herald.health, instance.herald.healthPerExtraFighter, fighters);
	}

	public static double galactusHealthFor(int fighters) {
		return scaled(instance.galactus.health, instance.galactus.healthPerExtraFighter, fighters);
	}

	public static double converterHealthFor(int fighters) {
		return scaled(instance.converters.health, instance.converters.healthPerExtraFighter, fighters);
	}

	public static double punisherHealthFor(int fighters) {
		return scaled(instance.punisher.health, instance.punisher.healthPerExtraFighter, fighters);
	}

	public static double pylonHealthFor(int fighters) {
		return scaled(instance.pylons.health, instance.pylons.healthPerExtraFighter, fighters);
	}

	public static double gripHealthFor(int fighters) {
		return scaled(instance.grab.gripHealth, instance.grab.gripHealthPerExtraFighter, fighters);
	}

	/** Weak point {@code kind} ({@link com.projecthero.mod.galactus.entity.GalactusWeakPointEntity}) health. */
	public static double weakPointHealthFor(int kind, int fighters) {
		WeakPoints w = instance.weakPoints;
		double base = switch (kind) {
			case 0 -> w.emblemHealth;
			case 1 -> w.crestHealth;
			default -> w.gauntletHealth;
		};
		return scaled(base, w.healthPerExtraFighter, fighters);
	}

	/** How many Element Converters crash down for {@code fighters}: base, +1 per two extra fighters, capped. */
	public static int converterCountFor(int fighters) {
		Converters c = instance.converters;
		int n = c.base + c.perTwoExtraFighters * (Math.max(0, fighters - 1) / 2);
		return Math.max(1, Math.min(c.max, n));
	}

	public static void load() {
		instance = SPEC.load();
	}
}
