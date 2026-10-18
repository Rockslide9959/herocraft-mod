package com.projecthero.mod.spider;

import com.projecthero.mod.network.SpiderGlidePayload;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * v0.16.2: wiring for the Spider-Man Suit v2 -- its Web Wings glide ({@link SpiderWebWings}) and Trip Mine
 * ({@link SpiderTripMines}). The items and the upgrade recipe live in {@code spider.item}.
 */
public final class SpiderSuitV2 {
	private SpiderSuitV2() {
	}

	public static void initialize() {
		SpiderTripMines.initialize();
		PayloadTypeRegistry.playC2S().register(SpiderGlidePayload.TYPE, SpiderGlidePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpiderGlidePayload.TYPE,
				(payload, context) -> {
					if (payload.gliding() && com.projecthero.mod.hero.PowerSuppression.refuse(context.player())) { // v0.16.7: Erased
						return;
					}
					SpiderWebWings.request(context.player(), payload.gliding());
				});
		ServerTickEvents.END_SERVER_TICK.register(SpiderWebWings::tick);
	}
}
