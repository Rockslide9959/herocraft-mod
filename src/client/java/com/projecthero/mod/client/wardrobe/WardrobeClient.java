package com.projecthero.mod.client.wardrobe;

import com.projecthero.mod.wardrobe.WardrobeMod;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.renderer.entity.ArmorStandRenderer;

/** v0.16.5: the Mannequin's and the Wardrobe's renderers, and the skin-tight Spider-Man suits on vanilla armour stands. */
public final class WardrobeClient {
	private WardrobeClient() {
	}

	public static void initialize() {
		EntityModelLayerRegistry.registerModelLayer(MannequinRenderer.LAYER, MannequinRenderer::createLayer);
		EntityRendererRegistry.register(WardrobeMod.MANNEQUIN, MannequinRenderer::new);
		BlockEntityRendererRegistry.register(WardrobeMod.WARDROBE_BE, WardrobeRenderer::new);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof ArmorStandRenderer stand) {
				helper.register(new StandSpiderSuitLayer<>(stand));
			}
		});
	}
}
