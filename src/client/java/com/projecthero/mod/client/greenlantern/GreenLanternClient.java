package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;

import net.minecraft.client.renderer.RenderType;

/** v0.13.21: Green Lantern's client-side registrations in one place. */
public final class GreenLanternClient {
	private GreenLanternClient() {
	}

	public static void initialize() {
		GreenLanternShieldRenderer.initialize();
		GreenLanternDomeRenderer.initialize(); // v0.15.1: hard-light dome model
		GreenLanternAutoProtectRenderer.initialize(); // v0.17.1: Ring Auto-Protection's shells and the Fall Cushion pad
		GreenLanternFlightFx.initialize(); // v0.15.15: flight aura + sprint-flight trail
		GreenLanternOathSubtitle.initialize(); // v0.15.15: the Oath as a centred subtitle (was the action bar)
		com.projecthero.mod.client.greenlantern.mech.GreenLanternMechClient.initialize(); // v0.17.1: the Hard-Light Mech
		PowerBatteryHeldRenderer.initialize(); // v0.15.15: the battery hanging from the hand, swaying; the charge glow
		// v0.17.1: the Carry Platform's Car and Jet forms
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.projecthero.mod.greenlantern.entity.GreenLanternEntityTypes.HARD_LIGHT_CAR, HardLightVehicleRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.projecthero.mod.greenlantern.entity.GreenLanternEntityTypes.HARD_LIGHT_JET, HardLightVehicleRenderer::new);
		HardLightVehicleHud.initialize(); // v0.17.1: the driver's controls, always up (bottom left)
		GreenLanternRingVoiceClient.initialize(); // v0.17.1: the ring's voice + the Construct Combo popup
		GreenLanternConstructHealthClient.initialize(); // v0.17.1: damaged hard-light blocks dim, darken and flicker
		// v0.16.1: the Sniper Rifle construct's attack / use input (its scope overlay is registered in ProjectHeroModClient,
		// before the HUDs, so the Green Lantern HUD draws on top of the scope instead of under its dark surround)
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(GreenLanternSniperClient::clientTick);
		// the hard-light construct blocks and the Power Battery's glass barrel are translucent
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), GreenLanternBlocks.HARD_LIGHT,
				GreenLanternBlocks.HARD_LIGHT_STAIRS, GreenLanternBlocks.HARD_LIGHT_LAMP, GreenLanternBlocks.POWER_BATTERY);
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), // v0.17.1: the survival constructs' chest and tower
				com.projecthero.mod.greenlantern.block.GreenLanternSurvivalBlocks.HARD_LIGHT_SCAFFOLD,
				com.projecthero.mod.greenlantern.block.GreenLanternSurvivalBlocks.HARD_LIGHT_CHEST);
	}
}
