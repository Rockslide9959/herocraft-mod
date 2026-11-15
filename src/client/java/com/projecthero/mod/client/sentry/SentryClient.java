package com.projecthero.mod.client.sentry;

import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.5 Sentry &amp; the Void, the client side (called once from {@code ProjectHeroModClient}): the suit / Void Form layer, the HUD
 * and the Void's thresholds, the effects (the Sunbeam and every {@code SentryFxPayload}), the takeover view (camera, input, the Shame
 * Rooms, the dark sky) and the block render layers. X, V and the rest go through the shared ability keys; H stays the power selector.
 */
public final class SentryClient {
	private SentryClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new SentrySuitLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(SentryHud::render);
		HudRenderCallback.EVENT.register(VoidTakeoverClient::renderHud);
		HudRenderCallback.EVENT.register(SentryFxClient::renderFlash);
		ClientPlayNetworking.registerGlobalReceiver(SentryFxPayload.TYPE, (payload, context) -> SentryFxClient.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(SentryFxClient::render);
		ClientTickEvents.START_CLIENT_TICK.register(VoidTakeoverClient::startTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SentryFxClient.reset();
			VoidTakeoverClient.reset();
		});
		BlockRenderLayerMap.INSTANCE.putBlock(com.projecthero.mod.sentry.block.SentryBlocks.BURNT_SHADOW, RenderType.translucent());
		BlockRenderLayerMap.INSTANCE.putBlock(com.projecthero.mod.sentry.block.SentryBlocks.SERUM_VAT, RenderType.cutout());
	}
}
