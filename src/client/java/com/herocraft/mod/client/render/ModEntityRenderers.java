package com.herocraft.mod.client.render;

import com.herocraft.mod.entity.ModEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class ModEntityRenderers {
	private ModEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(ModEntityTypes.MJOLNIR, MjolnirEntityRenderer::new);
		EntityRendererRegistry.register(com.herocraft.mod.maxsteel.entity.MaxSteelEntityTypes.STEEL,
				com.herocraft.mod.client.maxsteel.SteelRenderer::new);
		EntityRendererRegistry.register(com.herocraft.mod.maxsteel.entity.MaxSteelEntityTypes.TURBO_BOLT,
				TurboBoltRenderer::new);
		EntityRendererRegistry.register(com.herocraft.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE,
				com.herocraft.mod.client.symbiote.SymbioteEntityRenderer::new);
	}
}
