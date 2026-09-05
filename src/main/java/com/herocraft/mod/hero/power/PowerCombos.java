package com.herocraft.mod.hero.power;

import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Powers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Documented cross-power synergies (spec section 14). A combo applies whenever the player
 * <em>owns</em> both powers, even if only one is currently the active six-slot power. Thor is never
 * a party to any of these.
 */
public final class PowerCombos {
	public static final String STRENGTH = "power_01_super_strength";
	public static final String LASER = "power_02_laser_vision";
	public static final String FLIGHT = "power_03_flight";
	public static final String SPEED = "power_04_super_speed";
	public static final String GEO = "power_05_geokinesis";
	public static final String ELECTRO = "power_07_electrokinesis";
	public static final String PYRO = "power_08_pyrokinesis";
	public static final String CRYO = "power_09_cryokinesis";
	public static final String DURABILITY = "power_13_super_durability";
	public static final String ENERGY = "power_20_energy_absorption";
	public static final String WATER = "power_25_water_manipulation";
	public static final String SIZE = "power_27_size_manipulation";

	private PowerCombos() {
	}

	public static boolean owns(ServerPlayer player, String powerKey) {
		var power = Powers.byKey(powerKey);
		return power != null && ExperimentalPowers.owns(player, power);
	}

	public static boolean has(ServerPlayer player, String a, String b) {
		return owns(player, a) && owns(player, b);
	}

	/** Super Strength + Flight -> Meteor Slam: fall speed/distance amplifies a downward impact. */
	public static float meteorSlamBonus(ServerPlayer player, String activePowerKey) {
		if ((activePowerKey.equals(FLIGHT) && owns(player, STRENGTH))
				|| (activePowerKey.equals(STRENGTH) && owns(player, FLIGHT))) {
			return (float) Math.min(12.0, player.fallDistance * 0.6 + Math.abs(player.getDeltaMovement().y) * 4.0);
		}
		return 0.0f;
	}

	/** Water + Electrokinesis: a wet target takes extra electrical damage. */
	public static float wetElectricMultiplier(ServerPlayer player, LivingEntity target) {
		if (has(player, WATER, ELECTRO) && (target.isInWaterOrRain() || target.isInWater())) {
			return 1.6f;
		}
		return 1.0f;
	}

	/** Super Speed + Electrokinesis: sprinting builds Electrokinesis charge for the next bolt. */
	public static void speedGeneratesCharge(ServerPlayer player) {
		if (has(player, SPEED, ELECTRO) && player.isSprinting()) {
			var electro = Powers.byKey(ELECTRO);
			ExperimentalPowers.addResource(player, electro, "static_charge", 1.0f, 100.0f);
		}
	}

	/** Geokinesis + Super Strength: bigger boulders / stronger earth attacks. */
	public static float geoStrengthBonus(ServerPlayer player) {
		return has(player, GEO, STRENGTH) ? 4.0f : 0.0f;
	}

	/** Super Durability + Size: Giant/Large form is steadier and takes less self-impact damage. */
	public static float sizeStabilityFactor(ServerPlayer player) {
		return has(player, DURABILITY, SIZE) ? 0.5f : 1.0f;
	}

	/** Energy Absorption + Laser Vision: stored energy tops up Laser's heat budget. */
	public static boolean energyRefillsLaser(ServerPlayer player) {
		return has(player, ENERGY, LASER)
				&& ExperimentalPowers.getResource(player, Powers.byKey(ENERGY), "energy") > 20.0f;
	}

	/** Pyrokinesis + Flight: flame trail during fast flight. */
	public static boolean flameFlightTrail(ServerPlayer player) {
		return has(player, PYRO, FLIGHT);
	}
}
