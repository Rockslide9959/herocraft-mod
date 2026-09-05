package com.herocraft.mod.hero.power;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.hero.AbilityHandlers;

/**
 * Bootstrap that wires ability mechanics into {@link AbilityHandlers}. Called once at mod init after
 * {@code Powers.initialize()}. Each batch adds its per-power {@code register()} calls here.
 */
public final class HeroPowerHandlers {
	private HeroPowerHandlers() {
	}

	public static void registerAll() {
		// Batch 3 — powers 01-05
		com.herocraft.mod.hero.power.p01.SuperStrengthHandlers.register();
		com.herocraft.mod.hero.power.p02.LaserVisionHandlers.register();
		com.herocraft.mod.hero.power.p03.FlightHandlers.register();
		com.herocraft.mod.hero.power.p04.SuperSpeedHandlers.register();
		com.herocraft.mod.hero.power.p05.GeokinesisHandlers.register();

		// Batch 4 — powers 06-10
		com.herocraft.mod.hero.power.p06.CrystalkinesisHandlers.register();
		com.herocraft.mod.hero.power.p07.ElectrokinesisHandlers.register();
		com.herocraft.mod.hero.power.p08.PyrokinesisHandlers.register();
		com.herocraft.mod.hero.power.p09.CryokinesisHandlers.register();
		com.herocraft.mod.hero.power.p10.TelekinesisHandlers.register();

		// Batch 5 — powers 11-15
		com.herocraft.mod.hero.power.p11.TeleportationHandlers.register();
		com.herocraft.mod.hero.power.p12.HealingFactorHandlers.register();
		com.herocraft.mod.hero.power.p13.SuperDurabilityHandlers.register();
		com.herocraft.mod.hero.power.p14.SonicScreamHandlers.register();
		com.herocraft.mod.hero.power.p15.InvisibilityLightHandlers.register();

		// Batch 6 — powers 16-20
		com.herocraft.mod.hero.power.p16.SpiderClimbingHandlers.register();
		com.herocraft.mod.hero.power.p17.ElasticityHandlers.register();
		com.herocraft.mod.hero.power.p18.DensityManipulationHandlers.register();
		com.herocraft.mod.hero.power.p19.ShadowManipulationHandlers.register();
		com.herocraft.mod.hero.power.p20.EnergyAbsorptionHandlers.register();

		// Batch 7 — powers 21-27
		com.herocraft.mod.hero.power.p21.ShockwaveHandlers.register();
		com.herocraft.mod.hero.power.p22.PlantManipulationHandlers.register();
		com.herocraft.mod.hero.power.p23.GravityHandlers.register();
		com.herocraft.mod.hero.power.p24.WindHandlers.register();
		com.herocraft.mod.hero.power.p25.WaterHandlers.register();
		com.herocraft.mod.hero.power.p26.MagneticHandlers.register();
		com.herocraft.mod.hero.power.p27.SizeHandlers.register();

		HeroCraftMod.LOGGER.info("[HeroCraft] wired {} experimental ability handlers", AbilityHandlers.count());
	}
}
