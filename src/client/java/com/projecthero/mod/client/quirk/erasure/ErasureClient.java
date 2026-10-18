package com.projecthero.mod.client.quirk.erasure;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.erasure.ErasureGogglesPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Erasure Quirk's client side -- the poses ({@link ErasurePose}), the risen hair / red eyes / goggles
 * ({@link ErasureLayer}), the capture scarf in the world ({@link ErasureWorld}), the swing / zip movement
 * ({@link ErasureSwingClient}) and the screen effects: the red vignette while YOU are erased, the blink, the goggles
 * ({@link ErasureHud}; the outlines are {@code ErasureGogglesGlowMixin}).
 */
public final class ErasureClient implements QuirkClientKit {
	private ErasureClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.ERASURE, new ErasureClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new ErasureLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(ErasureWorld::render);
		HudRenderCallback.EVENT.register(ErasureHud::render);
		ClientTickEvents.START_CLIENT_TICK.register(ErasureSwingClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ErasureGogglesPayload.TYPE, (payload, context) -> ErasureHud.receive(payload.ids()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ErasureHud.reset();
			ErasureSwingClient.reset();
			ErasureWorld.reset();
		});
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		ErasurePose.apply(player, model);
	}
}
