package com.projecthero.mod.client.loot.finds;

import com.projecthero.mod.loot.finds.FindsMod;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** v0.17.5 Tier 1 Finds, client side: the falling Supply Drop's renderer. */
public final class FindsClient {
	private FindsClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(FindsMod.SUPPLY_DROP, SupplyDropRenderer::new);
	}
}
