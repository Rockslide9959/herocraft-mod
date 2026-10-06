package com.projecthero.mod.ironman.suit;

import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

/**
 * The data-driven definition of one Iron Man suit (spec section 37). Everything that differs between
 * marks lives here so adding Mark I / II / IV / VI / VIII / Hulkbuster / War Machine / ... later is a
 * new {@link IronManSuits} entry and a recipe set, not a new code path.
 *
 * <p>Runtime behaviour (repulsors, Unibeam, flight, missiles, HUD, suit-up, summon) is shared code in
 * {@code com.projecthero.mod.ironman.ability} / {@link IronManSuitCall} /
 * {@link IronManSuitUpManager} that reads the numbers off this definition.
 */
public final class IronManSuit {
	/** v0.15.3, explicit user request: every flying suit (the Mark 1's X burst included) drains a flat 3 energy/sec in the
	 *  air while its worn regen runs at half rate ({@code IronManEnergy#regenPerSecond}). Builder default. */
	public static final float DEFAULT_FLIGHT_DRAIN_PER_SECOND = 3f;
	private final String id;
	private final String nameKey;
	private final int techLevel;
	private final int markNumber;

	private final float energyCapacity;
	private final float flightEnergyCost;    // per tick while flying
	private final float flightSpeed;
	private final float flightAcceleration;

	private final float repulsorDamage;
	private final float unibeamDamage;
	private final float missileDamage;
	private final float missileEnergyCost;
	private final int missileCount;
	private final float strengthBonus;     // ATTACK_DAMAGE added by a full suit (partial = pro-rata)
	private final float maxIntegrity;      // full-condition integrity pool for this mark (default 500)
	private final boolean manualFlight;    // false = no double-tap-jump repulsor flight (Mark 1 flies only via its ability)

	private final float unibeamDamageMultiplier; // scales the shared per-tick Unibeam damage constant
	private final int repulsorWindupTicks;       // 0 = instant tap-fire; >0 = a forced spin-up before it fires
	private final boolean noFlightLean;          // never applies the sprint "superman" body-lean pose
	private final double altitudeCeiling;        // 0 = none; else flight force-cuts and abilities lock out above this Y
	private final float scale;                   // Attributes.SCALE while a full suit is worn (1.0 = normal)
	private final boolean toggleableHighlight;   // V is a real on/off mob-highlight toggle, not Targeting Mode
	private final double targetScanRange;        // how far the mob-highlight / target scan reaches (blocks)
	private final boolean hasWristLaser;         // "changes 15": Mark 4 -- sneak + V fires a one-shot wrist laser
	// "changes 16"
	private final float energyCostMultiplier;    // multiplies every ability / flight energy cost (Mark 6 = cheap)
	private final double maxFlightSpeedMps;      // 0 = uncapped; else horizontal flight speed is clamped to this m/s
	private final boolean fullBodyShield;        // Repulsor Shield covers 360 deg, not just the 180 deg front arc
	private final double passiveHighlightRange;  // > 0 = always-on highlight of ALL nearby entities within this radius
	private final boolean hasWeaponWheel;        // slot 5 (V) opens a weapon wheel that re-binds slot 3 (X)
	// "changes 17"
	private final int airTankSeconds;            // > 0 = a built-in air tank giving this many seconds of underwater breathing
	private final boolean helmetNightVision;     // helmet worn -> Night Vision (every mark except the Mark 1)
	private final float fallDamageFraction;      // fraction of fall damage the wearer still takes with boots on (0 = immune, 0.2 = takes 20%)
	private final double waterMoveMultiplier;    // horizontal movement speed multiplier while in water (1.0 = normal, 0.5 = 50% slower)
	private final boolean ceilingFreeze;         // hitting the altitude ceiling also gives Freeze + a hard 4 s systems lockout (Mark 2)
	private final float flamethrowerHeatMultiplier; // scales the flamethrower heat gauge ceiling (1.5 = 50% bigger bar)
	private final boolean coloredEntityGlow;     // the highlight toggle outlines ALL entities, coloured by type (hostile red / passive blue / player yellow)
	// "changes 18"
	private final float energyRegenPerSecond;    // worn Arc Reactor trickle (flat energy/second, per mark)
	private final float flightDrainMultiplier;   // scales the tiered base flight energy cost (hover/walk/sprint/supersonic) for this mark
	private final float flatFlightDrainPerSecond; // v0.14.30: > 0 replaces the tiered cost with one flat energy/sec for every kind of flight
	// v0.11.12: per-mark Suit Platform regen override (-1 = use the generic 0.1%-of-pool/sec formula).
	// Mark 1's flat 10 energy/sec + 6 integrity/sec is a deliberately different, much faster rate than
	// that formula would give it, so it needs its own explicit numbers rather than a scaled fraction.
	private final float platformEnergyPerSecondOverride;
	private final float platformIntegrityPerSecondOverride;
	// v0.11.13
	private final int resistanceAmplifier;   // -1 = none; else MobEffects.DAMAGE_RESISTANCE amplifier while a full suit is worn+powered

	// v0.14.27 (agent C): per-suit repulsor / dash / clap / unibeam / flamethrower / flight / defence tuning
	private final float repulsorTapEnergy;       // energy a tap Repulsor costs
	private final int repulsorTapCooldownTicks;  // cooldown after a tap Repulsor
	private final float chargedRepulsorDamage;   // < 0 = repulsorDamage x 3
	private final float chargedRepulsorEnergy;
	private final int chargedRepulsorCooldownTicks;
	private final int chargeHoldTicks;           // how long R must be held for a charged shot
	private final float dashDamage;              // > 0 = Shift+R is the repulsor dash (IronManDash)
	private final float dashEnergy;
	private final int dashCooldownTicks;
	private final float sonicClapDamage;         // the SONIC_CLAP slot's numbers (IronManSonicClap)
	private final float sonicClapEnergy;
	private final int sonicClapCooldownTicks;
	private final int unibeamChannelTicks;
	private final float unibeamDamagePerTick;    // < 0 = the shared 10/tick x unibeamDamageMultiplier
	private final float unibeamTotalEnergy;      // < 0 = the shared 700
	private final int unibeamCooldownTicks;
	private final float flamethrowerMaxHeat;     // > 0 overrides the shared 500 x flamethrowerHeatMultiplier ceiling
	private final float flamethrowerHeatPerSecond;
	private final float flamethrowerVentPerSecond;
	private final int flamethrowerVentDelayTicks; // idle time before the heat starts seeping away
	private final float flamethrowerEnergyPerSecond;
	private final float flamethrowerDamagePerSecond;
	private final double flightCruiseMps;        // > 0 = fixed cruise speed in blocks/second
	private final boolean sprintFlight;          // false = sprinting never speeds flight up
	private final double hoverFloor;             // > 0 = during a timed flight burst you can't sink lower than this above the ground
	private final boolean arrowFireImmune;       // arrows + fire do nothing (no health, no integrity)
	private final boolean autoFeed;
	private final boolean waterBreathing;        // the helmet keeps the air meter full underwater, indefinitely
	private final boolean targeting;             // the lock-on / auto-aim targeting system (IronManTargeting)
	private final float mobHighlightEnergy;      // energy to switch the V highlight on
	private final int mobHighlightDurationTicks; // > 0 = the highlight switches itself off after this long

	private final String[] abilities;        // 6, ordered slot 1..6 (ability ids from IronManAbilities)
	private final SuitUpType suitUpType;
	private final SummonType summonType;
	private final Item requiredBlueprint;

	private IronManSuit(Builder b) {
		this.id = b.id;
		this.nameKey = "projecthero.ironman.suit." + b.id + ".name";
		this.techLevel = b.techLevel;
		this.markNumber = b.markNumber;
		this.energyCapacity = b.energyCapacity;
		this.flightEnergyCost = b.flightEnergyCost;
		this.flightSpeed = b.flightSpeed;
		this.flightAcceleration = b.flightAcceleration;
		this.repulsorDamage = b.repulsorDamage;
		this.unibeamDamage = b.unibeamDamage;
		this.missileDamage = b.missileDamage;
		this.missileEnergyCost = b.missileEnergyCost;
		this.missileCount = b.missileCount;
		this.strengthBonus = b.strengthBonus;
		this.maxIntegrity = b.maxIntegrity;
		this.manualFlight = b.manualFlight;
		this.unibeamDamageMultiplier = b.unibeamDamageMultiplier;
		this.repulsorWindupTicks = b.repulsorWindupTicks;
		this.noFlightLean = b.noFlightLean;
		this.altitudeCeiling = b.altitudeCeiling;
		this.scale = b.scale;
		this.toggleableHighlight = b.toggleableHighlight;
		this.targetScanRange = b.targetScanRange;
		this.hasWristLaser = b.hasWristLaser;
		this.energyCostMultiplier = b.energyCostMultiplier;
		this.maxFlightSpeedMps = b.maxFlightSpeedMps;
		this.fullBodyShield = b.fullBodyShield;
		this.passiveHighlightRange = b.passiveHighlightRange;
		this.hasWeaponWheel = b.hasWeaponWheel;
		this.airTankSeconds = b.airTankSeconds;
		this.helmetNightVision = b.helmetNightVision;
		this.fallDamageFraction = b.fallDamageFraction;
		this.waterMoveMultiplier = b.waterMoveMultiplier;
		this.ceilingFreeze = b.ceilingFreeze;
		this.flamethrowerHeatMultiplier = b.flamethrowerHeatMultiplier;
		this.coloredEntityGlow = b.coloredEntityGlow;
		this.energyRegenPerSecond = b.energyRegenPerSecond;
		this.flightDrainMultiplier = b.flightDrainMultiplier;
		this.flatFlightDrainPerSecond = b.flatFlightDrainPerSecond;
		this.platformEnergyPerSecondOverride = b.platformEnergyPerSecondOverride;
		this.platformIntegrityPerSecondOverride = b.platformIntegrityPerSecondOverride;
		this.resistanceAmplifier = b.resistanceAmplifier;
		this.repulsorTapEnergy = b.repulsorTapEnergy;
		this.repulsorTapCooldownTicks = b.repulsorTapCooldownTicks;
		this.chargedRepulsorDamage = b.chargedRepulsorDamage;
		this.chargedRepulsorEnergy = b.chargedRepulsorEnergy;
		this.chargedRepulsorCooldownTicks = b.chargedRepulsorCooldownTicks;
		this.chargeHoldTicks = b.chargeHoldTicks;
		this.dashDamage = b.dashDamage;
		this.dashEnergy = b.dashEnergy;
		this.dashCooldownTicks = b.dashCooldownTicks;
		this.sonicClapDamage = b.sonicClapDamage;
		this.sonicClapEnergy = b.sonicClapEnergy;
		this.sonicClapCooldownTicks = b.sonicClapCooldownTicks;
		this.unibeamChannelTicks = b.unibeamChannelTicks;
		this.unibeamDamagePerTick = b.unibeamDamagePerTick;
		this.unibeamTotalEnergy = b.unibeamTotalEnergy;
		this.unibeamCooldownTicks = b.unibeamCooldownTicks;
		this.flamethrowerMaxHeat = b.flamethrowerMaxHeat;
		this.flamethrowerHeatPerSecond = b.flamethrowerHeatPerSecond;
		this.flamethrowerVentPerSecond = b.flamethrowerVentPerSecond;
		this.flamethrowerVentDelayTicks = b.flamethrowerVentDelayTicks;
		this.flamethrowerEnergyPerSecond = b.flamethrowerEnergyPerSecond;
		this.flamethrowerDamagePerSecond = b.flamethrowerDamagePerSecond;
		this.flightCruiseMps = b.flightCruiseMps;
		this.sprintFlight = b.sprintFlight;
		this.hoverFloor = b.hoverFloor;
		this.arrowFireImmune = b.arrowFireImmune;
		this.autoFeed = b.autoFeed;
		this.waterBreathing = b.waterBreathing;
		this.targeting = b.targeting;
		this.mobHighlightEnergy = b.mobHighlightEnergy;
		this.mobHighlightDurationTicks = b.mobHighlightDurationTicks;
		this.abilities = b.abilities;
		this.suitUpType = b.suitUpType;
		this.summonType = b.summonType;
		this.requiredBlueprint = b.requiredBlueprint;
	}

	public String id() { return id; }
	public String nameKey() { return nameKey; }
	public int techLevel() { return techLevel; }
	public int markNumber() { return markNumber; }
	public float energyCapacity() { return energyCapacity; }
	public float flightEnergyCost() { return flightEnergyCost; }
	public float flightSpeed() { return flightSpeed; }
	public float flightAcceleration() { return flightAcceleration; }
	public float repulsorDamage() { return repulsorDamage; }
	public float unibeamDamage() { return unibeamDamage; }
	public float missileDamage() { return missileDamage; }
	public float missileEnergyCost() { return missileEnergyCost; }
	public int missileCount() { return missileCount; }
	public float strengthBonus() { return strengthBonus; }
	public float maxIntegrity() { return maxIntegrity; }
	public boolean manualFlight() { return manualFlight; }
	public float unibeamDamageMultiplier() { return unibeamDamageMultiplier; }
	public int repulsorWindupTicks() { return repulsorWindupTicks; }
	public boolean noFlightLean() { return noFlightLean; }
	public double altitudeCeiling() { return altitudeCeiling; }
	public float scale() { return scale; }
	public boolean toggleableHighlight() { return toggleableHighlight; }
	public double targetScanRange() { return targetScanRange; }
	public boolean hasWristLaser() { return hasWristLaser; }
	public float energyCostMultiplier() { return energyCostMultiplier; }
	public double maxFlightSpeedMps() { return maxFlightSpeedMps; }
	public boolean fullBodyShield() { return fullBodyShield; }
	public double passiveHighlightRange() { return passiveHighlightRange; }
	public boolean hasWeaponWheel() { return hasWeaponWheel; }
	public int airTankSeconds() { return airTankSeconds; }
	public boolean helmetNightVision() { return helmetNightVision; }
	public float fallDamageFraction() { return fallDamageFraction; }
	public double waterMoveMultiplier() { return waterMoveMultiplier; }
	public boolean ceilingFreeze() { return ceilingFreeze; }
	public float flamethrowerHeatMultiplier() { return flamethrowerHeatMultiplier; }
	public boolean coloredEntityGlow() { return coloredEntityGlow; }
	public float energyRegenPerSecond() { return energyRegenPerSecond; }
	public float flightDrainMultiplier() { return flightDrainMultiplier; }
	public float flatFlightDrainPerSecond() { return flatFlightDrainPerSecond; }
	public float platformEnergyPerSecondOverride() { return platformEnergyPerSecondOverride; }
	public float platformIntegrityPerSecondOverride() { return platformIntegrityPerSecondOverride; }
	public int resistanceAmplifier() { return resistanceAmplifier; }
	public float repulsorTapEnergy() { return repulsorTapEnergy; }
	public int repulsorTapCooldownTicks() { return repulsorTapCooldownTicks; }
	public float chargedRepulsorDamage() { return chargedRepulsorDamage >= 0f ? chargedRepulsorDamage : repulsorDamage * 3.0f; }
	public float chargedRepulsorEnergy() { return chargedRepulsorEnergy; }
	public int chargedRepulsorCooldownTicks() { return chargedRepulsorCooldownTicks; }
	public int chargeHoldTicks() { return chargeHoldTicks; }
	public boolean hasDash() { return dashDamage > 0f; }
	public float dashDamage() { return dashDamage; }
	public float dashEnergy() { return dashEnergy; }
	public int dashCooldownTicks() { return dashCooldownTicks; }
	public float sonicClapDamage() { return sonicClapDamage; }
	public float sonicClapEnergy() { return sonicClapEnergy; }
	public int sonicClapCooldownTicks() { return sonicClapCooldownTicks; }
	public int unibeamChannelTicks() { return unibeamChannelTicks; }
	public float unibeamDamagePerTick() { return unibeamDamagePerTick >= 0f ? unibeamDamagePerTick : 10.0f * unibeamDamageMultiplier; }
	public float unibeamTotalEnergy() { return unibeamTotalEnergy >= 0f ? unibeamTotalEnergy : 700.0f; }
	public int unibeamCooldownTicks() { return unibeamCooldownTicks; }
	public float flamethrowerMaxHeatOverride() { return flamethrowerMaxHeat; }
	public float flamethrowerHeatPerSecond() { return flamethrowerHeatPerSecond; }
	public float flamethrowerVentPerSecond() { return flamethrowerVentPerSecond; }
	public int flamethrowerVentDelayTicks() { return flamethrowerVentDelayTicks; }
	public float flamethrowerEnergyPerSecond() { return flamethrowerEnergyPerSecond; }
	public float flamethrowerDamagePerSecond() { return flamethrowerDamagePerSecond; }
	public double flightCruiseMps() { return flightCruiseMps; }
	public boolean sprintFlight() { return sprintFlight; }
	public double hoverFloor() { return hoverFloor; }
	/** {@code >= 0}: the flat v0.14.27 split -- the wearer takes this share of a hit, integrity absorbs the rest. */
	public boolean arrowFireImmune() { return arrowFireImmune; }
	public boolean autoFeed() { return autoFeed; }
	public boolean waterBreathing() { return waterBreathing; }
	public boolean targeting() { return targeting; }
	public float mobHighlightEnergy() { return mobHighlightEnergy; }
	public int mobHighlightDurationTicks() { return mobHighlightDurationTicks; }
	public SuitUpType suitUpType() { return suitUpType; }
	public SummonType summonType() { return summonType; }
	public Item requiredBlueprint() { return requiredBlueprint; }

	/** Ability id occupying the given slot (1..6), or {@code null} if that slot is unused by this suit. */
	public String abilityInSlot(int slot) {
		return (slot < 1 || slot > 6) ? null : abilities[slot - 1];
	}

	public IronManArmorItem armor(ArmorItem.Type type) {
		return IronManItems.armor(id, type);
	}

	public static final class Builder {
		private final String id;
		private int techLevel = 1;
		private int markNumber = 3;
		private float energyCapacity = 10_000f;
		private float flightEnergyCost = 3.0f;
		private float flightSpeed = 1.0f;
		private float flightAcceleration = 0.08f;
		private float repulsorDamage = 6.0f;
		private float unibeamDamage = 18.0f;
		private float missileDamage = 8.0f;
		private float missileEnergyCost = 250f;
		private int missileCount = 4;
		private float strengthBonus = 7.0f;
		private float maxIntegrity = com.projecthero.mod.ironman.IronManEnergy.MAX_INTEGRITY;
		private boolean manualFlight = true;
		private float unibeamDamageMultiplier = 1.0f;
		private int repulsorWindupTicks = 0;
		private boolean noFlightLean = false;
		private double altitudeCeiling = 0.0;
		private float scale = 1.0f;
		private boolean toggleableHighlight = false;
		private double targetScanRange = 34.0;
		private boolean hasWristLaser = false;
		private float energyCostMultiplier = 1.0f;
		private double maxFlightSpeedMps = 0.0;
		private boolean fullBodyShield = false;
		private double passiveHighlightRange = 0.0;
		private boolean hasWeaponWheel = false;
		private int airTankSeconds = 0;
		private boolean helmetNightVision = true;
		private float fallDamageFraction = 0.0f;
		private double waterMoveMultiplier = 1.0;
		private boolean ceilingFreeze = false;
		private float flamethrowerHeatMultiplier = 1.0f;
		private boolean coloredEntityGlow = false;
		private float energyRegenPerSecond = 1.5f;
		private float flightDrainMultiplier = 1.0f;
		private float flatFlightDrainPerSecond = DEFAULT_FLIGHT_DRAIN_PER_SECOND; // v0.15.3: every mark
		private float platformEnergyPerSecondOverride = -1f;
		private float platformIntegrityPerSecondOverride = -1f;
		private int resistanceAmplifier = -1;
		private float repulsorTapEnergy = 80.0f;
		private int repulsorTapCooldownTicks = 8;
		private float chargedRepulsorDamage = -1f;
		private float chargedRepulsorEnergy = 250.0f;
		private int chargedRepulsorCooldownTicks = 40;
		private int chargeHoldTicks = 40;
		private float dashDamage = 0f;
		private float dashEnergy = 0f;
		private int dashCooldownTicks = 0;
		private float sonicClapDamage = 15f;
		private float sonicClapEnergy = 50f;
		private int sonicClapCooldownTicks = 160;
		private int unibeamChannelTicks = 100;
		private float unibeamDamagePerTick = -1f;
		private float unibeamTotalEnergy = -1f;
		private int unibeamCooldownTicks = 600;
		private float flamethrowerMaxHeat = -1f;
		private float flamethrowerHeatPerSecond = 38.0f;
		private float flamethrowerVentPerSecond = 20.0f;
		private int flamethrowerVentDelayTicks = 0;
		private float flamethrowerEnergyPerSecond = 5.0f;
		private float flamethrowerDamagePerSecond = 0f; // 0 = the legacy 2.5-per-hit stream
		private double flightCruiseMps = 0.0;
		private boolean sprintFlight = true;
		private double hoverFloor = 0.0;
		private boolean arrowFireImmune = false;
		private boolean autoFeed = true;
		private boolean waterBreathing = false;
		private boolean targeting = false;
		private float mobHighlightEnergy = 0f;
		private int mobHighlightDurationTicks = 0;
		private String[] abilities = new String[6];
		private SuitUpType suitUpType = SuitUpType.MECHANICAL_REMOTE;
		private SummonType summonType = SummonType.FLYING_SET;
		private Item requiredBlueprint;

		private Builder(String id) {
			this.id = id;
		}

		public static Builder of(String id) {
			return new Builder(id);
		}

		public Builder tech(int techLevel, int markNumber) { this.techLevel = techLevel; this.markNumber = markNumber; return this; }
		public Builder energy(float capacity, float flightCost) {
			this.energyCapacity = capacity; this.flightEnergyCost = flightCost; return this;
		}
		public Builder flight(float speed, float acceleration) { this.flightSpeed = speed; this.flightAcceleration = acceleration; return this; }
		public Builder repulsor(float damage) { this.repulsorDamage = damage; return this; }
		public Builder unibeam(float damage) { this.unibeamDamage = damage; return this; }
		public Builder missiles(int count, float damage, float energyCost) {
			this.missileCount = count; this.missileDamage = damage; this.missileEnergyCost = energyCost; return this;
		}
		public Builder strength(float attackDamageBonus) { this.strengthBonus = attackDamageBonus; return this; }
		public Builder maxIntegrity(float integrity) { this.maxIntegrity = integrity; return this; }
		/** This suit cannot start repulsor flight from the double-tap-jump gesture (Mark 1: flight ability only). */
		public Builder noManualFlight() { this.manualFlight = false; return this; }
		public Builder unibeamDamageMultiplier(float multiplier) { this.unibeamDamageMultiplier = multiplier; return this; }
		public Builder repulsorWindup(int ticks) { this.repulsorWindupTicks = ticks; return this; }
		public Builder noFlightLean() { this.noFlightLean = true; return this; }
		public Builder altitudeCeiling(double y) { this.altitudeCeiling = y; return this; }
		public Builder scale(float scale) { this.scale = scale; return this; }
		public Builder toggleableHighlight() { this.toggleableHighlight = true; return this; }
		/** How far this mark's mob-highlight / target scan reaches, in blocks (default 34). */
		public Builder targetScanRange(double blocks) { this.targetScanRange = blocks; return this; }
		/** Mark 4 ("changes 15"): sneak + V fires a one-shot wrist laser (V alone still toggles highlight). */
		public Builder wristLaser() { this.hasWristLaser = true; return this; }
		/** "changes 16": scale every ability / flight energy cost (0.5 = half price). */
		public Builder energyCost(float multiplier) { this.energyCostMultiplier = multiplier; return this; }
		/** "changes 16": clamp horizontal flight speed to this many m/s (0 = uncapped). */
		public Builder maxFlightSpeed(double metresPerSecond) { this.maxFlightSpeedMps = metresPerSecond; return this; }
		/** "changes 16": the Repulsor Shield covers the whole body, not just the 180 deg front arc. */
		public Builder fullBodyShield() { this.fullBodyShield = true; return this; }
		/** "changes 16": always outline every living entity within {@code range} blocks (no V toggle). */
		public Builder passiveHighlight(double range) { this.passiveHighlightRange = range; return this; }
		/** "changes 16": slot 5 (V) opens a weapon wheel that re-binds slot 3 (X). */
		public Builder weaponWheel() { this.hasWeaponWheel = true; return this; }
		/** "changes 17": a built-in air tank -- this many seconds of underwater breathing (refills 3x as fast out of water). */
		public Builder airTank(int seconds) { this.airTankSeconds = seconds; return this; }
		/** "changes 17": this mark's helmet does NOT grant Night Vision (Mark 1 only). */
		public Builder noHelmetNightVision() { this.helmetNightVision = false; return this; }
		/** "changes 17": with boots on, the wearer still takes this fraction of fall damage (0 = immune, 0.2 = 80% reduced). */
		public Builder fallDamageFraction(float fraction) { this.fallDamageFraction = fraction; return this; }
		/** "changes 17": horizontal movement is scaled by this while in water (0.5 = 50% slower -- a bulky, heavy suit). */
		public Builder waterMoveMultiplier(double multiplier) { this.waterMoveMultiplier = multiplier; return this; }
		/** "changes 17": hitting the altitude ceiling also freezes the wearer + hard-locks the suit for 4 s (Mark 2). */
		public Builder ceilingFreeze() { this.ceilingFreeze = true; return this; }
		/** "changes 17": scale the flamethrower heat-gauge ceiling (1.5 = a 50% bigger bar / longer burn). */
		public Builder flamethrowerHeat(float multiplier) { this.flamethrowerHeatMultiplier = multiplier; return this; }
		/** "changes 17": the highlight toggle outlines every nearby entity, coloured by type
		 *  (hostile = red, passive = blue, player = yellow) -- Mark 6 &amp; Mark 7. */
		public Builder coloredGlow() { this.coloredEntityGlow = true; return this; }
		/** "changes 18": worn Arc Reactor recharge for this mark, in energy per second. */
		public Builder energyRegen(float perSecond) { this.energyRegenPerSecond = perSecond; return this; }
		/** "changes 18": worn self-repair of integrity for this mark, in points per second (0 = platform only). */
		/** "changes 18": scales the tiered base flight energy cost (hover 10/s, walk 20/s, sprint 30/s, supersonic 45/s). */
		public Builder flightDrain(float multiplier) { this.flightDrainMultiplier = multiplier; return this; }
		/** v0.14.30: one flat energy/sec drain for all flight (hover, moving, sprinting, supersonic). */
		public Builder flatFlightDrain(float perSecond) { this.flatFlightDrainPerSecond = perSecond; return this; }
		/** v0.11.12: this mark's own flat Suit Platform regen rates, overriding the generic
		 *  0.1%-of-pool/sec formula every other mark still uses. */
		public Builder platformRegen(float energyPerSecond, float integrityPerSecond) {
			this.platformEnergyPerSecondOverride = energyPerSecond;
			this.platformIntegrityPerSecondOverride = integrityPerSecond;
			return this;
		}
		/** v0.11.13: while a full suit of this mark is worn and powered, apply a permanent
		 *  {@code MobEffects.DAMAGE_RESISTANCE} at this level (1 = Resistance I). */
		public Builder resistance(int level) { this.resistanceAmplifier = level - 1; return this; }
		/** v0.14.27: a tap Repulsor -- damage, energy and cooldown. */
		public Builder repulsorTap(float damage, float energy, int cooldownTicks) {
			this.repulsorDamage = damage; this.repulsorTapEnergy = energy; this.repulsorTapCooldownTicks = cooldownTicks; return this;
		}
		/** v0.14.27: hold R for {@code holdTicks} then release -- the Charged Repulsor's damage, energy and cooldown. */
		public Builder repulsorCharged(float damage, float energy, int cooldownTicks, int holdTicks) {
			this.chargedRepulsorDamage = damage; this.chargedRepulsorEnergy = energy;
			this.chargedRepulsorCooldownTicks = cooldownTicks; this.chargeHoldTicks = holdTicks; return this;
		}
		/** v0.14.27: Shift+R is the repulsor dash ({@code IronManDash}). */
		public Builder dash(float damage, float energy, int cooldownTicks) {
			this.dashDamage = damage; this.dashEnergy = energy; this.dashCooldownTicks = cooldownTicks; return this;
		}
		/** v0.14.27: the SONIC_CLAP slot's numbers ({@code IronManSonicClap}). */
		public Builder sonicClap(float damage, float energy, int cooldownTicks) {
			this.sonicClapDamage = damage; this.sonicClapEnergy = energy; this.sonicClapCooldownTicks = cooldownTicks; return this;
		}
		/** v0.14.27: the Unibeam channel -- length, damage per damage tick, total energy, cooldown. */
		public Builder unibeamChannel(int ticks, float damagePerTick, float totalEnergy, int cooldownTicks) {
			this.unibeamChannelTicks = ticks; this.unibeamDamagePerTick = damagePerTick;
			this.unibeamTotalEnergy = totalEnergy; this.unibeamCooldownTicks = cooldownTicks; return this;
		}
		/** v0.14.27: the flamethrower's heat bar and burn, all per second. */
		public Builder flamethrowerTuning(float maxHeat, float heatPerSecond, float ventPerSecond, int ventDelayTicks,
				float energyPerSecond, float damagePerSecond) {
			this.flamethrowerMaxHeat = maxHeat; this.flamethrowerHeatPerSecond = heatPerSecond;
			this.flamethrowerVentPerSecond = ventPerSecond; this.flamethrowerVentDelayTicks = ventDelayTicks;
			this.flamethrowerEnergyPerSecond = energyPerSecond; this.flamethrowerDamagePerSecond = damagePerSecond; return this;
		}
		/** v0.14.27: a fixed flight cruise speed (blocks per second). */
		public Builder flightCruise(double blocksPerSecond) { this.flightCruiseMps = blocksPerSecond; return this; }
		/** v0.14.27: sprinting never speeds this suit's flight up. */
		public Builder noSprintFlight() { this.sprintFlight = false; return this; }
		/** v0.14.27: during a timed flight burst the wearer can't sink lower than this many blocks above the ground. */
		public Builder hoverFloor(double blocks) { this.hoverFloor = blocks; return this; }
		/** v0.14.27: flat damage split -- the wearer takes {@code playerShare} of every hit, integrity absorbs the rest. */
		/** v0.14.27: arrows and fire do nothing to the wearer or the suit. */
		public Builder arrowFireImmune() { this.arrowFireImmune = true; return this; }
		/** v0.14.27: this suit does not feed its wearer. */
		public Builder noAutoFeed() { this.autoFeed = false; return this; }
		/** v0.14.27: the helmet lets the wearer breathe underwater indefinitely. */
		public Builder waterBreathing() { this.waterBreathing = true; return this; }
		/** v0.14.27: this suit has the lock-on / auto-aim targeting system. */
		public Builder targeting() { this.targeting = true; return this; }
		/** v0.14.27: the V highlight costs {@code energy} to switch on and (if {@code durationTicks > 0}) switches itself off. */
		public Builder mobHighlight(float energy, int durationTicks) {
			this.mobHighlightEnergy = energy; this.mobHighlightDurationTicks = durationTicks; return this;
		}
		public Builder abilities(String s1, String s2, String s3, String s4, String s5, String s6) {
			this.abilities = new String[] { s1, s2, s3, s4, s5, s6 };
			return this;
		}
		public Builder suitUp(SuitUpType type) { this.suitUpType = type; return this; }
		public Builder summon(SummonType type) { this.summonType = type; return this; }
		public Builder blueprint(Item blueprint) { this.requiredBlueprint = blueprint; return this; }

		public IronManSuit build() {
			return new IronManSuit(this);
		}
	}
}
