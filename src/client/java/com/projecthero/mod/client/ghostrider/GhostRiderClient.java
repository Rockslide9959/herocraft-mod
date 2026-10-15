package com.projecthero.mod.client.ghostrider;

import com.projecthero.mod.ghostrider.GhostRider;
import com.projecthero.mod.ghostrider.entity.GhostRiderEntityTypes;
import com.projecthero.mod.ghostrider.item.MephistoContractItem;
import com.projecthero.mod.ghostrider.network.GhostRiderActionPayload;
import com.projecthero.mod.ghostrider.network.GhostRiderMarkPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.5: Ghost Rider's client side -- the skin layer (with the flaming skull), the HUD, the chain, the Judgement Mark
 * outlines, the Hell Cycle's renderer, the contract screen and the H request (sent from {@code ProjectHeroModClient}).
 */
public final class GhostRiderClient {
	private GhostRiderClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new GhostRiderLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(GhostRiderHud::render);
		EntityRendererRegistry.register(GhostRiderEntityTypes.HELL_CYCLE, HellCycleRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(GhostRiderChainRenderer::render);
		ClientPlayNetworking.registerGlobalReceiver(GhostRiderMarkPayload.TYPE, (payload, context) -> GhostRiderMarkClient.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GhostRiderMarkClient.reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				GhostRiderMarkClient.reset();
				return;
			}
			if (client.level.getGameTime() % 2 == 0) {
				GhostRiderChainRenderer.tickParticles(client);
			}
		});
		MephistoContractItem.openScreen = player -> Minecraft.getInstance().setScreen(new MephistoContractScreen());
	}

	/** Whether plain H belongs to Ghost Rider for this player. */
	public static boolean ownsH(LocalPlayer player) {
		return GhostRider.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new GhostRiderActionPayload(GhostRiderActionPayload.Action.TOGGLE_FORM));
	}
}
