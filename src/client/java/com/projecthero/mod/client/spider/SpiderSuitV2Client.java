package com.projecthero.mod.client.spider;

import com.projecthero.mod.spider.SpiderTripMines;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** v0.16.2: client wiring for the Spider-Man Suit v2 -- the Trip Mine's model / renderer and the Web Wings' tick. */
public final class SpiderSuitV2Client {
	private SpiderSuitV2Client() {
	}

	public static void initialize() {
		EntityModelLayerRegistry.registerModelLayer(SpiderTripMineRenderer.LAYER, SpiderTripMineRenderer::createLayer);
		EntityRendererRegistry.register(SpiderTripMines.TRIP_MINE_ENTITY, SpiderTripMineRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(SpiderWebWingsClient::clientTick);
	}
}
