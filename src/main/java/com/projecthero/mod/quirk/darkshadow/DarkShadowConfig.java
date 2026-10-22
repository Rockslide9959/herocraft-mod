package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.7: Dark Shadow's ability ids (constants) and every number (a file, {@code config/projecthero_quirk_dark_shadow.json},
 * through {@link VersionedConfig}: {@link #get()}). Ticks unless noted; damage values are the base at strength 1.0 -- the
 * real hit is {@code base * DarkShadowCombat.strength(player)}, which follows the light at the shadow
 * ({@link Light#strengthMin} in daylight, {@link Light#strengthMax} in the pitch dark, {@link Berserk#strength} berserk).
 *
 * <p>The Quirk revamp (docs/QUIRK_REVAMP_V0167.md) replaced the old static numbers; there was no file before, so version 1
 * is the file's start ({@code introduce}). The ability ids of moves that kept their job keep their id (old saves' cooldowns
 * stay valid): Strike, Grab, Guard (now Shadow Shield), Black Ankh, Calm (now Light Flare).
 */
public final class DarkShadowConfig {
	// ---------------------------------------------------------------- ability ids (R G X Z V C, tap / Shift)
	public static final String STRIKE = "quirk_dark_shadow_strike";
	public static final String FANG = "quirk_dark_shadow_dark_fang";
	public static final String GRAB = "quirk_dark_shadow_grab";
	public static final String SHIELD = "quirk_dark_shadow_guard";
	public static final String ABYSSAL = "quirk_dark_shadow_abyssal_ankh";
	public static final String FETCH = "quirk_dark_shadow_fetch";
	public static final String SCOUT = "quirk_dark_shadow_scout";
	public static final String NOVA = "quirk_dark_shadow_sunshine_ankh";
	public static final String ANKH = "quirk_dark_shadow_black_ankh";
	public static final String RECALL = "quirk_dark_shadow_recall";
	public static final String BERSERK = "quirk_dark_shadow_berserk";
	public static final String FLARE = "quirk_dark_shadow_calm";

	public static final VersionedConfig<DarkShadowConfig> SPEC = VersionedConfig
			.builder(DarkShadowConfig.class, "projecthero_quirk_dark_shadow.json", DarkShadowConfig::new)
			.balance("light", "meter", "berserk", "moves")
			.introduce(1, "v0.16.7 Dark Shadow revamp -- the living shadow (light / darkness, Berserk until light, 12 new moves)")
			.build();

	private static DarkShadowConfig instance = new DarkShadowConfig();

	public static DarkShadowConfig get() {
		return instance;
	}

	/** Reads (migrating) the file; called from {@code DarkShadowMod.initialize}. */
	public static void load() {
		instance = SPEC.load();
	}

	public Integer configVersion;
	public Light light = new Light();
	public Meter meter = new Meter();
	public Berserk berserk = new Berserk();
	public Moves moves = new Moves();

	public DarkShadowConfig() {
	}

	/** How light at the shadow becomes darkness (0 bright .. 1 pitch dark), its size and its strength. */
	public static final class Light {
		/** Light at or above this: darkness 0 (it is small, timid, weak). */
		public int brightLight = 13;
		/** Light at or below this: darkness 1. Between the two it scales linearly. */
		public int pitchLight = 2;
		/** "Pitch dark" for the meter: at or below this it climbs fast toward Berserk. */
		public int pitchDarkMeter = 3;
		/** "Darkness" for intentional Berserk: at or below this. */
		public int darkLight = 7;
		/** Light at or above this calms it: a Berserk ends, the meter can't reach 100. */
		public int calmLight = 10;
		/** A light source held in either hand counts as its light emission minus this (a torch: 14 - 2 = 12). */
		public int heldLightPenalty = 2;
		/** How fast the darkness it feels follows the real light, per tick (0..1). */
		public float smoothing = 0.15f;
		/** Damage multiplier at darkness 0 / 1. */
		public float strengthMin = 0.4f;
		public float strengthMax = 1.7f;
		/** The model's scale at darkness 0 / 1 (Berserk: {@link Berserk#size}); the client reads these from its own copy. */
		public float sizeMin = 0.45f;
		public float sizeMax = 1.0f;
	}

	/** The Shadow meter, per tick. */
	public static final class Meter {
		public float pitchRise = 0.25f;
		public float dimRise = 0.05f;
		/** In dim light (above {@link Light#pitchDarkMeter}, below {@link Light#calmLight}) the light alone stops here. */
		public float dimCap = 85f;
		public float brightFall = 0.5f;
		public float recallFall = 0.75f;
		/** In Black Ankh it is close to you and calmer: its rise is multiplied by this. */
		public float ankhRiseFactor = 0.5f;
		/** What the moves push it by. */
		public float strikePush = 2f;
		public float fangPush = 6f;
		public float grabPush = 6f;
		public float scoutPush = 4f;
		public float fetchPush = 3f;
		public float novaPush = 10f;
		public float shieldPushPerSecond = 1f;
	}

	public static final class Berserk {
		/** Its scale while berserk (it towers over you). */
		public float size = 2.2f;
		public float strength = 2.0f;
		public double radius = 8.0;
		public int interval = 10;
		public int targetsPerSwing = 2;
		public float damage = 7f;
		public double knockback = 1.0;
		/** It turns on you too: every this many ticks, {@link #selfDamage} -- never taking you under {@link #selfFloor} HP. */
		public int selfInterval = 50;
		public float selfDamage = 3f;
		public float selfFloor = 2f;
		/** The meter once light has calmed it. */
		public float afterMeter = 35f;
		/** Never hits villagers (the frenzy has some limits). */
		public boolean spareVillagers = true;
		public float bossHealthThreshold = 300f;
		public float bossMaxFractionPerHit = 0.10f;
	}

	public static final class Moves {
		// R: Shadow Strike (in Black Ankh: a clawed dash) / Shift+R: Dark Fang
		public double strikeRangeMin = 5.0;
		public double strikeRangeMax = 10.0;
		public double strikeAssistDegrees = 10.0;
		public float strikeDamage = 8f;
		public double strikeKnockback = 0.7;
		public int strikeCooldown = 14;
		public double ankhDashSpeed = 1.7;
		public int ankhDashTicks = 6;
		public float ankhDashDamage = 7f;
		public int ankhDashCooldown = 24;
		public double fangRangeMin = 4.0;
		public double fangRangeMax = 8.0;
		public int fangBites = 4;
		public int fangInterval = 4;
		public float fangDamage = 3.5f;
		public int fangCooldown = 80;
		// G: Shadow Grab (lift + slam) / Shift+G: Shadow Shield (held)
		public double grabRangeMin = 6.0;
		public double grabRangeMax = 12.0;
		public int grabPullTicks = 8;
		public int grabSlamTicks = 6;
		public double grabHoldDistance = 2.4;
		public double grabLift = 2.5;
		public float grabDamage = 2f;
		public float grabSlamDamage = 9f;
		public double grabSplashRadius = 2.5;
		public float grabSplashDamage = 4f;
		public int grabCooldown = 80;
		public float shieldBlockMin = 0.55f;
		public float shieldBlockMax = 0.95f;
		public int shieldMaxTicks = 160;
		public int shieldCooldown = 50;
		// X: Abyssal Black Ankh (glide) / Shift+X: Fetch
		public double abyssalLaunch = 0.95;
		public double abyssalBoostMin = 0.02;
		public double abyssalBoostMax = 0.07;
		public double abyssalMaxSpeed = 1.5;
		public int abyssalCooldown = 30;
		public double fetchRangeMin = 10.0;
		public double fetchRangeMax = 20.0;
		public double fetchItemRadius = 3.0;
		public int fetchTicks = 10;
		public int fetchMaxTicks = 30;
		public int fetchCooldown = 100;
		// Z: Shadow Scout / Shift+Z: Sunshine Ankh (the nova)
		public double scoutRangeMin = 12.0;
		public double scoutRangeMax = 28.0;
		public int scoutOutTicks = 10;
		public int scoutHoverTicks = 12;
		public double scoutRadius = 7.0;
		public double scoutPathRadius = 3.0;
		public int scoutGlowTicks = 200;
		public int scoutCooldown = 200;
		public double novaRadiusMin = 4.0;
		public double novaRadiusMax = 8.0;
		public float novaDamage = 10f;
		public double novaKnockback = 1.3;
		public int novaCooldown = 300;
		// V: Black Ankh (mode) / Shift+V: Recall
		public double ankhArmorMin = 4.0;
		public double ankhArmorMax = 10.0;
		public double ankhToughness = 3.0;
		public double ankhDamage = 3.0;
		public double ankhReach = 1.0;
		public int ankhRefresh = 20;
		public int ankhCooldown = 30;
		public int recallCooldown = 20;
		// C: Berserk on purpose / Shift+C: Light Flare
		public int berserkCooldown = 600;
		public int flareCooldown = 300;
		public double flareRadius = 8.0;
		public int flareBlindTicks = 60;
		/** The flare's light lasts this long (darkness 0, it shrinks). */
		public int flareLightTicks = 60;
		public float flareMeter = 15f;
	}
}
