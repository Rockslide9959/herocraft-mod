package com.projecthero.mod.client.render;

import com.projecthero.mod.ironman.entity.IronManEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class IronManEntityRenderers {
	private IronManEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(IronManEntityTypes.SUIT_PART, IronManSuitPartRenderer::new);
		EntityRendererRegistry.register(IronManEntityTypes.MISSILE, IronManMissileRenderer::new);
		// v0.14.21: the Mark VII delivery pod + the Mark V suitcase model / layer
		EntityRendererRegistry.register(IronManEntityTypes.DELIVERY_POD,
				com.projecthero.mod.client.ironman.IronManDeliveryPodRenderer::new);
		com.projecthero.mod.client.ironman.MarkVSuitcaseClient.initialize();
		// v0.15.11: the hammer / wrench / carried piece of a hand-built suit-up
		com.projecthero.mod.client.ironman.IronManManualSuitUpLayer.initialize();
	}
}
