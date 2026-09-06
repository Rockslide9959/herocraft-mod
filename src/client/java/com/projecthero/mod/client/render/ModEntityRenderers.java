package com.projecthero.mod.client.render;

import com.projecthero.mod.entity.ModEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class ModEntityRenderers {
	private ModEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(ModEntityTypes.MJOLNIR, MjolnirEntityRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.maxsteel.entity.MaxSteelEntityTypes.STEEL,
				com.projecthero.mod.client.maxsteel.SteelRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.maxsteel.entity.MaxSteelEntityTypes.TURBO_BOLT,
				TurboBoltRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE,
				com.projecthero.mod.client.symbiote.SymbioteEntityRenderer::new);
	}
}
