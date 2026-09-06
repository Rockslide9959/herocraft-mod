package com.projecthero.mod.client.render;

import com.projecthero.mod.ironman.entity.IronManEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class IronManEntityRenderers {
	private IronManEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(IronManEntityTypes.SUIT_PART, IronManSuitPartRenderer::new);
		EntityRendererRegistry.register(IronManEntityTypes.MISSILE, IronManMissileRenderer::new);
	}
}
