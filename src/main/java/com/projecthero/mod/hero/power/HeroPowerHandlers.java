package com.projecthero.mod.hero.power;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilityHandlers;

/**
 * Bootstrap that wires ability mechanics into {@link AbilityHandlers}. Called once at mod init after
 * {@code Powers.initialize()}. Each batch adds its per-power {@code register()} calls here.
 */
public final class HeroPowerHandlers {
	private HeroPowerHandlers() {
	}

	public static void registerAll() {
		// Batch 3 — powers 01-05
		com.projecthero.mod.hero.power.p01.SuperStrengthHandlers.register();
		com.projecthero.mod.hero.power.p02.LaserVisionHandlers.register();
		com.projecthero.mod.hero.power.p03.FlightHandlers.register();
		com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.register();
		com.projecthero.mod.hero.power.p05.GeokinesisHandlers.register();

		// Batch 4 — powers 06-10
		com.projecthero.mod.hero.power.p06.CrystalkinesisHandlers.register();
		com.projecthero.mod.hero.power.p07.ElectrokinesisHandlers.register();
		com.projecthero.mod.hero.power.p08.PyrokinesisHandlers.register();
		com.projecthero.mod.hero.power.p09.CryokinesisHandlers.register();
		com.projecthero.mod.hero.power.p10.TelekinesisHandlers.register();

		// Batch 5 — powers 11-15
		com.projecthero.mod.hero.power.p11.TeleportationHandlers.register();
		com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers.register();
		com.projecthero.mod.hero.power.p13.SuperDurabilityHandlers.register();
		com.projecthero.mod.hero.power.p14.SonicScreamHandlers.register();
		com.projecthero.mod.hero.power.p15.InvisibilityLightHandlers.register();

		// Batch 6 — powers 16-20
		com.projecthero.mod.hero.power.p16.SpiderClimbingHandlers.register();
		com.projecthero.mod.hero.power.p17.ElasticityHandlers.register();
		com.projecthero.mod.hero.power.p18.DensityManipulationHandlers.register();
		com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers.register();
		com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers.register();

		// Batch 7 — powers 21-27
		com.projecthero.mod.hero.power.p21.ShockwaveHandlers.register();
		com.projecthero.mod.hero.power.p22.PlantManipulationHandlers.register();
		com.projecthero.mod.hero.power.p23.GravityHandlers.register();
		com.projecthero.mod.hero.power.p24.WindHandlers.register();
		com.projecthero.mod.hero.power.p25.WaterHandlers.register();
		com.projecthero.mod.hero.power.p26.MagneticHandlers.register();
		com.projecthero.mod.hero.power.p27.SizeHandlers.register();

		ProjectHeroMod.LOGGER.info("[ProjectHero] wired {} experimental ability handlers", AbilityHandlers.count());
	}
}
