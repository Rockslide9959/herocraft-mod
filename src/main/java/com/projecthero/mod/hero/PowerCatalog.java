package com.projecthero.mod.hero;

import static com.projecthero.mod.hero.AbilityActivation.CHARGE;
import static com.projecthero.mod.hero.AbilityActivation.CYCLE;
import static com.projecthero.mod.hero.AbilityActivation.HOLD;
import static com.projecthero.mod.hero.AbilityActivation.INSTANT;
import static com.projecthero.mod.hero.AbilityActivation.TOGGLE;
import static com.projecthero.mod.hero.AbilitySlot.SLOT_1;
import static com.projecthero.mod.hero.AbilitySlot.SLOT_2;
import static com.projecthero.mod.hero.AbilitySlot.SLOT_3;
import static com.projecthero.mod.hero.AbilitySlot.SLOT_4;
import static com.projecthero.mod.hero.AbilitySlot.SLOT_5;
import static com.projecthero.mod.hero.AbilitySlot.SLOT_6;

import com.projecthero.mod.hero.MutationTrigger.Kind;

/**
 * Builds the data definitions for all 27 experimental powers (spec section 5). This file is
 * <em>data only</em> -- cooldown seconds from the spec are converted to ticks here as starting
 * values (scaled at runtime by {@link HeroConfig#cooldownMultiplier}); ability mechanics live in
 * per-power handler classes registered into {@link AbilityHandlers} by later batches.
 */
final class PowerCatalog {
	private static final int S = 20; // ticks per second

	private PowerCatalog() {
	}

	static void registerAll() {
		Powers.register(superStrength());
		Powers.register(laserVision());
		Powers.register(flight());
		Powers.register(superSpeed());
		Powers.register(geokinesis());
		Powers.register(crystalkinesis());
		Powers.register(electrokinesis());
		Powers.register(pyrokinesis());
		Powers.register(cryokinesis());
		Powers.register(telekinesis());
		Powers.register(teleportation());
		Powers.register(superRegeneration());
		Powers.register(superDurability());
		Powers.register(sonicScream());
		Powers.register(invisibilityLight());
		Powers.register(spiderClimbing());
		Powers.register(elasticity());
		Powers.register(densityManipulation());
		Powers.register(shadowManipulation());
		Powers.register(energyAbsorption());
		Powers.register(shockwaveManipulation());
		Powers.register(plantManipulation());
		Powers.register(gravityManipulation());
		Powers.register(windManipulation());
		Powers.register(waterManipulation());
		Powers.register(magneticManipulation());
		Powers.register(sizeManipulation());
	}

	// ---- helpers ----

	private static Ability ab(String powerKey, String id, AbilitySlot slot, AbilityActivation act, int cooldownTicks) {
		String nameKey = "projecthero.power." + powerKey + ".ability." + id;
		return Ability.of(id, slot, nameKey, act, cooldownTicks);
	}

	private static String pk(String powerKey, String suffix) {
		return "projecthero.power." + powerKey + "." + suffix;
	}

	// ==================================================================================
	// 01 Super Strength
	// ==================================================================================
	private static Power superStrength() {
		String k = "power_01_super_strength";
		return Power.Builder.of(Powers.id(k), PowerCategory.PHYSICAL)
				.ability(ab(k, "ground_slam", SLOT_1, INSTANT, 8 * S))
				.ability(ab(k, "air_punch", SLOT_2, INSTANT, 10 * S))
				.ability(ab(k, "power_leap", SLOT_3, CHARGE, 3 * S))
				.ability(ab(k, "bull_rush", SLOT_4, HOLD, 40 * S))
				.ability(ab(k, "grab_carry", SLOT_5, INSTANT, 5 * S))
				.ability(ab(k, "maximum_effort", SLOT_6, INSTANT, 60 * S))
				.passives(pk(k, "passive.melee"), pk(k, "passive.defense"), pk(k, "passive.jump"),
						pk(k, "passive.mining"), pk(k, "passive.fall"), pk(k, "passive.charged"))
				.serum(SerumRecipe.of("minecraft:strength", pk(k, "serum"),
						"minecraft:iron_nugget", "minecraft:redstone").withFuel("minecraft:coal"))
				.trigger(MutationTrigger.of(Kind.ELECTRICAL_DISCHARGE, pk(k, "trigger"), "projecthero.device.overloaded_redstone_coil"))
				.combos("projecthero.combo.strength_flight", "projecthero.combo.geokinesis_strength")
				.build();
	}

	// ==================================================================================
	// 02 Laser Vision
	// ==================================================================================
	private static Power laserVision() {
		String k = "power_02_laser_vision";
		return Power.Builder.of(Powers.id(k), PowerCategory.ENERGY)
				.ability(ab(k, "heat_vision", SLOT_1, HOLD, 0))
				.ability(ab(k, "focused_beam", SLOT_2, HOLD, 9 * S))
				.ability(ab(k, "heat_burst", SLOT_3, INSTANT, 5 * S))
				.ability(ab(k, "maximum_output", SLOT_4, HOLD, 65 * S))
				.ability(ab(k, "precision_vision", SLOT_5, INSTANT, 1 * S))
				.ability(ab(k, "thermal_vision", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.blindness"), pk(k, "passive.glow"))
				.serum(SerumRecipe.of("minecraft:night_vision", pk(k, "serum"),
						"minecraft:redstone", "minecraft:fire_charge", "minecraft:amethyst_shard"))
				.trigger(MutationTrigger.of(Kind.HIGH_INTENSITY_LIGHT, pk(k, "trigger"), "projecthero.device.light_projector"))
				.combos("projecthero.combo.energy_absorption_laser")
				.build();
	}

	// ==================================================================================
	// 03 Flight
	// ==================================================================================
	private static Power flight() {
		String k = "power_03_flight";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOVEMENT)
				.ability(ab(k, "air_dash", SLOT_1, INSTANT, 3 * S))
				.ability(ab(k, "dive_bomb", SLOT_2, INSTANT, 8 * S))
				.ability(ab(k, "flight_toggle", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "sonic_flight", SLOT_4, INSTANT, 30 * S))
				.ability(ab(k, "carry", SLOT_5, TOGGLE, 0))
				.ability(ab(k, "aerial_burst", SLOT_6, INSTANT, 6 * S))
				.passives(pk(k, "passive.air_control"), pk(k, "passive.fall"))
				.serum(SerumRecipe.of("minecraft:slow_falling", pk(k, "serum"),
						"minecraft:feather", "minecraft:rabbit_foot", "minecraft:amethyst_shard"))
				.trigger(MutationTrigger.of(Kind.GRAVITY_DISTORTION, pk(k, "trigger"), "projecthero.device.gravity_plate"))
				.combos("projecthero.combo.strength_flight", "projecthero.combo.pyrokinesis_flight")
				.build();
	}

	// ==================================================================================
	// 04 Super Speed
	// ==================================================================================
	private static Power superSpeed() {
		String k = "power_04_super_speed";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOVEMENT)
				.ability(ab(k, "speed_carry", SLOT_1, INSTANT, 3 * S))
				.ability(ab(k, "rapid_assault", SLOT_2, INSTANT, 0))
				.ability(ab(k, "momentum_dash", SLOT_3, INSTANT, 2 * S))
				.ability(ab(k, "overdrive", SLOT_4, INSTANT, 50 * S))
				.ability(ab(k, "whirlwind", SLOT_5, HOLD, 12 * S))
				.ability(ab(k, "speed_mode", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.sprint"), pk(k, "passive.step"), pk(k, "passive.collision"))
				.serum(SerumRecipe.of("minecraft:swiftness", pk(k, "serum"),
						"minecraft:sugar", "minecraft:rabbit_foot", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.ELECTRICAL_DISCHARGE, pk(k, "trigger"), "projecthero.device.charged_copper_plates"))
				.combos("projecthero.combo.speed_electrokinesis")
				.build();
	}

	// ==================================================================================
	// 05 Geokinesis
	// ==================================================================================
	private static Power geokinesis() {
		String k = "power_05_geokinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.ELEMENTAL)
				.ability(ab(k, "rock_shot", SLOT_1, INSTANT, 2 * S))
				.ability(ab(k, "earth_spike", SLOT_2, INSTANT, 6 * S))
				.ability(ab(k, "stone_wall", SLOT_3, INSTANT, 8 * S))
				.ability(ab(k, "earthquake", SLOT_4, HOLD, 65 * S))
				.ability(ab(k, "boulder_lift", SLOT_5, INSTANT, 8 * S))
				.ability(ab(k, "earth_armor", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.mining"))
				.serum(SerumRecipe.of("minecraft:weakness", pk(k, "serum"), "minecraft:amethyst_shard")
						.withFuel("minecraft:lapis_lazuli"))
				.trigger(MutationTrigger.of(Kind.GEOLOGICAL_RESONANCE, pk(k, "trigger"), "projecthero.device.resonance_chamber"))
				.combos("projecthero.combo.geokinesis_strength")
				.build();
	}

	// ==================================================================================
	// 06 Crystalkinesis
	// ==================================================================================
	private static Power crystalkinesis() {
		String k = "power_06_crystalkinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.ELEMENTAL)
				.ability(ab(k, "crystal_shard", SLOT_1, INSTANT, 2 * S))
				.ability(ab(k, "crystal_spikes", SLOT_2, INSTANT, 6 * S))
				.ability(ab(k, "crystal_barrier", SLOT_3, INSTANT, 8 * S))
				.ability(ab(k, "crystal_eruption", SLOT_4, HOLD, 70 * S))
				.ability(ab(k, "crystal_prison", SLOT_5, INSTANT, 12 * S))
				.ability(ab(k, "crystal_armor", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.crystal_immunity"))
				.serum(SerumRecipe.of("minecraft:weakness", pk(k, "serum"),
						"minecraft:amethyst_shard", "minecraft:glowstone_dust").withFuel("minecraft:lapis_lazuli"))
				.trigger(MutationTrigger.of(Kind.AMETHYST_GEODE, pk(k, "trigger"), "projecthero.device.crystal_chamber"))
				.combos("projecthero.combo.cryokinesis_water")
				.build();
	}

	// ==================================================================================
	// 07 Electrokinesis
	// ==================================================================================
	private static Power electrokinesis() {
		String k = "power_07_electrokinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.ENERGY)
				.ability(ab(k, "electric_bolt", SLOT_1, INSTANT, 2 * S))
				.ability(ab(k, "chain_lightning", SLOT_2, INSTANT, 10 * S))
				.ability(ab(k, "electric_dash", SLOT_3, INSTANT, 4 * S))
				.ability(ab(k, "electrical_storm", SLOT_4, HOLD, 65 * S))
				.ability(ab(k, "electromagnetic_pull", SLOT_5, INSTANT, 7 * S))
				.ability(ab(k, "charged_mode", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.lightning_resist"), pk(k, "passive.redstone"))
				.serum(SerumRecipe.of("minecraft:swiftness", pk(k, "serum"),
						"minecraft:copper_ingot", "minecraft:redstone", "minecraft:glowstone_dust"))
				.trigger(MutationTrigger.of(Kind.ELECTRICAL_DISCHARGE, pk(k, "trigger"), "projecthero.device.overloaded_redstone_coil"))
				.combos("projecthero.combo.speed_electrokinesis", "projecthero.combo.water_electrokinesis")
				.build();
	}

	// ==================================================================================
	// 08 Pyrokinesis
	// ==================================================================================
	private static Power pyrokinesis() {
		String k = "power_08_pyrokinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.ELEMENTAL)
				.ability(ab(k, "fireball", SLOT_1, INSTANT, 2 * S))
				.ability(ab(k, "flamethrower", SLOT_2, HOLD, 0))
				.ability(ab(k, "flame_dash", SLOT_3, INSTANT, 4 * S))
				.ability(ab(k, "inferno", SLOT_4, HOLD, 55 * S))
				.ability(ab(k, "flame_wall", SLOT_5, INSTANT, 1 * S))
				.ability(ab(k, "flame_body", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.fire_resist"))
				.serum(SerumRecipe.of("minecraft:fire_resistance", pk(k, "serum"),
						"minecraft:fire_charge", "minecraft:charcoal", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.FIRE_EXPOSURE, pk(k, "trigger"), null))
				.combos("projecthero.combo.pyrokinesis_flight", "projecthero.combo.wind_fire")
				.build();
	}

	// ==================================================================================
	// 09 Cryokinesis
	// ==================================================================================
	private static Power cryokinesis() {
		String k = "power_09_cryokinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.ELEMENTAL)
				.ability(ab(k, "ice_bolt", SLOT_1, HOLD, 2 * S))
				.ability(ab(k, "freeze_beam", SLOT_2, HOLD, 0))
				.ability(ab(k, "ice_slide", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "absolute_zero", SLOT_4, HOLD, 45 * S))
				.ability(ab(k, "ice_wall", SLOT_5, INSTANT, 8 * S))
				.ability(ab(k, "frozen_armor", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.powder_snow"), pk(k, "passive.freeze_resist"))
				.serum(SerumRecipe.of("minecraft:slowness", pk(k, "serum"),
						"minecraft:snowball", "minecraft:ice", "minecraft:lapis_lazuli"))
				.trigger(MutationTrigger.of(Kind.POWDER_SNOW, pk(k, "trigger"), null))
				.combos("projecthero.combo.cryokinesis_water")
				.build();
	}

	// ==================================================================================
	// 10 Telekinesis
	// ==================================================================================
	private static Power telekinesis() {
		String k = "power_10_telekinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.MENTAL)
				// v0.10.10: Force Pull moved onto R's sneak variant, freeing G for the Telekinetic Barrier;
				// both halves of R are down to a 1 s cooldown, and the ultimate is a 5 s hold on 90 s.
				.ability(ab(k, "force_push", SLOT_1, INSTANT, 1 * S))
				.ability(ab(k, "telekinetic_barrier", SLOT_2, TOGGLE, 0))
				.ability(ab(k, "psychic_flight", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "telekinetic_explosion", SLOT_4, HOLD, 90 * S))
				.ability(ab(k, "telekinetic_grab", SLOT_5, INSTANT, 5 * S))
				.ability(ab(k, "block_manipulation", SLOT_6, HOLD, 0))
				.passives(pk(k, "passive.psi"), pk(k, "passive.fall"), pk(k, "passive.item_drift"))
				.serum(SerumRecipe.of("minecraft:slow_falling", pk(k, "serum"),
						"minecraft:ender_pearl", "minecraft:amethyst_shard", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.PSIONIC_RESONANCE, pk(k, "trigger"), "projecthero.device.enchanting_resonance"))
				.combos("projecthero.combo.shadow_teleportation")
				.build();
	}

	// ==================================================================================
	// 11 Teleportation
	// ==================================================================================
	private static Power teleportation() {
		String k = "power_11_teleportation";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOVEMENT)
				.ability(ab(k, "blink", SLOT_1, HOLD, 3 * S))
				.ability(ab(k, "target_teleport", SLOT_2, INSTANT, 7 * S))
				.ability(ab(k, "escape_blink", SLOT_3, INSTANT, 4 * S))
				.ability(ab(k, "portal", SLOT_4, CHARGE, 60 * S))
				.ability(ab(k, "teleport_mark", SLOT_5, INSTANT, 20 * S))
				.ability(ab(k, "portal_anchor", SLOT_6, INSTANT, 3 * S))
				.passives(pk(k, "passive.pearl_resist"))
				.serum(SerumRecipe.of("minecraft:swiftness", pk(k, "serum"),
						"minecraft:ender_pearl", "minecraft:amethyst_shard", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.ENDER_PEARL, pk(k, "trigger"), null))
				.combos("projecthero.combo.shadow_teleportation")
				.build();
	}

	// ==================================================================================
	// 12 Super Regeneration (renamed from "Healing Factor" in v0.9.22)
	// ==================================================================================
	private static Power superRegeneration() {
		String k = "power_12_super_regeneration";
		return Power.Builder.of(Powers.id(k), PowerCategory.PHYSICAL)
				.ability(ab(k, "rapid_heal", SLOT_1, INSTANT, 6 * S))
				.ability(ab(k, "purge", SLOT_2, INSTANT, 12 * S))
				.ability(ab(k, "recovery_burst", SLOT_3, INSTANT, 15 * S))
				.ability(ab(k, "resurrection", SLOT_4, INSTANT, 60 * S))
				.ability(ab(k, "cellular_surge", SLOT_5, INSTANT, 45 * S))
				.ability(ab(k, "regeneration_mode", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.regen"), pk(k, "passive.debuff"))
				.serum(SerumRecipe.of("minecraft:regeneration", pk(k, "serum"),
						"minecraft:golden_apple", "minecraft:spider_eye", "minecraft:bone_meal"))
				.trigger(MutationTrigger.of(Kind.NEAR_DEATH, pk(k, "trigger"), null))
				.combos("projecthero.combo.speedster_set")
				.build();
	}

	// ==================================================================================
	// 13 Super Durability
	// ==================================================================================
	private static Power superDurability() {
		String k = "power_13_super_durability";
		return Power.Builder.of(Powers.id(k), PowerCategory.PHYSICAL)
				.ability(ab(k, "heavy_strike", SLOT_1, INSTANT, 3 * S))
				.ability(ab(k, "shoulder_charge", SLOT_2, INSTANT, 8 * S))
				.ability(ab(k, "block", SLOT_3, HOLD, 0))
				.ability(ab(k, "unbreakable", SLOT_4, INSTANT, 50 * S))
				.ability(ab(k, "projectile_deflection", SLOT_5, HOLD, 0))
				.ability(ab(k, "tank_mode", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.resist"), pk(k, "passive.health"))
				.serum(SerumRecipe.of("minecraft:awkward", pk(k, "serum"),
						"minecraft:iron_ingot", "minecraft:flint", "minecraft:leather"))
				.trigger(MutationTrigger.of(Kind.EXPLOSION, pk(k, "trigger"), "projecthero.device.blast_chamber"))
				.combos("projecthero.combo.durability_size", "projecthero.combo.flying_brick_set")
				.build();
	}

	// ==================================================================================
	// 14 Sonic Scream
	// ==================================================================================
	private static Power sonicScream() {
		String k = "power_14_sonic_scream";
		return Power.Builder.of(Powers.id(k), PowerCategory.ENERGY)
				.ability(ab(k, "sonic_blast", SLOT_1, INSTANT, 5 * S))
				.ability(ab(k, "focused_scream", SLOT_2, INSTANT, 10 * S))
				.ability(ab(k, "sonic_jump", SLOT_3, INSTANT, 5 * S))
				.ability(ab(k, "supersonic_scream", SLOT_4, HOLD, 70 * S))
				.ability(ab(k, "resonance", SLOT_5, INSTANT, 5 * S))
				.ability(ab(k, "echolocation", SLOT_6, TOGGLE, 20 * S))
				.passives(pk(k, "passive.self_resist"))
				.serum(SerumRecipe.of("minecraft:strength", pk(k, "serum"),
						"minecraft:goat_horn", "minecraft:note_block", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.RESONANT_HORN, pk(k, "trigger"), "projecthero.device.resonant_chamber"))
				.combos("projecthero.combo.crystal_hero_set")
				.build();
	}

	// ==================================================================================
	// 15 Invisibility / Light Manipulation
	// ==================================================================================
	private static Power invisibilityLight() {
		String k = "power_15_invisibility_light_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.LIGHT)
				.ability(ab(k, "light_blast", SLOT_1, INSTANT, 3 * S))
				.ability(ab(k, "flash", SLOT_2, INSTANT, 10 * S))
				.ability(ab(k, "mirage_dash", SLOT_3, HOLD, 3 * S))
				.ability(ab(k, "perfect_cloak", SLOT_4, INSTANT, 45 * S))
				.ability(ab(k, "decoy", SLOT_5, INSTANT, 18 * S))
				.ability(ab(k, "cloaking_toggle", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.detection"))
				.serum(SerumRecipe.of("minecraft:invisibility", pk(k, "serum"),
						"minecraft:glass", "minecraft:amethyst_shard", "minecraft:glow_ink_sac"))
				.trigger(MutationTrigger.of(Kind.DIRECT_SUNLIGHT, pk(k, "trigger"), null))
				.combos("projecthero.combo.psychic_set")
				.build();
	}

	// ==================================================================================
	// 16 Spider Climbing / Adhesion
	// ==================================================================================
	private static Power spiderClimbing() {
		String k = "power_16_spider_climbing_adhesion";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOVEMENT)
				.ability(ab(k, "adhesive_strike", SLOT_1, INSTANT, 4 * S))
				.ability(ab(k, "pounce", SLOT_2, INSTANT, 5 * S))
				.ability(ab(k, "wall_leap", SLOT_3, INSTANT, 3 * S))
				.ability(ab(k, "predator_rush", SLOT_4, INSTANT, 35 * S))
				.ability(ab(k, "wall_grip", SLOT_5, TOGGLE, 0))
				.ability(ab(k, "adhesion_mode", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.fall"), pk(k, "passive.jump"), pk(k, "passive.evolve"))
				.serum(SerumRecipe.of("minecraft:leaping", pk(k, "serum"),
						"minecraft:spider_eye", "minecraft:string", "minecraft:slime_ball"))
				.trigger(MutationTrigger.of(Kind.SPIDER_VENOM, pk(k, "trigger"), null))
				.build();
	}

	// ==================================================================================
	// 17 Elasticity
	// ==================================================================================
	private static Power elasticity() {
		String k = "power_17_elasticity";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOLECULAR)
				.ability(ab(k, "stretch_punch", SLOT_1, CHARGE, 2 * S))
				.ability(ab(k, "double_fist_slam", SLOT_2, INSTANT, 7 * S))
				.ability(ab(k, "slingshot", SLOT_3, INSTANT, 3 * S))
				.ability(ab(k, "giant_hammer_fist", SLOT_4, INSTANT, 35 * S))
				.ability(ab(k, "elastic_grab", SLOT_5, INSTANT, 6 * S))
				.ability(ab(k, "elastic_form", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.fall"), pk(k, "passive.bounce"), pk(k, "passive.squeeze"),
						pk(k, "passive.melee"), pk(k, "passive.knockback"))
				.serum(SerumRecipe.of("minecraft:leaping", pk(k, "serum"),
						"minecraft:slime_ball", "minecraft:string", "minecraft:rabbit_hide"))
				.trigger(MutationTrigger.of(Kind.SLIME_IMPACT, pk(k, "trigger"), null))
				.build();
	}

	// ==================================================================================
	// 18 Density Manipulation
	// ==================================================================================
	private static Power densityManipulation() {
		String k = "power_18_density_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOLECULAR)
				.ability(ab(k, "increase_density", SLOT_1, INSTANT, 0))
				.ability(ab(k, "decrease_density", SLOT_2, INSTANT, 0))
				.ability(ab(k, "density_anchor", SLOT_3, INSTANT, 30 * S))
				.ability(ab(k, "heavy_impact", SLOT_4, INSTANT, 10 * S))
				.ability(ab(k, "phase", SLOT_5, TOGGLE, 0))
				.ability(ab(k, "zero_density", SLOT_6, INSTANT, 2 * S))
				.passives(pk(k, "passive.mode"))
				.serum(SerumRecipe.of("minecraft:slow_falling", pk(k, "serum"),
						"minecraft:iron_ingot", "minecraft:feather", "minecraft:amethyst_shard"))
				.trigger(MutationTrigger.of(Kind.MOLECULAR_COMPRESSION, pk(k, "trigger"), "projecthero.device.compression_chamber"))
				.combos("projecthero.combo.durability_size")
				.build();
	}

	// ==================================================================================
	// 19 Shadow Manipulation
	// ==================================================================================
	private static Power shadowManipulation() {
		String k = "power_19_shadow_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.ENERGY)
				.ability(ab(k, "shadow_bolt", SLOT_1, INSTANT, 2 * S))
				.ability(ab(k, "shadow_tendrils", SLOT_2, INSTANT, 8 * S))
				.ability(ab(k, "shadow_step", SLOT_3, INSTANT, 4 * S))
				.ability(ab(k, "total_darkness", SLOT_4, INSTANT, 50 * S))
				.ability(ab(k, "shadow_clone", SLOT_5, INSTANT, 12 * S))
				.ability(ab(k, "shadow_form", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.darkness_regen"))
				.serum(SerumRecipe.of("minecraft:night_vision", pk(k, "serum"),
						"minecraft:ink_sac", "minecraft:coal", "minecraft:fermented_spider_eye"))
				.trigger(MutationTrigger.of(Kind.TRUE_DARKNESS, pk(k, "trigger"), null))
				.combos("projecthero.combo.shadow_teleportation")
				.build();
	}

	// ==================================================================================
	// 20 Energy Absorption
	// ==================================================================================
	private static Power energyAbsorption() {
		String k = "power_20_energy_absorption";
		return Power.Builder.of(Powers.id(k), PowerCategory.ENERGY)
				.ability(ab(k, "energy_blast", SLOT_1, INSTANT, 0))
				.ability(ab(k, "energy_beam", SLOT_2, HOLD, 0))
				.ability(ab(k, "absorption_shield", SLOT_3, INSTANT, 3 * S))
				.ability(ab(k, "overload", SLOT_4, INSTANT, 35 * S))
				.ability(ab(k, "energy_drain", SLOT_5, HOLD, 0))
				.ability(ab(k, "absorption_mode", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.meter"))
				.serum(SerumRecipe.of("minecraft:fire_resistance", pk(k, "serum"),
						"minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:amethyst_shard"))
				.trigger(MutationTrigger.of(Kind.ENERGY_OVERLOAD, pk(k, "trigger"), null))
				.combos("projecthero.combo.energy_absorption_laser")
				.build();
	}

	// ==================================================================================
	// 21 Shockwave Manipulation
	// ==================================================================================
	private static Power shockwaveManipulation() {
		String k = "power_21_shockwave_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.KINETIC)
				.ability(ab(k, "shockwave_punch", SLOT_1, INSTANT, 2 * S))
				.ability(ab(k, "ground_wave", SLOT_2, INSTANT, 6 * S))
				.ability(ab(k, "recoil_jump", SLOT_3, INSTANT, 4 * S))
				.ability(ab(k, "kinetic_detonation", SLOT_4, INSTANT, 35 * S))
				.ability(ab(k, "repulsion_field", SLOT_5, HOLD, 0))
				.ability(ab(k, "charge", SLOT_6, CHARGE, 0))
				.passives(pk(k, "passive.knockback_resist"))
				.serum(SerumRecipe.of("minecraft:strength", pk(k, "serum"),
						"minecraft:gunpowder", "minecraft:clay_ball", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.EXPLOSION, pk(k, "trigger"), "projecthero.device.blast_chamber"))
				.combos("projecthero.combo.crystal_hero_set")
				.build();
	}

	// ==================================================================================
	// 22 Plant Manipulation / Chlorokinesis
	// ==================================================================================
	private static Power plantManipulation() {
		String k = "power_22_plant_manipulation_chlorokinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.NATURE)
				.ability(ab(k, "thorn_shot", SLOT_1, INSTANT, 2 * S))
				.ability(ab(k, "vine_grab", SLOT_2, INSTANT, 7 * S))
				.ability(ab(k, "vine_swing", SLOT_3, INSTANT, 1 * S))
				.ability(ab(k, "overgrowth", SLOT_4, INSTANT, 40 * S))
				.ability(ab(k, "living_wall", SLOT_5, INSTANT, 8 * S))
				.ability(ab(k, "natures_blessing", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.bonemeal"), pk(k, "passive.lush_regen"))
				.serum(SerumRecipe.of("minecraft:regeneration", pk(k, "serum"),
						"minecraft:moss_block", "minecraft:vine", "minecraft:bone_meal"))
				.trigger(MutationTrigger.of(Kind.PLANT_SURROUNDINGS, pk(k, "trigger"), null))
				.build();
	}

	// ==================================================================================
	// 23 Gravity Manipulation
	// ==================================================================================
	private static Power gravityManipulation() {
		String k = "power_23_gravity_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.FORCE)
				.ability(ab(k, "gravity_push", SLOT_1, INSTANT, 3 * S))
				.ability(ab(k, "gravity_crush", SLOT_2, INSTANT, 9 * S))
				.ability(ab(k, "zero_g", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "gravity_well", SLOT_4, INSTANT, 45 * S))
				.ability(ab(k, "levitate", SLOT_5, INSTANT, 6 * S))
				.ability(ab(k, "gravity_field", SLOT_6, CYCLE, 0))
				.passives(pk(k, "passive.low_g_fall"))
				.serum(SerumRecipe.of("minecraft:slow_falling", pk(k, "serum"),
						"minecraft:compass", "minecraft:iron_nugget", "minecraft:amethyst_shard", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.GRAVITY_DISTORTION, pk(k, "trigger"), "projecthero.device.gravity_distortion_rig"))
				.build();
	}

	// ==================================================================================
	// 24 Wind Manipulation
	// ==================================================================================
	private static Power windManipulation() {
		String k = "power_24_wind_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.ELEMENTAL)
				.ability(ab(k, "wind_blade", SLOT_1, HOLD, 2 * S))
				.ability(ab(k, "tornado", SLOT_2, INSTANT, 10 * S))
				.ability(ab(k, "wind_flight", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "hurricane", SLOT_4, HOLD, 60 * S))
				.ability(ab(k, "wind_push", SLOT_5, INSTANT, 4 * S))
				.ability(ab(k, "tailwind", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.fall"))
				.serum(SerumRecipe.of("minecraft:swiftness", pk(k, "serum"),
						"minecraft:feather", "minecraft:paper", "minecraft:string", "minecraft:sugar"))
				.trigger(MutationTrigger.of(Kind.PRESSURE_CHAMBER, pk(k, "trigger"), "projecthero.device.pressure_chamber"))
				.combos("projecthero.combo.wind_fire")
				.build();
	}

	// ==================================================================================
	// 25 Water Manipulation
	// ==================================================================================
	private static Power waterManipulation() {
		String k = "power_25_water_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.ELEMENTAL)
				.ability(ab(k, "water_shot", SLOT_1, HOLD, 2 * S))
				.ability(ab(k, "water_whip", SLOT_2, INSTANT, 6 * S))
				.ability(ab(k, "riptide", SLOT_3, INSTANT, 6 * S))
				.ability(ab(k, "tidal_wave", SLOT_4, HOLD, 45 * S))
				.ability(ab(k, "water_prison", SLOT_5, HOLD, 12 * S))
				.ability(ab(k, "aquatic_form", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.swimming"), pk(k, "passive.no_drown"))
				.serum(SerumRecipe.of("minecraft:water_breathing", pk(k, "serum"),
						"minecraft:kelp", "minecraft:clay_ball", "minecraft:lapis_lazuli"))
				.trigger(MutationTrigger.of(Kind.SUBMERSION, pk(k, "trigger"), "projecthero.device.hydrostatic_tank"))
				.combos("projecthero.combo.water_electrokinesis", "projecthero.combo.cryokinesis_water")
				.build();
	}

	// ==================================================================================
	// 26 Magnetic Manipulation
	// ==================================================================================
	private static Power magneticManipulation() {
		String k = "power_26_magnetic_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.FORCE)
				.ability(ab(k, "ferrous_shot", SLOT_1, INSTANT, 3 * S))
				.ability(ab(k, "magnetic_grip", SLOT_2, INSTANT, 0))
				.ability(ab(k, "polarity_leap", SLOT_3, INSTANT, 2 * S))
				.ability(ab(k, "metal_storm", SLOT_4, INSTANT, 18 * S))
				.ability(ab(k, "magnetic_crush", SLOT_5, INSTANT, 9 * S))
				.ability(ab(k, "magnetic_sense", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.metal_attraction"), pk(k, "passive.projectile_immunity"))
				.serum(SerumRecipe.of("minecraft:swiftness", pk(k, "serum"),
						"minecraft:iron_nugget", "minecraft:copper_ingot", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.MAGNETIC_FIELD, pk(k, "trigger"), "projecthero.device.electromagnetic_coil_pair"))
				.build();
	}

	// ==================================================================================
	// 27 Size Manipulation
	// ==================================================================================
	private static Power sizeManipulation() {
		String k = "power_27_size_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOLECULAR)
				.ability(ab(k, "giant_punch", SLOT_1, INSTANT, 3 * S))
				.ability(ab(k, "stomp", SLOT_2, INSTANT, 8 * S))
				.ability(ab(k, "shrink", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "giant_form", SLOT_4, TOGGLE, 0))
				.ability(ab(k, "tiny_dash", SLOT_5, INSTANT, 5 * S))
				.ability(ab(k, "large_form", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.form"), pk(k, "passive.fall"))
				.serum(SerumRecipe.of("minecraft:leaping", pk(k, "serum"),
						"minecraft:slime_ball", "minecraft:rabbit_hide", "minecraft:fermented_spider_eye", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.MASS_COMPRESSION, pk(k, "trigger"), "projecthero.device.mass_compression_chamber"))
				.combos("projecthero.combo.durability_size")
				.build();
	}
}
