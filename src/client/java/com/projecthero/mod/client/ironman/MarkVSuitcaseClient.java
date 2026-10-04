package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.item.MarkVSuitcaseItem;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/** v0.14.21: client wiring for the 3D Mark V Suitcase -- its GeckoLib renderer, inventory sprite model and hand layer. */
public final class MarkVSuitcaseClient {
	private static MarkVSuitcaseRenderer renderer;

	private MarkVSuitcaseClient() {
	}

	public static void initialize() {
		MarkVSuitcaseItem.rendererFactory = consumer -> consumer.accept(new GeoRenderProvider() {
			@Override
			public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
				if (renderer == null) {
					renderer = new MarkVSuitcaseRenderer();
				}
				return renderer;
			}
		});
		// the flat inventory sprite, kept as its own model so the GUI keeps the 2D icon
		ModelLoadingPlugin.register(plugin -> plugin.addModels(MarkVSuitcaseRenderer.ICON_MODEL));
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new MarkVSuitcaseLayer(playerRenderer));
			}
		});
	}
}
