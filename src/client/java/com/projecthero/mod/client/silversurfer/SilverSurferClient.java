package com.projecthero.mod.client.silversurfer;

import com.projecthero.mod.silversurfer.SilverSurferMod;
import com.projecthero.mod.silversurfer.network.SurferStatuePayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.1: the Silver Surfer's client side -- the skin / glow / board layer, the HUD, the beam / barrier / streak, the
 * thrown board's renderer, Transmutation's statues and Cosmic Awareness. The skin swap, the poses, the banking, the statue
 * silver, the awareness glow and the phase are mixins ({@code SilverSurfer*Mixin}); double-tap jump is sent from
 * {@code ProjectHeroModClient}.
 */
public final class SilverSurferClient {
	private SilverSurferClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new SilverSurferLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(SilverSurferMod.THROWN_BOARD, ThrownSurfboardRenderer::new);
		HudRenderCallback.EVENT.register(SilverSurferHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(SilverSurferEffects::render);
		ClientPlayNetworking.registerGlobalReceiver(SurferStatuePayload.TYPE, (payload, context) -> SilverSurferClientState.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SilverSurferClientState.tick(client);
			SilverSurferAwareness.tick(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SilverSurferClientState.reset());
	}
}
