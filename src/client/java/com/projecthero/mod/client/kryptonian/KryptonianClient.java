package com.projecthero.mod.client.kryptonian;

import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.kryptonian.item.KryptonianItems;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * v0.14.8: the Kryptonian's client registration -- the HUD, the heat-vision beams and the falling meteor; v0.14.9 the
 * Superman Suit's cape layer (v0.14.21: on the shared {@link FlowingCapeLayer}).
 */
public final class KryptonianClient {
	private KryptonianClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(KryptonianHud::render);
		KryptonianBeamRenderer.init();
		EntityRendererRegistry.register(KryptonianItems.METEOR, KryptoniteMeteorRenderer::new);
		// v0.14.9: the Superman Suit's cloth cape
		FlowingCapeLayer.register(SupermanCapeLayer::new);
	}
}
