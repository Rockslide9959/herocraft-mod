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
 * {@code com.projecthero.mod.ironman.ability} / {@link IronManSuitSummonManager} /
 * {@link IronManSuitUpManager} that reads the numbers off this definition.
 */
public final class IronManSuit {
	private final String id;
	private final String nameKey;
	private final int techLevel;
	private final int markNumber;

	private final float energyCapacity;
	private final float energyRecharge;      // vestigial -- IronManEnergy now derives a flat 10-minute
	                                          // full-recharge time from energyCapacity for every suit
	private final float flightEnergyCost;    // per tick while flying
	private final float flightSpeed;
	private final float flightAcceleration;

	private final float repulsorDamage;
	private final float repulsorEnergyCost;   // vestigial -- IronManAbilities.REPULSOR_ENERGY is a flat 50 for every mark now
	private final float unibeamDamage;
	private final float unibeamEnergyCost;    // vestigial -- IronManAbilities.UNIBEAM_ENERGY is a flat 500 for every mark now
	private final float missileDamage;
	private final float missileEnergyCost;
	private final int missileCount;
	private final float damageReduction;   // vestigial -- see IronManDamage's flat 90/10 integrity split
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
	private final float armorRegenPerSecond;     // worn self-repair of integrity (flat integrity/second, per mark; 0 = none, platform only)
	private final float flightDrainMultiplier;   // scales the tiered base flight energy cost (hover/walk/sprint/supersonic) for this mark

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
		this.energyRecharge = b.energyRecharge;
		this.flightEnergyCost = b.flightEnergyCost;
		this.flightSpeed = b.flightSpeed;
		this.flightAcceleration = b.flightAcceleration;
		this.repulsorDamage = b.repulsorDamage;
		this.repulsorEnergyCost = b.repulsorEnergyCost;
		this.unibeamDamage = b.unibeamDamage;
		this.unibeamEnergyCost = b.unibeamEnergyCost;
		this.missileDamage = b.missileDamage;
		this.missileEnergyCost = b.missileEnergyCost;
		this.missileCount = b.missileCount;
		this.damageReduction = b.damageReduction;
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
		this.armorRegenPerSecond = b.armorRegenPerSecond;
		this.flightDrainMultiplier = b.flightDrainMultiplier;
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
	public float energyRecharge() { return energyRecharge; }
	public float flightEnergyCost() { return flightEnergyCost; }
	public float flightSpeed() { return flightSpeed; }
	public float flightAcceleration() { return flightAcceleration; }
	public float repulsorDamage() { return repulsorDamage; }
	public float repulsorEnergyCost() { return repulsorEnergyCost; }
	public float unibeamDamage() { return unibeamDamage; }
	public float unibeamEnergyCost() { return unibeamEnergyCost; }
	public float missileDamage() { return missileDamage; }
	public float missileEnergyCost() { return missileEnergyCost; }
	public int missileCount() { return missileCount; }
	public float damageReduction() { return damageReduction; }
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
	public float armorRegenPerSecond() { return armorRegenPerSecond; }
	public float flightDrainMultiplier() { return flightDrainMultiplier; }
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
		private float energyRecharge = 2.0f;
		private float flightEnergyCost = 3.0f;
		private float flightSpeed = 1.0f;
		private float flightAcceleration = 0.08f;
		private float repulsorDamage = 6.0f;
		private float repulsorEnergyCost = 120f;
		private float unibeamDamage = 18.0f;
		private float unibeamEnergyCost = 1500f;
		private float missileDamage = 8.0f;
		private float missileEnergyCost = 250f;
		private int missileCount = 4;
		private float damageReduction = 0.7f;
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
		private float armorRegenPerSecond = 0.0f;
		private float flightDrainMultiplier = 1.0f;
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
		public Builder energy(float capacity, float recharge, float flightCost) {
			this.energyCapacity = capacity; this.energyRecharge = recharge; this.flightEnergyCost = flightCost; return this;
		}
		public Builder flight(float speed, float acceleration) { this.flightSpeed = speed; this.flightAcceleration = acceleration; return this; }
		public Builder repulsor(float damage, float energyCost) { this.repulsorDamage = damage; this.repulsorEnergyCost = energyCost; return this; }
		public Builder unibeam(float damage, float energyCost) { this.unibeamDamage = damage; this.unibeamEnergyCost = energyCost; return this; }
		public Builder missiles(int count, float damage, float energyCost) {
			this.missileCount = count; this.missileDamage = damage; this.missileEnergyCost = energyCost; return this;
		}
		public Builder damageReduction(float multiplier) { this.damageReduction = multiplier; return this; }
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
		public Builder armorRegen(float perSecond) { this.armorRegenPerSecond = perSecond; return this; }
		/** "changes 18": scales the tiered base flight energy cost (hover 10/s, walk 20/s, sprint 30/s, supersonic 45/s). */
		public Builder flightDrain(float multiplier) { this.flightDrainMultiplier = multiplier; return this; }
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
