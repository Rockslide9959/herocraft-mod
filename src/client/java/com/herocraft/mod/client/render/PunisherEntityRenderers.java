package com.herocraft.mod.client.render;

import com.herocraft.mod.punisher.entity.PunisherEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** Client renderers for the Punisher's Frag Grenade and Explosive Charge entities. */
public final class PunisherEntityRenderers {
	private PunisherEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(PunisherEntityTypes.FRAG_GRENADE, ThrownItemRenderer::new);
		EntityRendererRegistry.register(PunisherEntityTypes.C4_CHARGE, C4ChargeRenderer::new);
	}
}
