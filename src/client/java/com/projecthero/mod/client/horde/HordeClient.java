package com.projecthero.mod.client.horde;

import com.projecthero.mod.horde.entity.HordeEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * v0.14.12: the Horde mobs' renderers. v0.14.16: both bosses (Bone Tyrant, Brood Queen) are GeckoLib models with their
 * own renderers; the Horde Spider and the brood variants keep the vanilla spider mesh.
 */
public final class HordeClient {
	private HordeClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(HordeEntityTypes.HORDE_SPIDER, SpiderRenderer::new);
		// v0.14.16: the Bone Tyrant is a GeckoLib model now; it and the Skeleton Horde's own mobs are registered here
		SkeletonHordeClient.initialize();
		// v0.14.16: the Brood Queen is a GeckoLib boss now; her brood variants share the vanilla spider mesh
		EntityRendererRegistry.register(HordeEntityTypes.BROOD_QUEEN, BroodQueenRenderer::new);
		EntityRendererRegistry.register(HordeEntityTypes.BROOD_SPIDER, BroodSpiderRenderer::new);
		EntityRendererRegistry.register(HordeEntityTypes.WEB_SHOT, ThrownItemRenderer::new);
	}
}
