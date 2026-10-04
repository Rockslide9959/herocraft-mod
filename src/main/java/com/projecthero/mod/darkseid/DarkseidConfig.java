package com.projecthero.mod.darkseid;

import com.projecthero.mod.config.VersionedConfig;

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
 *
 * <p>v0.13.21: version 3 -- Parademons back to their v0.13.18 strength (the +20% health / +15% damage is gone; the
 * five waves and the gunners' strafing stay). A v2 file's Parademon stats move to the restored defaults.
 */
public final class DarkseidConfig {
	/**
	 * v0.14.21: loaded through the shared {@link VersionedConfig} (see {@code docs/CONFIGS.md}); the steps are the
	 * migrations this file already had, unchanged. A file with no version is reset to today's defaults (as before). v2
	 * (v0.13.19): every value that balance pass changed moves to its new default; keys it did not touch keep whatever
	 * the file says. v3 (v0.13.21): the Parademons go back to their v0.13.18 stats; nothing else changes.
	 */
	private static final String[] PARADEMON_STATS = { "parademons.standardHealth", "parademons.standardDamage",
			"parademons.rangedHealth", "parademons.rangedBoltDamage", "parademons.eliteHealth", "parademons.eliteDamage",
			"parademons.bruteHealth", "parademons.bruteDamage" };

	public static final VersionedConfig<DarkseidConfig> SPEC = VersionedConfig
			.builder(DarkseidConfig.class, "projecthero_darkseid.json", DarkseidConfig::new)
			.balance("boss", "abilities", "motherBoxes", "parademons", "rewards", "raid.enemyCap", "raid.invasionWaves",
					"raid.waveScalingPerExtraPlayer", "raid.wave1Standard", "raid.wave2Standard", "raid.wave2Ranged",
					"raid.wave3Elite", "raid.wave3Brute", "raid.wave3Ranged", "raid.wave3Standard", "raid.wave4Elite",
					"raid.wave4Brute", "raid.wave4Ranged", "raid.wave4Standard", "raid.wave5Elite", "raid.wave5Brute",
					"raid.wave5Ranged", "raid.wave5Standard", "raid.raidSoftEnrageTime", "raid.softEnrageStepSeconds")
			.reset(1, "v0.13.18 first versioned file: a file from before it is reset to today's defaults", "*")
			.reset(2, "v0.13.19 balance (5 waves, more Parademons, faster Darkseid, more Omega Beams)", concat(
					new String[] { "raid.enemyCap", "raid.wave1Standard", "raid.wave2Standard", "raid.wave2Ranged", "raid.wave3Elite",
							"raid.wave3Brute", "raid.wave3Ranged", "raid.wave3Standard", "boss.globalCooldownTicks",
							"abilities.omegaBeamCooldown", "abilities.reinforcementCooldown" }, PARADEMON_STATS))
			.reset(3, "v0.13.21 Parademons back to their original strength", PARADEMON_STATS)
			.build();

	private static String[] concat(String[] a, String[] b) {
		String[] out = java.util.Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

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
		// v0.13.21: back to the v0.13.18 numbers (v0.13.19-20 ran +20% health / +15% damage: 36/6.9, 28.8/5.75,
		// 72/11.5, 120/17.25)
		public double standardHealth = 30.0;
		public double standardDamage = 6.0;
		public double rangedHealth = 24.0;
		public float rangedBoltDamage = 5.0f;
		public double eliteHealth = 60.0;
		public double eliteDamage = 10.0;
		public double bruteHealth = 100.0;
		public double bruteDamage = 15.0;
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
	 * Test hook: run the file migration on a JSON string and return the result (nothing is written or installed).
	 */
	public static DarkseidConfig migrateForTest(String json) {
		return SPEC.fromJson(json);
	}

	public static void load() {
		// migrates, fills in new keys / sections, repairs a corrupt file and always rewrites it
		instance = SPEC.load();
	}
}
