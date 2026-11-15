package com.projecthero.mod.client.gear;

import com.projecthero.mod.gear.GearItems;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.item.Item;

/** v0.17.5 gear tiers, client side: the grapple hook + cable, thrown gadgets, the (invisible) smoke cloud, Celestial glow. */
public final class GearClient {
	private GearClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(GearItems.GRAPPLE_HOOK, GrappleHookRenderer::new);
		EntityRendererRegistry.register(GearItems.THROWN_GADGET, ThrownItemRenderer::new);
		EntityRendererRegistry.register(GearItems.SMOKE_CLOUD, NoopRenderer::new);
		ArmorRenderer.register(new CelestialArmorRenderer(), GearItems.celestialArmor().toArray(new Item[0]));
	}
}
