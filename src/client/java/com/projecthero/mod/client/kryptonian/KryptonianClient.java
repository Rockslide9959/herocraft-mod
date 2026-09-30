package com.projecthero.mod.client.kryptonian;

import com.projecthero.mod.kryptonian.item.KryptonianItems;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** v0.14.8: the Kryptonian's client registration -- the HUD, the heat-vision beams and the falling meteor. */
public final class KryptonianClient {
	private KryptonianClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(KryptonianHud::render);
		KryptonianBeamRenderer.init();
		EntityRendererRegistry.register(KryptonianItems.METEOR, KryptoniteMeteorRenderer::new);
	}
}
