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
		GreenLanternFlightFx.initialize(); // v0.15.15: flight aura + sprint-flight trail
		PowerBatteryHeldRenderer.initialize(); // v0.15.15: the battery hanging from the hand, swaying; the charge glow
		// the hard-light construct blocks and the Power Battery's glass barrel are translucent
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), GreenLanternBlocks.HARD_LIGHT,
				GreenLanternBlocks.HARD_LIGHT_STAIRS, GreenLanternBlocks.HARD_LIGHT_LAMP, GreenLanternBlocks.POWER_BATTERY);
	}
}
