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
		com.projecthero.mod.client.ironman.IronManPortableClient.initialize(); // v0.16.7 (user) Era II portable forms
		// v0.16.1: the Hulkbuster Rocket Fist in flight
		EntityRendererRegistry.register(IronManEntityTypes.HULKBUSTER_FIST, com.projecthero.mod.client.ironman.HulkbusterFistRenderer::new);
		// v0.16.7 (user) Era II: the Hulkbuster I drop crate
		EntityRendererRegistry.register(IronManEntityTypes.HULKBUSTER_CRATE, com.projecthero.mod.client.ironman.HulkbusterCrateRenderer::new);
		// v0.16.7 (user) Veronica: the Hulkbuster's Replacement Pod / mid-air build pod, its plates, part damage FX
		EntityRendererRegistry.register(IronManEntityTypes.HULKBUSTER_POD, com.projecthero.mod.client.ironman.HulkbusterPodRenderer::new);
		com.projecthero.mod.client.ironman.HulkbusterVeronicaClient.initialize();
		com.projecthero.mod.client.ironman.HulkbusterBuildClient.initialize(); // v0.16.7 (user): the gantry-style build-on
		// v0.15.11: the hammer / wrench / carried piece of a hand-built suit-up
		com.projecthero.mod.client.ironman.IronManManualSuitUpLayer.initialize();
	}
}
