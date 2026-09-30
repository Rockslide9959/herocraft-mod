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
 * Builds the data definitions for all the experimental powers (26 since Super Durability was removed in v0.14.5) (spec section 5). This file is
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
				// v0.13.22 revamp: throw anything + hero landings
				.ability(ab(k, "haymaker", SLOT_1, INSTANT, 102))
				.ability(ab(k, "ground_slam", SLOT_2, INSTANT, 136))
				.ability(ab(k, "power_leap", SLOT_3, CHARGE, 51))
				.ability(ab(k, "maximum_effort", SLOT_4, INSTANT, 1020))
				.ability(ab(k, "grab_throw", SLOT_5, INSTANT, 68))
				.ability(ab(k, "bull_rush", SLOT_6, HOLD, 680))
				.ability(ab(k, "thunderclap", AbilitySlot.SLOT_7, INSTANT, 160))
				.ability(ab(k, "rip_hurl", AbilitySlot.SLOT_8, INSTANT, 187))
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
				// v0.14.5 rework: six keys (no H / N), a 0-100 heat gauge, 100-block beams drawn as models.
				// heat_vision's cooldown is Shift+R Piercing Blast's (set by the handler), shown on the R box.
				.ability(ab(k, "heat_vision", SLOT_1, HOLD, 0))
				.ability(ab(k, "sweeping_arc", SLOT_2, INSTANT, 170))
				.ability(ab(k, "recoil_blast", SLOT_3, INSTANT, 85))
				.ability(ab(k, "maximum_output", SLOT_4, INSTANT, 1100))
				.ability(ab(k, "ignite", SLOT_5, INSTANT, 10))
				.ability(ab(k, "thermal_vision", SLOT_6, TOGGLE, 0))
				.passives(pk(k, "passive.heat"))
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
				// v0.13.22 revamp: speed tiers + the sonic boom
				.ability(ab(k, "air_dash", SLOT_1, INSTANT, 51))
				.ability(ab(k, "dive_bomb", SLOT_2, INSTANT, 136))
				.ability(ab(k, "flight_toggle", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "orbital_drop", SLOT_4, INSTANT, 900))
				.ability(ab(k, "carry", SLOT_5, TOGGLE, 0))
				.ability(ab(k, "sonic_flight", SLOT_6, INSTANT, 510))
				.ability(ab(k, "slipstream", AbilitySlot.SLOT_7, INSTANT, 320))
				.ability(ab(k, "barrel_roll", AbilitySlot.SLOT_8, INSTANT, 70))
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
				// v0.13.22 revamp: momentum builds while you run
				.ability(ab(k, "rapid_assault", SLOT_1, INSTANT, 30))
				.ability(ab(k, "speed_carry", SLOT_2, INSTANT, 51))
				.ability(ab(k, "momentum_dash", SLOT_3, INSTANT, 34))
				.ability(ab(k, "overdrive", SLOT_4, INSTANT, 850))
				.ability(ab(k, "vortex", SLOT_5, HOLD, 204))
				.ability(ab(k, "speed_mode", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "phase_vibrate", AbilitySlot.SLOT_7, INSTANT, 102))
				.ability(ab(k, "lightning_throw", AbilitySlot.SLOT_8, INSTANT, 80))
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
				// v0.13.22 revamp (batch B): the ground under you is your ammo
				.ability(ab(k, "rock_shot", SLOT_1, INSTANT, 34))
				.ability(ab(k, "earth_spike", SLOT_2, INSTANT, 85))
				.ability(ab(k, "rock_surf", SLOT_3, TOGGLE, 85))
				.ability(ab(k, "earthquake", SLOT_4, HOLD, 55 * S))
				.ability(ab(k, "stone_wall", SLOT_5, INSTANT, 7 * S))
				.ability(ab(k, "earth_armor", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "sinkhole", AbilitySlot.SLOT_7, INSTANT, 12 * S))
				.ability(ab(k, "tectonic_pillar", AbilitySlot.SLOT_8, INSTANT, 153))
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
				// v0.13.22 revamp (batch B): plant crystal nodes, then shatter them
				.ability(ab(k, "crystal_shard", SLOT_1, INSTANT, 34))
				.ability(ab(k, "shatter", SLOT_2, INSTANT, 4 * S))
				.ability(ab(k, "crystal_path", SLOT_3, TOGGLE, 102))
				.ability(ab(k, "crystal_eruption", SLOT_4, HOLD, 60 * S))
				.ability(ab(k, "crystal_prison", SLOT_5, INSTANT, 204))
				.ability(ab(k, "crystal_armor", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "resonance_spire", AbilitySlot.SLOT_7, INSTANT, 17 * S))
				.ability(ab(k, "refract", AbilitySlot.SLOT_8, INSTANT, 85))
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
				.ability(ab(k, "electric_bolt", SLOT_1, INSTANT, 34))
				.ability(ab(k, "chain_lightning", SLOT_2, INSTANT, 7 * S))
				.ability(ab(k, "electric_dash", SLOT_3, INSTANT, 68))
				.ability(ab(k, "electrical_storm", SLOT_4, HOLD, 55 * S))
				.ability(ab(k, "electromagnetic_pull", SLOT_5, INSTANT, 6 * S))
				.ability(ab(k, "charged_mode", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "overcharge", AbilitySlot.SLOT_7, INSTANT, 10 * S))
				.ability(ab(k, "ion_pull", AbilitySlot.SLOT_8, INSTANT, 8 * S))
				.passives(pk(k, "passive.static"), pk(k, "passive.lightning_resist"), pk(k, "passive.redstone"))
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
				// v0.13.22 revamp (batch B): one Heat bar -- hotter hits harder, blue above 75%, overheat burns
				.ability(ab(k, "fireball", SLOT_1, INSTANT, 34))
				.ability(ab(k, "flamethrower", SLOT_2, HOLD, 0))
				.ability(ab(k, "jet_flight", SLOT_3, HOLD, 0))
				.ability(ab(k, "inferno", SLOT_4, HOLD, 47 * S))
				.ability(ab(k, "flame_wall", SLOT_5, INSTANT, 1 * S))
				.ability(ab(k, "flame_body", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "heat_wave", AbilitySlot.SLOT_7, INSTANT, 10 * S))
				.ability(ab(k, "fire_whip", AbilitySlot.SLOT_8, INSTANT, 51))
				.passives(pk(k, "passive.fire_resist"))
				.serum(SerumRecipe.of("minecraft:fire_resistance", pk(k, "serum"),
						"minecraft:fire_charge", "minecraft:charcoal", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.FIRE_EXPOSURE, pk(k, "trigger"), "projecthero.device.blast_chamber"))
				.combos("projecthero.combo.pyrokinesis_flight", "projecthero.combo.wind_fire")
				.build();
	}

	// ==================================================================================
	// 09 Cryokinesis
	// ==================================================================================
	private static Power cryokinesis() {
		String k = "power_09_cryokinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.ELEMENTAL)
				// v0.13.22 revamp (batch B): frost stacks, then frozen solid
				.ability(ab(k, "ice_bolt", SLOT_1, INSTANT, 34))
				.ability(ab(k, "freeze_beam", SLOT_2, HOLD, 0))
				.ability(ab(k, "ice_slide", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "absolute_zero", SLOT_4, HOLD, 38 * S))
				.ability(ab(k, "ice_wall", SLOT_5, INSTANT, 7 * S))
				.ability(ab(k, "frozen_armor", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "flash_freeze", AbilitySlot.SLOT_7, INSTANT, 12 * S))
				.ability(ab(k, "ice_blade", AbilitySlot.SLOT_8, INSTANT, 32 * S))
				.passives(pk(k, "passive.powder_snow"), pk(k, "passive.freeze_resist"))
				.serum(SerumRecipe.of("minecraft:slowness", pk(k, "serum"),
						"minecraft:snowball", "minecraft:ice", "minecraft:lapis_lazuli"))
				.trigger(MutationTrigger.of(Kind.POWDER_SNOW, pk(k, "trigger"), "projecthero.device.crystal_chamber"))
				.combos("projecthero.combo.cryokinesis_water")
				.build();
	}

	// ==================================================================================
	// 10 Telekinesis
	// ==================================================================================
	private static Power telekinesis() {
		String k = "power_10_telekinesis";
		return Power.Builder.of(Powers.id(k), PowerCategory.MENTAL)
				// v0.13.22 revamp (batch D): the juggling kit -- V feeds up to three objects into an orbit around
				// you, V throws them one at a time, H launches the whole orbit, N Mind-Locks a target in mid-air.
				.ability(ab(k, "force_push", SLOT_1, INSTANT, 17))
				.ability(ab(k, "telekinetic_barrier", SLOT_2, TOGGLE, 0))
				.ability(ab(k, "psychic_flight", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "telekinetic_explosion", SLOT_4, HOLD, 76 * S))
				.ability(ab(k, "telekinetic_grab", SLOT_5, INSTANT, 15))
				.ability(ab(k, "block_manipulation", SLOT_6, HOLD, 0))
				.ability(ab(k, "launch_orbit", AbilitySlot.SLOT_7, INSTANT, 3 * S))
				.ability(ab(k, "mind_lock", AbilitySlot.SLOT_8, INSTANT, 12 * S))
				.passives(pk(k, "passive.psi"), pk(k, "passive.fall"), pk(k, "passive.item_drift"), pk(k, "passive.orbit"))
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
				// v0.13.22 revamp (batch D): light pass -- 15% shorter cooldowns, plus H Bamf Strike and N Swap.
				.ability(ab(k, "blink", SLOT_1, HOLD, 51))
				.ability(ab(k, "target_teleport", SLOT_2, INSTANT, 6 * S))
				.ability(ab(k, "escape_blink", SLOT_3, INSTANT, 68))
				.ability(ab(k, "portal", SLOT_4, CHARGE, 51 * S))
				.ability(ab(k, "teleport_mark", SLOT_5, INSTANT, 17 * S))
				.ability(ab(k, "portal_anchor", SLOT_6, INSTANT, 51))
				.ability(ab(k, "bamf_strike", AbilitySlot.SLOT_7, INSTANT, 12 * S))
				.ability(ab(k, "swap", AbilitySlot.SLOT_8, INSTANT, 6 * S))
				.passives(pk(k, "passive.pearl_resist"))
				.serum(SerumRecipe.of("minecraft:swiftness", pk(k, "serum"),
						"minecraft:ender_pearl", "minecraft:amethyst_shard", "minecraft:chorus_fruit"))
				.trigger(MutationTrigger.of(Kind.ENDER_PEARL, pk(k, "trigger"), "projecthero.device.enchanting_resonance"))
				.combos("projecthero.combo.shadow_teleportation")
				.build();
	}

	// ==================================================================================
	// 12 Super Regeneration (renamed from "Healing Factor" in v0.9.22)
	// ==================================================================================
	private static Power superRegeneration() {
		String k = "power_12_super_regeneration";
		return Power.Builder.of(Powers.id(k), PowerCategory.PHYSICAL)
				// v0.14.5 rework: passive-only -- no ability keys, just healing, cleansing and revive charges
				.passiveOnly()
				.passives(pk(k, "passive.regen"), pk(k, "passive.cleanse"), pk(k, "passive.revive"))
				.serum(SerumRecipe.of("minecraft:regeneration", pk(k, "serum"),
						"minecraft:golden_apple", "minecraft:spider_eye", "minecraft:bone_meal"))
				.trigger(MutationTrigger.of(Kind.NEAR_DEATH, pk(k, "trigger"), null))
				.combos("projecthero.combo.speedster_set")
				.build();
	}

	// ==================================================================================
	// 14 Sonic Scream
	// ==================================================================================
	private static Power sonicScream() {
		String k = "power_14_sonic_scream";
		return Power.Builder.of(Powers.id(k), PowerCategory.ENERGY)
				.ability(ab(k, "sonic_blast", SLOT_1, INSTANT, 85))
				.ability(ab(k, "focused_scream", SLOT_2, INSTANT, 170))
				.ability(ab(k, "sonic_jump", SLOT_3, INSTANT, 85))
				.ability(ab(k, "supersonic_scream", SLOT_4, HOLD, 60 * S))
				.ability(ab(k, "resonance", SLOT_5, INSTANT, 85))
				.ability(ab(k, "echolocation", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "sound_barrier", AbilitySlot.SLOT_7, INSTANT, 12 * S))
				.ability(ab(k, "disorient", AbilitySlot.SLOT_8, INSTANT, 12 * S))
				.passives(pk(k, "passive.voice"), pk(k, "passive.self_resist"))
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
				// v0.13.22 revamp (batch D): refraction -- V Mirror Images, H Hard-Light Blade, N Prism Shield;
				// Flash moved to G (Radiant Lance on its sneak variant).
				.ability(ab(k, "light_blast", SLOT_1, CHARGE, 17))
				.ability(ab(k, "flash", SLOT_2, INSTANT, 170))
				.ability(ab(k, "mirage_dash", SLOT_3, HOLD, 51))
				.ability(ab(k, "perfect_cloak", SLOT_4, CHARGE, 64 * S))
				.ability(ab(k, "decoy", SLOT_5, INSTANT, 16 * S))
				.ability(ab(k, "cloaking_toggle", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "hard_light_blade", AbilitySlot.SLOT_7, INSTANT, 20 * S))
				.ability(ab(k, "prism_shield", AbilitySlot.SLOT_8, HOLD, 0))
				.passives(pk(k, "passive.detection"), pk(k, "passive.refraction"))
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
				// v0.13.22 revamp (batch E): -15% cooldowns, + H Spider-Sense Dodge / N Venom Bite
				.ability(ab(k, "adhesive_strike", SLOT_1, INSTANT, 68))
				.ability(ab(k, "pounce", SLOT_2, INSTANT, 85))
				.ability(ab(k, "wall_leap", SLOT_3, INSTANT, 51))
				.ability(ab(k, "predator_rush", SLOT_4, INSTANT, 30 * S))
				.ability(ab(k, "wall_grip", SLOT_5, TOGGLE, 0))
				.ability(ab(k, "adhesion_mode", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "spider_sense", AbilitySlot.SLOT_7, INSTANT, 6 * S))
				.ability(ab(k, "venom_bite", AbilitySlot.SLOT_8, INSTANT, 7 * S))
				.passives(pk(k, "passive.fall"), pk(k, "passive.jump"), pk(k, "passive.sense"), pk(k, "passive.evolve"))
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
				// v0.13.22 revamp (batch E): -15% cooldowns, + H Rubber Shield / N Parachute Glide
				.ability(ab(k, "stretch_punch", SLOT_1, CHARGE, 34))
				.ability(ab(k, "double_fist_slam", SLOT_2, INSTANT, 119))
				.ability(ab(k, "slingshot", SLOT_3, INSTANT, 51))
				.ability(ab(k, "giant_hammer_fist", SLOT_4, INSTANT, 30 * S))
				.ability(ab(k, "elastic_grab", SLOT_5, INSTANT, 102))
				.ability(ab(k, "elastic_form", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "rubber_shield", AbilitySlot.SLOT_7, HOLD, 8 * S))
				.ability(ab(k, "parachute_glide", AbilitySlot.SLOT_8, INSTANT, 5 * S))
				.passives(pk(k, "passive.fall"), pk(k, "passive.bounce"), pk(k, "passive.squeeze"),
						pk(k, "passive.melee"), pk(k, "passive.knockback"))
				.serum(SerumRecipe.of("minecraft:leaping", pk(k, "serum"),
						"minecraft:slime_ball", "minecraft:string", "minecraft:rabbit_hide"))
				.trigger(MutationTrigger.of(Kind.SLIME_IMPACT, pk(k, "trigger"), "projecthero.device.gravity_distortion_rig"))
				.build();
	}

	// ==================================================================================
	// 18 Density Manipulation
	// ==================================================================================
	private static Power densityManipulation() {
		String k = "power_18_density_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.MOLECULAR)
				// v0.13.22 revamp (batch E): -15% cooldowns, + H Intangible Dodge / N Crushing Touch
				.ability(ab(k, "increase_density", SLOT_1, INSTANT, 0))
				.ability(ab(k, "decrease_density", SLOT_2, INSTANT, 0))
				.ability(ab(k, "density_anchor", SLOT_3, INSTANT, 510))
				.ability(ab(k, "heavy_impact", SLOT_4, INSTANT, 170))
				.ability(ab(k, "phase", SLOT_5, TOGGLE, 0))
				.ability(ab(k, "zero_density", SLOT_6, INSTANT, 34))
				.ability(ab(k, "intangible_dodge", AbilitySlot.SLOT_7, INSTANT, 4 * S))
				.ability(ab(k, "crushing_touch", AbilitySlot.SLOT_8, INSTANT, 12 * S))
				.passives(pk(k, "passive.mode"), pk(k, "passive.shell"))
				.serum(SerumRecipe.of("minecraft:slow_falling", pk(k, "serum"),
						"minecraft:iron_ingot", "minecraft:feather", "minecraft:amethyst_shard"))
				.trigger(MutationTrigger.of(Kind.MOLECULAR_COMPRESSION, pk(k, "trigger"), "projecthero.device.compression_chamber"))
				.build();
	}

	// ==================================================================================
	// 19 Shadow Manipulation
	// ==================================================================================
	private static Power shadowManipulation() {
		String k = "power_19_shadow_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.ENERGY)
				// v0.13.22 revamp (batch D): shadow travel -- V Shadow Bind (Shadow Grab kept on Sneak+V),
				// H Shadow Walk, N Shadow Servant.
				.ability(ab(k, "shadow_bolt", SLOT_1, INSTANT, 34))
				.ability(ab(k, "shadow_tendrils", SLOT_2, INSTANT, 136))
				.ability(ab(k, "shadow_step", SLOT_3, INSTANT, 51))
				.ability(ab(k, "total_darkness", SLOT_4, CHARGE, 51 * S))
				.ability(ab(k, "shadow_bind", SLOT_5, INSTANT, 10 * S))
				.ability(ab(k, "shadow_form", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "shadow_walk", AbilitySlot.SLOT_7, TOGGLE, 0))
				.ability(ab(k, "shadow_servant", AbilitySlot.SLOT_8, INSTANT, 30 * S))
				.passives(pk(k, "passive.darkness_regen"), pk(k, "passive.shadow_travel"))
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
				.ability(ab(k, "energy_blast", SLOT_1, CHARGE, 34))
				.ability(ab(k, "energy_beam", SLOT_2, HOLD, 0))
				.ability(ab(k, "absorption_shield", SLOT_3, INSTANT, 34))
				.ability(ab(k, "overload", SLOT_4, CHARGE, 25 * S))
				.ability(ab(k, "energy_drain", SLOT_5, HOLD, 8 * S))
				.ability(ab(k, "absorption_mode", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "redirect", AbilitySlot.SLOT_7, INSTANT, 10 * S))
				.ability(ab(k, "empower", AbilitySlot.SLOT_8, INSTANT, 25 * S))
				.passives(pk(k, "passive.meter"), pk(k, "passive.elements"))
				.serum(SerumRecipe.of("minecraft:fire_resistance", pk(k, "serum"),
						"minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:amethyst_shard"))
				.trigger(MutationTrigger.of(Kind.ENERGY_OVERLOAD, pk(k, "trigger"), "projecthero.device.blast_chamber"))
				.combos("projecthero.combo.energy_absorption_laser")
				.build();
	}

	// ==================================================================================
	// 21 Shockwave Manipulation
	// ==================================================================================
	private static Power shockwaveManipulation() {
		String k = "power_21_shockwave_manipulation";
		return Power.Builder.of(Powers.id(k), PowerCategory.KINETIC)
				.ability(ab(k, "shockwave_punch", SLOT_1, INSTANT, 34))
				.ability(ab(k, "ground_wave", SLOT_2, INSTANT, 5 * S))
				.ability(ab(k, "recoil_jump", SLOT_3, INSTANT, 50))
				.ability(ab(k, "kinetic_detonation", SLOT_4, CHARGE, 85 * S))
				.ability(ab(k, "repulsion_field", SLOT_5, HOLD, 0))
				.ability(ab(k, "charge", SLOT_6, CHARGE, 0))
				.ability(ab(k, "aftershock", AbilitySlot.SLOT_7, INSTANT, 8 * S))
				.ability(ab(k, "kinetic_parry", AbilitySlot.SLOT_8, INSTANT, 6 * S))
				.passives(pk(k, "passive.kinetic"), pk(k, "passive.knockback_resist"))
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
				// v0.13.22 revamp (batch E): -15% cooldowns, + H Thorn Sentry / N Spore Cloud
				.ability(ab(k, "thorn_shot", SLOT_1, INSTANT, 34))
				.ability(ab(k, "vine_grab", SLOT_2, INSTANT, 119))
				.ability(ab(k, "vine_swing", SLOT_3, INSTANT, 17))
				.ability(ab(k, "overgrowth", SLOT_4, CHARGE, 51 * S))
				.ability(ab(k, "living_wall", SLOT_5, INSTANT, 136))
				.ability(ab(k, "natures_blessing", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "thorn_sentry", AbilitySlot.SLOT_7, INSTANT, 25 * S))
				.ability(ab(k, "spore_cloud", AbilitySlot.SLOT_8, INSTANT, 16 * S))
				.passives(pk(k, "passive.bonemeal"), pk(k, "passive.lush_regen"), pk(k, "passive.swing_through_grass"))
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
				// v0.13.22 revamp (batch D): changing which way gravity pulls -- H Invert, N Heavy Ground.
				.ability(ab(k, "gravity_push", SLOT_1, INSTANT, 34))
				.ability(ab(k, "gravity_crush", SLOT_2, INSTANT, 17 * S))
				.ability(ab(k, "zero_g", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "gravity_well", SLOT_4, CHARGE, 102 * S))
				.ability(ab(k, "levitate", SLOT_5, INSTANT, 5 * S))
				.ability(ab(k, "gravity_field", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "invert", AbilitySlot.SLOT_7, INSTANT, 9 * S))
				.ability(ab(k, "heavy_ground", AbilitySlot.SLOT_8, INSTANT, 17 * S))
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
				.ability(ab(k, "wind_blade", SLOT_1, HOLD, 34))
				.ability(ab(k, "tornado", SLOT_2, INSTANT, 170))
				.ability(ab(k, "glide", SLOT_3, HOLD, 5 * S))
				.ability(ab(k, "hurricane", SLOT_4, HOLD, 51 * S))
				.ability(ab(k, "wind_push", SLOT_5, INSTANT, 68))
				.ability(ab(k, "tailwind", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "rideable_tornado", AbilitySlot.SLOT_7, HOLD, 30 * S))
				.ability(ab(k, "vacuum", AbilitySlot.SLOT_8, HOLD, 12 * S))
				.passives(pk(k, "passive.glide"), pk(k, "passive.fall"))
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
				// v0.13.22 revamp (batch B): a carried water supply
				.ability(ab(k, "water_shot", SLOT_1, HOLD, 34))
				.ability(ab(k, "water_whip", SLOT_2, INSTANT, 102))
				.ability(ab(k, "riptide", SLOT_3, INSTANT, 102))
				.ability(ab(k, "tidal_wave", SLOT_4, HOLD, 38 * S))
				.ability(ab(k, "water_prison", SLOT_5, HOLD, 204))
				.ability(ab(k, "aquatic_form", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "healing_water", AbilitySlot.SLOT_7, INSTANT, 12 * S))
				.ability(ab(k, "geyser", AbilitySlot.SLOT_8, INSTANT, 150))
				.passives(pk(k, "passive.swimming"), pk(k, "passive.no_drown"), pk(k, "passive.supply"))
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
				// v0.13.22 revamp (batch D): light pass -- +20% damage, 15% shorter cooldowns, plus H Magneto
				// Hover and N Disarm.
				.ability(ab(k, "ferrous_shot", SLOT_1, INSTANT, 51))
				.ability(ab(k, "magnetic_grip", SLOT_2, INSTANT, 0))
				.ability(ab(k, "polarity_leap", SLOT_3, INSTANT, 34))
				.ability(ab(k, "metal_storm", SLOT_4, INSTANT, 15 * S))
				.ability(ab(k, "magnetic_crush", SLOT_5, INSTANT, 152))
				.ability(ab(k, "magnetic_sense", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "magneto_hover", AbilitySlot.SLOT_7, TOGGLE, 0))
				.ability(ab(k, "disarm", AbilitySlot.SLOT_8, INSTANT, 10 * S))
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
				// v0.13.22 revamp (batch E): -15% cooldowns, + H Shrink Punch / N Mount
				.ability(ab(k, "giant_punch", SLOT_1, INSTANT, 51))
				.ability(ab(k, "stomp", SLOT_2, INSTANT, 136))
				.ability(ab(k, "shrink", SLOT_3, TOGGLE, 0))
				.ability(ab(k, "giant_form", SLOT_4, TOGGLE, 0))
				.ability(ab(k, "tiny_dash", SLOT_5, INSTANT, 85))
				.ability(ab(k, "large_form", SLOT_6, TOGGLE, 0))
				.ability(ab(k, "shrink_punch", AbilitySlot.SLOT_7, INSTANT, 10 * S))
				.ability(ab(k, "mount", AbilitySlot.SLOT_8, INSTANT, 2 * S))
				.passives(pk(k, "passive.form"), pk(k, "passive.fall"))
				.serum(SerumRecipe.of("minecraft:leaping", pk(k, "serum"),
						"minecraft:slime_ball", "minecraft:rabbit_hide", "minecraft:fermented_spider_eye", "minecraft:redstone"))
				.trigger(MutationTrigger.of(Kind.MASS_COMPRESSION, pk(k, "trigger"), "projecthero.device.mass_compression_chamber"))
				.build();
	}
}
