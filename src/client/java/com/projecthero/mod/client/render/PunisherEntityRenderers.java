package com.projecthero.mod.client.render;

import com.projecthero.mod.punisher.entity.PunisherEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** Client renderers for the Punisher's thrown entities. */
public final class PunisherEntityRenderers {
	private PunisherEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(PunisherEntityTypes.FRAG_GRENADE, ThrownItemRenderer::new);
		EntityRendererRegistry.register(PunisherEntityTypes.FLASHBANG, ThrownItemRenderer::new);
		// v0.15.18: the C4 ability is gone; a leftover charge from an old save removes itself at once, never drawn
		EntityRendererRegistry.register(PunisherEntityTypes.C4_CHARGE, NoopRenderer::new);
	}
}
