package com.projecthero.mod.hulk;

/**
 * Every Hulk tunable. The core rage / transform loop (Phase 1) is static finals like {@code AllMightConfig};
 * the abilities and the world rules (Phase 2) live in a JSON file, {@code config/projecthero_hulk.json}, re-read
 * at start-up and rewritten so new keys appear -- same hand-rolled GSON approach as {@code TitanShifterConfig}.
 * Ticks are 20 per second.
 */
public final class HulkConfig {
	private static HulkConfig instance = new HulkConfig();

	/** Schema version (nullable on purpose -- see {@code TitanShifterConfig#configVersion}). */
	public Integer configVersion;
	public Abilities abilities = new Abilities();
	public World world = new World();
	public Control control = new Control();
	public Calm calm = new Calm();
	/** v0.15.3: the Gladiator Hulk's weapon kit (used instead of {@link #abilities} while the full gladiator gear is on). */
	public Gladiator gladiator = new Gladiator();

	/**
	 * v0.15.3: damage, reach and cooldown of the twelve Gladiator Hulk moves (tap / Shift+key on R G Z X C V). A little
	 * harder per hit than the bare-handed kit -- he has an axe and a hammer.
	 */
	public static final class Gladiator {
		// ---- R Axe Cleave: a wide horizontal sweep in front, bleeds ----
		public float axeCleaveDamage = 34.0f;
		public double axeCleaveRange = 5.5;
		/** Full width of the arc in degrees. */
		public double axeCleaveArcDegrees = 160.0;
		public double axeCleaveKnockback = 2.4;
		/** Bleed: this much every second for {@link #bleedSeconds} seconds. */
		public float bleedDamage = 2.0f;
		public int bleedSeconds = 4;
		public int axeCleaveCooldownTicks = 100;

		// ---- Shift+R Hammer Uppercut: one target launched high ----
		public float uppercutDamage = 36.0f;
		public double uppercutRange = 5.0;
		public double uppercutLaunch = 1.6;
		public int uppercutCooldownTicks = 120;

		// ---- G Hammer Quake: overhead slam, a forward cone fissure ----
		public float quakeDamage = 36.0f;
		public double quakeRange = 14.0;
		public double quakeConeDegrees = 60.0;
		public double quakeKnockback = 1.6;
		public double quakeLift = 0.6;
		/** The fissure only cracks blocks at or below this hardness (dirt, sand, gravel, grass, snow...). */
		public float quakeCrackHardness = 0.8f;
		public int quakeCooldownTicks = 140;

		// ---- Shift+G Earthsplitter: a line of erupting earth ----
		public float earthsplitterDamage = 30.0f;
		public double earthsplitterLength = 18.0;
		public double earthsplitterWidth = 3.0;
		public double earthsplitterLift = 1.2;
		public int earthsplitterCooldownTicks = 180;

		// ---- Z Champion's Roar: a short buff, mobs flee ----
		public int roarBuffTicks = 160;
		/** Extra attack damage while the roar lasts. */
		public double roarAttackBonus = 6.0;
		/** Extra knockback resistance while the roar lasts (he already has 0.9). */
		public double roarKnockbackResistance = 0.1;
		public double roarRadius = 12.0;
		public float roarRage = 15.0f;
		public int roarCooldownTicks = 400;
		/** v0.15.7: mobs with at most this much max health are scared off by the roar (bosses never are). */
		public double roarFleeMaxHealth = 40.0;
		/** v0.15.7: how long a scared mob keeps running from him. */
		public int roarFleeTicks = 120;

		// ---- Shift+Z Weapon Clash: a ringing shockwave that stuns ----
		public float clashDamage = 18.0f;
		public double clashRadius = 7.0;
		public int clashStunTicks = 30;
		public int clashCooldownTicks = 240;

		// ---- X Arena Leap: a big ballistic leap, weapons-first landing ----
		public double arenaLeapBlocks = 28.0;
		public float arenaLeapDamage = 32.0f;
		public double arenaLeapRadius = 6.0;
		public double arenaLeapCraterRadius = 3.0;
		public int arenaLeapCooldownTicks = 160;

		// ---- Shift+X Meteor Dive: straight up, then a guided dive ----
		public double meteorRiseSpeed = 2.0;
		public double meteorDiveSpeed = 2.8;
		public double meteorDiveRange = 60.0;
		public float meteorDamage = 55.0f;
		public double meteorRadius = 3.5;
		public double meteorCraterRadius = 2.5;
		public int meteorCooldownTicks = 300;

		// ---- C Axe Throw: out and back ----
		public float axeThrowDamage = 26.0f;
		public double axeThrowRange = 24.0;
		public double axeThrowSpeed = 1.8;
		public int axeThrowCooldownTicks = 100;

		// ---- Shift+C Hammer Hurl: an arc, a shockwave, stuck until recalled ----
		public float hammerHurlDamage = 30.0f;
		public double hammerHurlRadius = 4.5;
		public double hammerHurlSpeed = 1.6;
		/** Hit by the hammer flying home after C recalls it. */
		public float hammerRecallDamage = 20.0f;
		/** A hammer left in the ground flies home on its own after this long. */
		public int hammerStuckTicks = 600;
		public int hammerHurlCooldownTicks = 160;

		// ---- V Gladiator Whirlwind: a spin with both weapons ----
		public int whirlwindTicks = 60;
		public int whirlwindPulseTicks = 5;
		public float whirlwindDamage = 8.0f;
		public double whirlwindRadius = 3.5;
		public double whirlwindPullRadius = 7.0;
		public float whirlwindSlamDamage = 26.0f;
		public double whirlwindSlamRadius = 5.0;
		/** Counted from the end of the spin. */
		public int whirlwindCooldownTicks = 240;

		// ---- Shift+V Arena Grapple: pin a target and pound it ----
		public double grappleRange = 5.0;
		public int grappleBlows = 5;
		public int grappleBlowTicks = 10;
		public float grappleBlowDamage = 9.0f;
		public double grappleThrowSpeed = 2.0;
		public float grappleThrowDamage = 14.0f;
		/** Counted from the release / throw. */
		public int grappleCooldownTicks = 200;
	}

	/** Damage, reach and cooldown of every Hulk ability (v0.13.14 kit: R G Z X C V). */
	public static final class Abilities {
		// ---- R Power Punch: a 3-block-wide, 6-block-long punch ----
		public float powerPunchDamage = 30.0f;
		public double powerPunchRange = 6.0;
		public double powerPunchWidth = 3.0;
		public double powerPunchKnockback = 2.6;
		public int powerPunchCooldownTicks = 100;

		// ---- G Ground Smash: both fists into the ground, a ring all round him and a crater ----
		public float groundSmashDamage = 30.0f;
		public double groundSmashRadius = 7.0;
		public double groundSmashKnockback = 1.5;
		public double groundSmashLift = 0.75;
		public double groundSmashCraterRadius = 3.0;
		public int groundSmashCooldownTicks = 100;

		// ---- Z Thunderclap: a cone shockwave in front of him ----
		public float thunderclapDamage = 22.0f;
		/** v0.13.17: 12 -> 25 blocks (a 12.0 in an older config file is moved up too -- see {@link #load}). */
		public double thunderclapRange = 25.0;
		/** Full width of the cone in degrees. */
		public double thunderclapConeDegrees = 70.0;
		public double thunderclapKnockback = 2.2;
		public int thunderclapCooldownTicks = 160;

		// ---- Shift+Z HULK SMASH: hold 5 s, then everything in front and around him ----
		public int hulkSmashChargeTicks = 100;
		public float hulkSmashDamage = 100.0f;
		public double hulkSmashForwardRange = 25.0;
		public double hulkSmashForwardWidth = 8.0;
		public double hulkSmashRadius = 30.0;
		public double hulkSmashCraterRadius = 6.0;
		public int hulkSmashCooldownTicks = 1200;

		// ---- X Super Leap (hold then release) ----
		public int leapMaxChargeTicks = 30;
		public double leapMinBlocks = 15.0;
		public double leapMaxBlocks = 70.0;
		public int leapCooldownTicks = 40;
		public float leapLandingDamage = 8.0f;
		public double leapLandingRadius = 4.5;

		// ---- C Charge: runs forward on his own for 8 s ----
		public int chargeTicks = 160;
		/** Blocks per tick (0.6 = 12 blocks a second). */
		public double chargeSpeed = 0.6;
		public float chargeDamage = 20.0f;
		public double chargeHitRadius = 2.4;
		/** Counted from when the run ends. */
		public int chargeCooldownTicks = 240;

		// ---- V Grab / Throw / Crush, Shift+V Earth Chunk ----
		public double grabRange = 6.0;
		public float crushDamage = 26.0f;
		public double throwSpeed = 2.0;
		public float throwImpactDamage = 16.0f;
		public float boulderDamage = 30.0f;
		public double boulderRadius = 4.0;
		public double boulderSpeed = 1.7;
		/** Counted from the throw / crush. */
		public int grabCooldownTicks = 160;

		// ---- Sprint Smash (always on while sprinting as the Hulk; world.sprintSmashEnabled is the server switch) ----
		/** Minimum horizontal speed (blocks / tick) before sprinting into a block breaks it. */
		public double sprintSmashMinSpeed = 0.2;
	}

	/** Keeping control of the Hulk: stop dealing damage and he starts to take over. */
	public static final class Control {
		public boolean enabled = true;
		/** Ticks without dealing damage before control starts to slip. */
		public int graceTicks = 160;
		public float drainPerSecond = 6.0f;
		/** While control is slipping, a key prompt appears this often... */
		public int promptEveryTicks = 50;
		/** ...and must be answered within this long. */
		public int promptWindowTicks = 30;
		public float promptRestore = 25.0f;
		public float promptPenalty = 15.0f;
		/** How long a rampage lasts before the player wrestles control back. */
		public int rampageTicks = 300;
		/** Control the player gets back when a rampage ends. */
		public float rampageRestore = 60.0f;
	}

	/** The calm-down minigame (hold N). */
	public static final class Calm {
		public int holdTicks = 40;
		/** Out of combat this long before it can start. */
		public int outOfCombatTicks = 60;
		public float hitDrain = 14.0f;
		public float missRage = 4.0f;
	}

	/** What the Hulk is allowed to do to the world. */
	public static final class World {
		/** Master switch for every block the Hulk breaks (Thunderclap's fragile blocks, Sprint Smash). */
		public boolean blockBreaking = true;
		/** Server switch for Sprint Smash, so worlds don't get wrecked. */
		public boolean sprintSmashEnabled = true;
		/** Sprint Smash only breaks blocks at or below this hardness (dirt 0.5, wood 2, stone 1.5, ores 3, obsidian 50). */
		public float maxBreakableHardness = 3.0f;
		/** Broken blocks drop their items. */
		public boolean dropBrokenBlocks = true;
		/** If true, the mobGriefing gamerule also stops the Hulk breaking blocks. */
		public boolean respectMobGriefing = false;
		/** Camera shake from Ground Smash, Thunderclap and leap landings. */
		public boolean screenShake = true;
	}

	private HulkConfig() {
	}

	public static HulkConfig get() {
		return instance;
	}

	public static Abilities abilities() {
		return instance.abilities;
	}

	public static World world() {
		return instance.world;
	}

	public static Control control() {
		return instance.control;
	}

	public static Calm calm() {
		return instance.calm;
	}

	public static Gladiator gladiator() {
		return instance.gladiator;
	}

	/**
	 * v0.14.21: loaded through the shared {@link com.projecthero.mod.config.VersionedConfig} (see {@code docs/CONFIGS.md}).
	 * The steps are the migrations this file already had, unchanged.
	 */
	public static final com.projecthero.mod.config.VersionedConfig<HulkConfig> SPEC = com.projecthero.mod.config.VersionedConfig
			.builder(HulkConfig.class, "projecthero_hulk.json", HulkConfig::new)
			.balance("abilities", "control", "calm", "gladiator")
			.introduce(1, "v0.13.12 first file")
			// v0.13.14 rebuilt the kit (new keys, new damage and cooldowns): a 0.13.13 file must not keep the old numbers
			.reset(2, "v0.13.14 Hulk kit rebuilt", "abilities")
			// v0.13.17: Thunderclap reaches 25 blocks -- move the old default up, but keep a range a server chose itself
			.custom(3, "v0.13.17 Thunderclap 12 -> 25 blocks", (file, defaults) -> {
				com.google.gson.JsonElement range = com.projecthero.mod.config.VersionedConfig.at(file, "abilities.thunderclapRange");
				if (range != null && range.isJsonPrimitive() && range.getAsJsonPrimitive().isNumber() && range.getAsDouble() == 12.0) {
					com.projecthero.mod.config.VersionedConfig.resetKey(file, defaults, "abilities.thunderclapRange");
				}
			})
			// v0.15.3: the new "gladiator" section is filled in from the defaults; nothing a server set is touched
			.introduce(4, "v0.15.3 Gladiator Hulk moves section")
			.build();

	public static void load() {
		// migrates, fills in new keys / sections, repairs a corrupt file and always rewrites it
		instance = SPEC.load();
	}

	// ---------------- rage ----------------
	public static final float RAGE_MAX = 100.0f;
	/** H transforms by hand from this much rage; at {@link #RAGE_MAX} the change is forced. */
	public static final float MANUAL_TRANSFORM_RAGE = 75.0f;
	/**
	 * v0.13.17: rage per point of damage TAKEN, both forms -- 5 damage = 5% (was 2.5 as Banner, 1.5 as the Hulk). Banner
	 * gets nothing for the damage he deals himself.
	 */
	public static final float RAGE_PER_DAMAGE_TAKEN = 1.0f;
	public static final float HULK_RAGE_PER_DAMAGE_TAKEN = 1.0f;
	/** v0.13.17: the Hulk gains this much rage every time he hits something (a punch, or each thing an ability hits). */
	public static final float HULK_RAGE_PER_HIT = 2.0f;
	/** v0.13.17: out of combat (no hit taken or dealt) this long, the Hulk's rage burns off at {@link #HULK_DRAIN_PER_SECOND}. */
	public static final int HULK_OUT_OF_COMBAT_TICKS = 5 * 20;
	/** Rage the Hulk burns every second once out of combat (v0.13.17: was 1.0 a second, all the time). */
	public static final float HULK_DRAIN_PER_SECOND = 0.75f;
	/**
	 * Banner calms down when left alone: rage bleeds off this fast once {@link #CALM_DELAY_TICKS} pass without being
	 * HURT (v0.13.17: 2 a second after 5 s; was 0.5 after 15 s of no fighting at all).
	 */
	public static final float CALM_DECAY_PER_SECOND = 2.0f;
	public static final int CALM_DELAY_TICKS = 5 * 20;
	/** v0.13.12: a Gamma Reactor within this many blocks feeds a Gamma player's rage... */
	public static final int REACTOR_RADIUS = 4;
	/** ...by this much a second (also counts as not being calm, so it never bleeds off beside one). */
	public static final float REACTOR_RAGE_PER_SECOND = 3.0f;

	// ---------------- the change ----------------
	/** Ticks the body takes to grow (or shrink back) -- the change the player chose with H, and every change back. */
	public static final int GROWTH_TICKS = 30;
	/**
	 * v0.13.15: the unwilling change (rage hit the top, or the death save) is slower and happens on his knees: Banner drops for
	 * {@link #FORCED_KNEEL_TICKS}, grows over {@link #FORCED_GROWTH_TICKS} while the Hulk takes him over, then rises and roars over
	 * {@link #FORCED_RISE_TICKS}. He cannot move or use abilities until it is over.
	 */
	public static final int FORCED_KNEEL_TICKS = 20;
	public static final int FORCED_GROWTH_TICKS = 60;
	public static final int FORCED_RISE_TICKS = 24;
	public static final int FORCED_CHANGE_TICKS = FORCED_KNEEL_TICKS + FORCED_GROWTH_TICKS + FORCED_RISE_TICKS;
	/** Anti-spam gate on H. */
	public static final int TOGGLE_DEBOUNCE_TICKS = 10;
	/** Health healed on top of the carried-over health percentage when the Hulk comes out. */
	public static final float TRANSFORM_HEAL = 20.0f;
	/** After changing back: Weakness + Slowness for this long, and no rage can build. */
	public static final int EXHAUSTED_TICKS = 8 * 20;

	// ---------------- Hulk stats (fixed-id transient attribute modifiers) ----------------
	/** Scale bonus: 1 + 0.8 = 1.8x (a 3.24-block Hulk). */
	public static final double SCALE_BONUS = 0.8;
	/** v0.13.14: punches land 20 (1 base + 19). */
	public static final double ATTACK_BONUS = 19.0;
	/** v0.13.14: punches throw things much further. */
	public static final double ATTACK_KNOCKBACK_BONUS = 1.5;
	/** v0.13.14: +50% movement -- a 1.8x body at normal speed looked like it was wading. */
	public static final double SPEED_BONUS = 0.5;
	/** v0.13.14: diamond-level protection built in (a full diamond set: 20 armour, 8 toughness). */
	public static final double ARMOR_BONUS = 20.0;
	public static final double HEALTH_BONUS = 40.0;
	public static final double KNOCKBACK_RESISTANCE = 0.9;
	public static final double ARMOR_TOUGHNESS_BONUS = 8.0;
	/** A bigger stride: steps straight up a full block. */
	public static final double STEP_HEIGHT_BONUS = 0.5;
	/** Longer arms for a bigger body. */
	public static final double REACH_BONUS = 1.5;
	/** v0.13.14: fast regeneration -- {@link #REGEN_AMOUNT} HP every {@link #REGEN_INTERVAL_TICKS} (3 HP a second). */
	public static final int REGEN_INTERVAL_TICKS = 5;
	public static final float REGEN_AMOUNT = 0.75f;
	/** v0.13.14: armour worn when he changes takes this much durability and falls off. */
	public static final int ARMOUR_TEAR_DAMAGE = 50;
	/** v0.13.14: lava and explosions only do this fraction of their damage to the Hulk (fire, arrows and falls: none). */
	public static final float LAVA_FACTOR = 0.25f;
	public static final float EXPLOSION_FACTOR = 0.5f;
}
