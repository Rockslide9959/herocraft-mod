package com.projecthero.mod.darkseid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side balance config for the Darkseid Raid ("Apokolips Invasion"), at
 * {@code config/projecthero_darkseid.json}. Same hand-rolled GSON approach as {@code BehemothConfig}, including
 * its {@code configVersion} migration guard: {@link #load} deserialises onto whatever is on disk, so without a
 * version a future balance pass that changes a default would never reach anyone who already has the file.
 *
 * <p>Split by what the numbers belong to: the raid manager ({@link Raid}), Darkseid's body ({@link Boss}), his
 * attacks ({@link Abilities}), the Mother Boxes ({@link MotherBoxes}), the Parademons ({@link Parademons}) and
 * the loot ({@link Rewards}). Every key the design spec names is here under the spec's own name.
 * See {@code docs/DARKSEID_RAID_REFERENCE.md}.
 *
 * <p>v0.13.19: version 2 -- five invasion waves with ~35% more Parademons each (enemy cap 26 -> 34), Parademons +20%
 * health / +15% damage, Darkseid's shared attack cooldown 30 -> 19 ticks, Omega Beams far more often (cooldown 11 s -> 7 s,
 * weight 4.5/6 -> 7/8.5) and zig-zagging, Boom Tube Reinforcements more often (cooldown 26 s -> 20 s, weight 2 -> 2.5),
 * and Mother Boxes coming back online during the fight. {@link #load} moves a v1
 * file's changed values to the new defaults; everything else in the file is kept.
 */
public final class DarkseidConfig {
	/** Bump when a default changes in a way existing files must pick up (and migrate in {@link #load}). */
	private static final int CONFIG_VERSION = 2;

	private static DarkseidConfig instance = new DarkseidConfig();

	public Integer configVersion;
	public Raid raid = new Raid();
	public Boss boss = new Boss();
	public Abilities abilities = new Abilities();
	public MotherBoxes motherBoxes = new MotherBoxes();
	public Parademons parademons = new Parademons();
	public Rewards rewards = new Rewards();

	/** The raid manager: roster, arena, waves, timers. */
	public static final class Raid {
		/** Hard cap on official raid participants. */
		public int maxParticipants = 8;
		/** Players within this many blocks of the Boom Tube Beacon when it is used become participants. */
		public double registrationRadius = 48.0;
		/** Arena radius. Everything happens inside it; participants beyond it are warned then pulled back. */
		public double raidRadius = 64.0;
		/** Beyond this, a participant has deliberately left (teleported home, ...) and is no longer pulled back. */
		public double leaveRadius = 140.0;
		/** Seconds a participant may spend outside the arena before being pulled back in. */
		public int boundaryGraceSeconds = 6;
		/** Once Darkseid falls to this health fraction, no new participants can join the roster. */
		public double rosterSealHealthFraction = 0.50;
		/** Seconds a dead participant must wait after dying before they may re-enter the arena. */
		public int reentryDelaySeconds = 25;
		/** Countdown between activating the beacon and the first wave. */
		public int preparationSeconds = 12;
		/** Quiet between waves. */
		public int betweenWaveSeconds = 8;
		/** Length of Darkseid's entrance cinematic. */
		public int entranceSeconds = 10;
		/** Seconds after Darkseid arrives before the soft enrage begins (the raid's intended length). */
		public int raidSoftEnrageTime = 15 * 60;
		/** Seconds between each further enrage step once enraged. */
		public int softEnrageStepSeconds = 150;
		/** While enraged, one disabled Mother Box reactivates this often (seconds). */
		public int enrageMotherBoxReactivateSeconds = 75;
		/** Hard ceiling on raid enemies (Parademons) alive at once. */
		public int enemyCap = 34;
		/** Invasion waves before Darkseid arrives (1-5; v0.13.19 default 5). */
		public int invasionWaves = 5;
		/** Extra wave Parademons per participant beyond the first, as a fraction of the base count. */
		public double waveScalingPerExtraPlayer = 0.35;
		/** Base wave compositions (solo counts). */
		public int wave1Standard = 11;
		public int wave2Standard = 8;
		public int wave2Ranged = 7;
		public int wave3Elite = 5;
		public int wave3Brute = 2;
		public int wave3Ranged = 7;
		public int wave3Standard = 6;
		public int wave4Elite = 6;
		public int wave4Brute = 3;
		public int wave4Ranged = 8;
		public int wave4Standard = 6;
		public int wave5Elite = 8;
		public int wave5Brute = 4;
		public int wave5Ranged = 9;
		public int wave5Standard = 6;
		/** Seconds a wave may sit with an unchanged enemy count before stragglers are pulled back in. */
		public int waveStallSeconds = 40;
	}

	/** Darkseid's body. */
	public static final class Boss {
		public double baseDarkseidHealth = 3000.0;
		/** Added for every participant beyond the first (1 = 3000, 2 = 3600 ... 8 = 7200). */
		public double healthPerParticipant = 600.0;
		public double armor = 14.0;
		public double armorToughness = 8.0;
		public double knockbackResistance = 0.95;
		/** Base melee damage (each attack multiplies it). */
		public double meleeDamage = 16.0;
		/** Movement speed per phase (attribute units -- a player walks at 0.1, a zombie at 0.23). */
		public double speedPhase1 = 0.27;
		public double speedPhase2 = 0.31;
		public double speedPhase3 = 0.35;
		/** Damage multipliers per phase. */
		public double damageMultiplierPhase2 = 1.2;
		public double damageMultiplierPhase3 = 1.4;
		/** Cooldown multipliers per phase (below 1 = attacks come faster). */
		public double cooldownMultiplierPhase2 = 0.8;
		public double cooldownMultiplierPhase3 = 0.62;
		/** Knockback multiplier in phase 3. */
		public double knockbackMultiplierPhase3 = 1.35;
		/** Ticks of breathing room between two attacks, phase 1 (scaled by the cooldown multiplier). */
		public int globalCooldownTicks = 19;
		/** Model/hit-box scale (1.0 = 4.2 blocks tall). Read at startup. */
		public double modelScale = 1.0;
		/** Extra damage taken while staggered. */
		public double staggerDamageTakenMultiplier = 1.3;
		/** Damage added per soft-enrage step, and cooldown multiplier per step. */
		public double enrageDamagePerStep = 0.15;
		public double enrageCooldownPerStep = 0.88;
	}

	/** Darkseid's attacks. Damage values are before the phase multiplier and the target's armour. */
	public static final class Abilities {
		// Omega Beams
		public float omegaBeamDamage = 16.0f;
		public int omegaBeamCooldown = 7 * 20;
		/** Weight of the Omega Beams in his attack pick against a grounded / an airborne target (v0.13.18: 4.5 / 6). */
		public double omegaBeamWeight = 7.0;
		public double omegaBeamAirWeight = 8.5;
		/**
		 * v0.13.19: sharp angular legs each beam snakes through before it homes in (0 = the old smooth curve). Each leg
		 * breaks away from the straight line to the target by a random angle, alternating sides; closer than
		 * {@code omegaBeamZigZagStopDistance} blocks the beam stops zig-zagging and homes (still turn-limited).
		 */
		public int omegaBeamZigZagTurns = 4;
		public int omegaBeamZigZagLegTicksMin = 4;
		public int omegaBeamZigZagLegTicksMax = 7;
		public double omegaBeamZigZagAngleMin = 35.0;
		public double omegaBeamZigZagAngleMax = 70.0;
		/** Speed multiplier while zig-zagging, so the detour does not make them arrive late. */
		public double omegaBeamZigZagSpeedMultiplier = 1.3;
		public double omegaBeamZigZagStopDistance = 6.0;
		/** Ticks the two beams keep steering (homing) once their zig-zag is done. */
		public int omegaBeamTrackingTime = 60;
		/** Ticks of warning between the Omega Mark and the beams firing. */
		public int omegaBeamChargeTicks = 32;
		public double omegaBeamSpeed = 0.95;
		/** Maximum steering per tick, degrees, phase 1 (phases 2/3 add to it). */
		public double omegaBeamTurnDegrees = 6.5;

		// Omega Barrage
		public float omegaBarrageDamage = 14.0f;
		public double omegaBarrageRadius = 3.2;
		public int omegaBarrageWarningTicks = 32;
		public int omegaBarrageCooldown = 14 * 20;

		// Godly Ground Slam
		public float groundSlamDamage = 22.0f;
		public double groundSlamRadius = 13.0;
		/** How far above the ground the shockwave still reaches (so flight is not a hard counter). */
		public double groundSlamVerticalRange = 10.0;
		/** Damage multiplier on airborne players caught by it. */
		public double groundSlamAirborneMultiplier = 0.6;
		public int groundSlamCooldown = 12 * 20;

		// Darkseid's Grip
		public int gripDuration = 50;
		/** Total damage over the whole hold. */
		public float gripDamage = 12.0f;
		public double gripRange = 32.0;
		/** Damage (fraction of max health) the team must deal to him during a grip to make him let go. */
		public double gripBreakDamageFraction = 0.02;
		public int gripCooldown = 16 * 20;
		/** A released player cannot be gripped again for this long. */
		public int gripImmunityTicks = 20 * 20;

		// Omega Teleport
		public int teleportCooldown = 9 * 20;
		/** Only targets at least this far away (or high above him) trigger it. */
		public double teleportMinDistance = 20.0;
		/** He never teleports further than this from the arena centre. */
		public double teleportMaxDistance = 96.0;

		// Apokoliptian Charge
		public float chargeDamage = 24.0f;
		public double chargeSpeed = 1.2;
		public double chargeDistance = 28.0;
		public int chargeCooldown = 13 * 20;

		// Boom Tube Reinforcements
		/** v0.13.19: 26 s -> 20 s, and weight 2.0 -> 2.5 (+1 from phase 2) -- he calls tubes more often, still within the cap. */
		public int reinforcementCooldown = 20 * 20;
		public double reinforcementWeight = 2.5;

		// Omega Beam Sweep (phase 2+)
		public float omegaSweepDamage = 11.0f;
		/** Degrees per tick. 4 = one full turn every 4.5 seconds. */
		public double omegaSweepRotationSpeed = 4.0;
		public double omegaSweepLength = 46.0;
		public int omegaSweepCooldown = 24 * 20;

		// Omega Annihilation (phase 3)
		public int omegaAnnihilationInterval = 45 * 20;
		public int omegaAnnihilationChargeTime = 5 * 20;
		public float omegaAnnihilationDamage = 40.0f;
		public double omegaAnnihilationRadius = 48.0;
		/** Damage (fraction of max health) the team must deal during the charge to interrupt it. */
		public double omegaAnnihilationInterruptFraction = 0.04;
		/** How long the interrupted Darkseid stays staggered. */
		public int omegaAnnihilationStaggerTicks = 6 * 20;
	}

	/** The four Mother Boxes that power Darkseid's shield. */
	public static final class MotherBoxes {
		/** Seconds a player must channel next to a box to disable it. */
		public int motherBoxChannelTime = 10;
		/** Blocks the channelling player must stay within. */
		public double channelRange = 4.5;
		/** Seconds an active box can go un-channelled before it overloads. */
		public int motherBoxOverloadTime = 90;
		/** Darkseid heals this fraction of his max health on each overload. */
		public double motherBoxHealAmount = 0.06;
		public float overloadExplosionDamage = 12.0f;
		public double overloadExplosionRadius = 7.0;
		/** Parademons an overload's Boom Tube lets through. */
		public int overloadParademons = 3;
		/** Danger zone left behind by an overload. */
		public int dangerZoneSeconds = 12;
		public double dangerZoneRadius = 5.0;
		public float dangerZoneDamagePerSecond = 4.0f;
		/** Distance of the boxes from the arena centre. */
		public double distanceFromCenter = 24.0;
		/**
		 * v0.13.19: once every box is disabled during Darkseid's phases 1-3, some come back online after a random
		 * {@code fightReactivateMinSeconds}-{@code fightReactivateMaxSeconds} delay (the clock only runs while all four
		 * are dark), with a {@code fightReactivateWarningSeconds} warning first. Each active box takes 25% off the damage
		 * he takes and runs the normal neglect/overload clock until it is disabled again.
		 */
		public boolean fightReactivateEnabled = true;
		public int fightReactivateMinSeconds = 70;
		public int fightReactivateMaxSeconds = 100;
		public int fightReactivateWarningSeconds = 5;
		public int fightReactivateBoxesPhase1 = 1;
		public int fightReactivateBoxesPhase2Min = 1;
		public int fightReactivateBoxesPhase2Max = 2;
		public int fightReactivateBoxesPhase3 = 2;
	}

	/** Parademon stats per variant. */
	public static final class Parademons {
		// v0.13.19: +20% health, +15% damage (v0.13.18: 30/6, 24/5, 60/10, 100/15)
		public double standardHealth = 36.0;
		public double standardDamage = 6.9;
		public double rangedHealth = 28.8;
		public float rangedBoltDamage = 5.75f;
		public double eliteHealth = 72.0;
		public double eliteDamage = 11.5;
		public double bruteHealth = 120.0;
		public double bruteDamage = 17.25;
		/** v0.13.19: the distance gunners circle their target at while strafing and firing. */
		public double rangedPreferredRange = 10.0;
		/** Blocks per tick while flying after an airborne target. */
		public double flightSpeed = 0.42;
	}

	/** Raid loot, per rewarded participant. */
	public static final class Rewards {
		public int omegaCoreCount = 1;
		public double omegaShardChance = 0.60;
		public int omegaShardMin = 1;
		public int omegaShardMax = 3;
		public double motherBoxChance = 0.10;
		public double omegaRelicChance = 0.03;
		public int experiencePoints = 1500;
	}

	private DarkseidConfig() {
	}

	public static Raid raid() {
		return instance.raid;
	}

	public static Boss boss() {
		return instance.boss;
	}

	public static Abilities abilities() {
		return instance.abilities;
	}

	public static MotherBoxes motherBoxes() {
		return instance.motherBoxes;
	}

	public static Parademons parademons() {
		return instance.parademons;
	}

	public static Rewards rewards() {
		return instance.rewards;
	}

	/** Darkseid's max health for {@code participants} official participants. */
	public static double healthFor(int participants) {
		return boss().baseDarkseidHealth + boss().healthPerParticipant * Math.max(0, participants - 1);
	}

	/**
	 * v1 -> v2 (v0.13.19): every value this balance pass changed moves to its new default -- a v1 file on disk would
	 * otherwise silently keep the old numbers (the Behemoth "unbeatable" lesson). Keys new in v2 arrive at their
	 * defaults on their own (Gson builds each section with its constructor); keys this pass did not touch keep
	 * whatever the file says. Package-visible for the gametest.
	 */
	static void migrateToV2(DarkseidConfig c) {
		ProjectHeroMod.LOGGER.info("[ProjectHero] Darkseid Raid config v{} -> v{}: v0.13.19 balance (5 waves, more and tougher"
				+ " Parademons, faster Darkseid, more Omega Beams)", c.configVersion, CONFIG_VERSION);
		if (c.raid != null) {
			Raid d = new Raid();
			c.raid.enemyCap = d.enemyCap;
			c.raid.wave1Standard = d.wave1Standard;
			c.raid.wave2Standard = d.wave2Standard;
			c.raid.wave2Ranged = d.wave2Ranged;
			c.raid.wave3Elite = d.wave3Elite;
			c.raid.wave3Brute = d.wave3Brute;
			c.raid.wave3Ranged = d.wave3Ranged;
			c.raid.wave3Standard = d.wave3Standard;
		}
		if (c.boss != null) {
			c.boss.globalCooldownTicks = new Boss().globalCooldownTicks;
		}
		if (c.abilities != null) {
			Abilities d = new Abilities();
			c.abilities.omegaBeamCooldown = d.omegaBeamCooldown;
			c.abilities.reinforcementCooldown = d.reinforcementCooldown;
		}
		if (c.parademons != null) {
			Parademons d = new Parademons();
			c.parademons.standardHealth = d.standardHealth;
			c.parademons.standardDamage = d.standardDamage;
			c.parademons.rangedHealth = d.rangedHealth;
			c.parademons.rangedBoltDamage = d.rangedBoltDamage;
			c.parademons.eliteHealth = d.eliteHealth;
			c.parademons.eliteDamage = d.eliteDamage;
			c.parademons.bruteHealth = d.bruteHealth;
			c.parademons.bruteDamage = d.bruteDamage;
		}
	}

	/**
	 * Test hook: run the v1 file migration on a JSON string and return the result (nothing is written or installed).
	 */
	public static DarkseidConfig migrateForTest(String json) {
		DarkseidConfig c = new Gson().fromJson(json, DarkseidConfig.class);
		if (c.configVersion != null && c.configVersion < 2) {
			migrateToV2(c);
			c.configVersion = CONFIG_VERSION;
		}
		return c;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("projecthero_darkseid.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				DarkseidConfig loaded = gson.fromJson(Files.readString(path), DarkseidConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			}
			if (instance.configVersion == null) {
				instance = new DarkseidConfig();
				instance.configVersion = CONFIG_VERSION;
			} else if (instance.configVersion < 2) {
				migrateToV2(instance);
				instance.configVersion = CONFIG_VERSION;
			}
			// Sections added by a later version land at their defaults rather than null.
			if (instance.raid == null) {
				instance.raid = new Raid();
			}
			if (instance.boss == null) {
				instance.boss = new Boss();
			}
			if (instance.abilities == null) {
				instance.abilities = new Abilities();
			}
			if (instance.motherBoxes == null) {
				instance.motherBoxes = new MotherBoxes();
			}
			if (instance.parademons == null) {
				instance.parademons = new Parademons();
			}
			if (instance.rewards == null) {
				instance.rewards = new Rewards();
			}
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | JsonSyntaxException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load Darkseid Raid config, using defaults", e);
			instance = new DarkseidConfig();
			instance.configVersion = CONFIG_VERSION;
		}
	}
}
