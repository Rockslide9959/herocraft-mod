package com.projecthero.mod.frostgiant;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.1: the Frost Giant Assault's tuning, at {@code config/projecthero_frost_giants.json} (loaded through
 * {@link VersionedConfig}, see {@code docs/CONFIGS.md}). Read before the entity types are registered: their default
 * attributes come from here.
 *
 * <p>Every number the raid uses lives here -- the arena, the blizzard and the cold, the Rune Braziers, the eight waves,
 * every Frost Giant's health and damage, King Laufey's three phases, Ymir, the damage rules and the rewards -- plus the
 * one server toggle: the natural trigger (Heimdall's warning), off by default.
 */
public final class FrostGiantConfig {
	public static final VersionedConfig<FrostGiantConfig> SPEC = VersionedConfig.builder(FrostGiantConfig.class,
			"projecthero_frost_giants.json", FrostGiantConfig::new)
			.balance("arena", "cold", "braziers", "waves", "hound", "raider", "shaman", "brute", "warlord", "laufey", "ymir",
					"damage", "rewards", "casket", "gear", "trigger.chancePerMinute")
			.introduce(1, "v0.16.1 Frost Giant Assault")
			// v0.16.1 (later, user request): 8 waves instead of 5 -- three heavier waves, two Warlords in the last
			.reset(2, "v0.16.1 eight waves", "waves.waveCount", "waves.hounds", "waves.raiders", "waves.shamans", "waves.brutes", "waves.warlords")
			.build();

	private static FrostGiantConfig instance = new FrostGiantConfig();

	public Integer configVersion;
	public Trigger trigger = new Trigger();
	public Arena arena = new Arena();
	public Cold cold = new Cold();
	public Braziers braziers = new Braziers();
	public Waves waves = new Waves();
	public Hound hound = new Hound();
	public Raider raider = new Raider();
	public Shaman shaman = new Shaman();
	public Brute brute = new Brute();
	public Warlord warlord = new Warlord();
	public Laufey laufey = new Laufey();
	public Ymir ymir = new Ymir();
	public Damage damage = new Damage();
	public Rewards rewards = new Rewards();
	public Casket casket = new Casket();
	public Gear gear = new Gear();

	public static final class Trigger {
		/**
		 * The natural trigger: in a snowy biome, at night, during a thunderstorm, a player who is Thor-worthy or holds
		 * Mjolnir / Stormbreaker rolls {@link #chancePerMinute} once a minute. Heimdall warns them and a Jotunheim Rune
		 * Stone appears in their inventory. Off by default.
		 */
		public boolean naturalTriggerEnabled = false;
		public double chancePerMinute = 0.02;
	}

	public static final class Arena {
		/** The border ring (blocks from the Rift Anchor). Fighters are kept inside; anyone who walks in joins. */
		public double radius = 56.0;
		/** Height of the rift above the anchor. */
		public double riftHeight = 40.0;
		/** Ticks a giant takes to climb down the frost beam from the rift. */
		public int arrivalTicks = 40;
		/** The blizzard's view distance (fog end, blocks) and when the Casket is open. */
		public double fogBlocks = 48.0;
		public double fogBlocksCasketOpen = 32.0;
		/** Temporary snow layers placed per framework tick (every 0.5 s), and the most the arena ever gets. */
		public int snowLayersPerTick = 6;
		public int maxSnowLayers = 1600;
		/** Snow layers only ever stack this high. */
		public int maxSnowDepth = 2;
		/** Temporary ice blocks (walls, pillars, spikes, frozen water) placed per server tick at most. */
		public int iceBlocksPerTick = 48;
	}

	public static final class Cold {
		/** Freeze ticks gained per second outside warmth (vanilla full frost is 140). */
		public double buildPerSecond = 14.0;
		/** Freeze ticks lost per second inside a brazier's warmth (on top of vanilla's own thaw). */
		public double thawPerSecond = 60.0;
		/** Extra freeze damage every 2 s at full frost; with vanilla's own that is 1 damage a second. */
		public float frozenDamage = 1.0f;
		/** Freeze builds this much faster with every brazier out, and again with the Casket open. */
		public double allOutMultiplier = 2.0;
		public double casketOpenMultiplier = 2.0;
	}

	public static final class Braziers {
		public int count = 4;
		public double ring = 20.0;
		public double health = 200.0;
		public double healthPerExtraFighter = 60.0;
		/** Warmth radius. */
		public double warmRadius = 7.0;
		/** Giants go for a lit brazier when no fighter is this close to them. */
		public double ignoreWhenFighterWithin = 8.0;
		/** Ticks of holding right-click to relight one by hand (flint and steel, a fire charge or a fire power: instant). */
		public int relightTicks = 60;
		/** A relit brazier comes back at this fraction of its health. */
		public double relightHealth = 0.5;
		/** The Casket opening (Laufey phase 2) takes this fraction off every brazier. */
		public double casketOpenDrain = 0.3;
	}

	public static final class Waves {
		public int waveCount = 8;
		/** Each extra fighter adds this fraction of every wave entry. */
		public double perExtraFighter = 0.5;
		/** Live giants at once (the giants are big). */
		public int enemyCap = 16;
		public int countdownSeconds = 10;
		public int breatherSeconds = 10;
		/** Solo composition per wave (index 0 = wave 1). */
		public int[] hounds = { 6, 4, 4, 3, 3, 4, 3, 3 };
		public int[] raiders = { 0, 3, 3, 3, 2, 4, 4, 3 };
		public int[] shamans = { 0, 0, 2, 2, 2, 2, 3, 3 };
		public int[] brutes = { 0, 0, 0, 1, 1, 2, 2, 2 };
		public int[] warlords = { 0, 0, 0, 0, 1, 0, 0, 2 };
	}

	public static final class Hound {
		public double health = 40.0;
		public double armor = 2.0;
		public double speed = 0.38;
		public float biteDamage = 5.0f;
		public int biteFreezeTicks = 40;
		public float pounceDamage = 6.0f;
	}

	public static final class Raider {
		public double health = 140.0;
		public double armor = 6.0;
		public double speed = 0.27;
		public float spearDamage = 10.0f;
		public double spearReach = 4.0;
		public float javelinDamage = 9.0f;
		public int javelinFreezeTicks = 60;
		public double javelinMinRange = 8.0;
		public double javelinMaxRange = 24.0;
		public int javelinCooldownTicks = 90;
	}

	public static final class Shaman {
		public double health = 160.0;
		public double armor = 4.0;
		public double speed = 0.25;
		/** Keeps at least this far from the fighter it is fighting. */
		public double keepAway = 14.0;
		public int iceWallTicks = 160;
		public int iceWallCooldownTicks = 220;
		public int wardTicks = 80;
		public double wardHeal = 0.25;
		public int wardCooldownTicks = 260;
		public float novaDamage = 6.0f;
		public double novaRadius = 4.0;
		public int novaFreezeTicks = 120;
		public int novaCooldownTicks = 100;
	}

	public static final class Brute {
		public double health = 450.0;
		public double armor = 12.0;
		public double speed = 0.24;
		public float meleeDamage = 14.0f;
		public float brazierSmashDamage = 40.0f;
		public float chargeDamage = 16.0f;
		public double chargeSpeed = 0.85;
		public int chargeCooldownTicks = 160;
		public float spikeDamage = 12.0f;
		public double spikeRange = 16.0;
		public int spikeCooldownTicks = 200;
	}

	public static final class Warlord {
		public double health = 1200.0;
		public double healthPerExtraFighter = 300.0;
		public double armor = 14.0;
		public double speed = 0.22;
		public float cleaveDamage = 18.0f;
		public double cleaveReach = 7.0;
		public float plantDamage = 14.0f;
		public double plantRadius = 12.0;
		public double rallyRadius = 24.0;
		public int rallyTicks = 160;
		public double rallyDamageBonus = 0.2;
		public double kneelAt = 0.3;
		public int kneelTicks = 60;
		public double kneelDamageMultiplier = 1.5;
		public int attackCooldownTicks = 50;
	}

	public static final class Laufey {
		public double health = 2500.0;
		public double healthPerExtraFighter = 600.0;
		public double maxHealth = 6000.0;
		public double armor = 14.0;
		public double armorToughness = 8.0;
		public double speed = 0.18;
		public float spearDamage = 10.0f;
		public int spearFreezeTicks = 60;
		public int guardRaiders = 2;
		/** No Royal Guard while this many giants (Laufey excluded) are already alive. */
		public int guardCap = 4;
		public int graspTelegraphTicks = 30;
		public int prisonTicks = 100;
		public int prisonTicksSolo = 60;
		/** Hits to break a prison: from the prisoner, and from an ally (an ally's hit counts double). */
		public int prisonHits = 8;
		public int prisonHitsSolo = 5;
		public float breathDamage = 8.0f;
		public double breathRange = 22.0;
		public double breathHalfAngle = 22.0;
		public int pillarCount = 6;
		public int pillarHeight = 7;
		public int pillarTicks = 400;
		public float pillarDamage = 14.0f;
		public float slamDamage = 16.0f;
		public double slamRadius = 10.0;
		public int attackCooldownTicks = 60;
		/** Phase 3: cooldowns multiplied by this. */
		public double enragedCooldown = 0.6;
		public int staggerTicks = 100;
		/** Ticks after a stagger before he can stagger again. */
		public int staggerGraceTicks = 60;
		/** He staggers on taking this fraction of his max health within staggerWindowTicks. */
		public double staggerBurst = 0.15;
		public int staggerWindowTicks = 120;
		/** Breaking a Frozen Grasp prison this fast staggers him. */
		public int quickBreakTicks = 30;
		/** Once the Casket breaks he takes this much more damage. */
		public double drainedDamageMultiplier = 2.0;
	}

	public static final class Ymir {
		public double casketHealth = 400.0;
		public double casketHealthPerExtraFighter = 100.0;
		public int attackCooldownTicks = 200;
		public float palmDamage = 30.0f;
		public double palmRadius = 7.0;
		public int palmTelegraphTicks = 40;
		public float sweepDamage = 20.0f;
		public double sweepWidth = 3.0;
		public int sweepTelegraphTicks = 60;
		public int sweepTicks = 20;
	}

	public static final class Damage {
		/** Fire, lava, a burning attacker, Pyrokinesis: x1.5 against every giant. */
		public double fireMultiplier = 1.5;
		/** Lightning (Thor is Jotunheim's old enemy): x1.25. */
		public double lightningMultiplier = 1.25;
	}

	public static final class Rewards {
		/** Rime Shards = shardBase + shardPerFighter x fighters. */
		public int shardBase = 4;
		public int shardPerFighter = 2;
		public int experienceBottlesMin = 12;
		public int experienceBottlesMax = 20;
		/** A Casket of Ancient Winters for every fighter clearing the assault for the first time. */
		public boolean casketOnFirstClear = true;
		/** Scales the gems and ice; 0 gives none. */
		public double valuablesMultiplier = 1.0;
	}

	public static final class Casket {
		public float breathDamage = 4.0f;
		public int breathTicks = 40;
		public double breathRange = 10.0;
		public int breathCooldownTicks = 400;
		public int prisonTicks = 100;
		public int prisonCooldownTicks = 800;
		public double prisonRange = 16.0;
		/** Mobs with this much max health or more resist the Ice Prison. */
		public double bossHealth = 300.0;
		public int jotunheimStormFreezeTicks = 120;
	}

	public static final class Gear {
		/** Rimed netherite: extra toughness per piece. */
		public double rimedToughness = 1.0;
		/** Rime-touched Mjolnir / Stormbreaker: freeze ticks a thrown hit adds. */
		public int rimeTouchedFreezeTicks = 120;
	}

	public static FrostGiantConfig get() {
		return instance;
	}

	public static Trigger trigger() {
		return instance.trigger;
	}

	public static Arena arena() {
		return instance.arena;
	}

	public static Cold cold() {
		return instance.cold;
	}

	public static Braziers braziers() {
		return instance.braziers;
	}

	public static Waves waves() {
		return instance.waves;
	}

	public static Hound hound() {
		return instance.hound;
	}

	public static Raider raider() {
		return instance.raider;
	}

	public static Shaman shaman() {
		return instance.shaman;
	}

	public static Brute brute() {
		return instance.brute;
	}

	public static Warlord warlord() {
		return instance.warlord;
	}

	public static Laufey laufey() {
		return instance.laufey;
	}

	public static Ymir ymir() {
		return instance.ymir;
	}

	public static Damage damage() {
		return instance.damage;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	public static Casket casket() {
		return instance.casket;
	}

	public static Gear gear() {
		return instance.gear;
	}

	/** The Warlord's health for {@code fighters} fighters. */
	public static double warlordHealthFor(int fighters) {
		Warlord w = instance.warlord;
		return w.health + w.healthPerExtraFighter * Math.max(0, fighters - 1);
	}

	/** King Laufey's health for {@code fighters} fighters: the base plus a share per extra fighter, capped. */
	public static double laufeyHealthFor(int fighters) {
		Laufey l = instance.laufey;
		return Math.min(l.maxHealth, l.health + l.healthPerExtraFighter * Math.max(0, fighters - 1));
	}

	/** A Rune Brazier's health for {@code fighters} fighters. */
	public static double brazierHealthFor(int fighters) {
		Braziers b = instance.braziers;
		return b.health + b.healthPerExtraFighter * Math.max(0, fighters - 1);
	}

	/** The Casket of Ancient Winters' health (the phase-3 hit-box) for {@code fighters} fighters. */
	public static double casketHealthFor(int fighters) {
		Ymir y = instance.ymir;
		return y.casketHealth + y.casketHealthPerExtraFighter * Math.max(0, fighters - 1);
	}

	public static void load() {
		instance = SPEC.load();
	}
}
