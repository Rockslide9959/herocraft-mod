package com.projecthero.mod.client.kryptonian;

import com.projecthero.mod.kryptonian.item.KryptonianItems;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.14.8: the Kryptonian's client registration -- the HUD, the heat-vision beams and the falling meteor; v0.14.9 the
 * Superman Suit's cape layer.
 */
public final class KryptonianClient {
	private KryptonianClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(KryptonianHud::render);
		KryptonianBeamRenderer.init();
		EntityRendererRegistry.register(KryptonianItems.METEOR, KryptoniteMeteorRenderer::new);
		// v0.14.9: the Superman Suit's cloth cape
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new SupermanCapeLayer(playerRenderer));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SupermanCapeLayer.clear());
	}
}
