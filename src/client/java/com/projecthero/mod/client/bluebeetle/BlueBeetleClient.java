package com.projecthero.mod.client.bluebeetle;

import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.entity.BlueBeetleEntityTypes;
import com.projecthero.mod.bluebeetle.network.BlueBeetleActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.5: Blue Beetle's client side -- the armour layer (wings, blade, shield, the voxel Scarab and arm tech), the HUD,
 * the world effects (cannon beam, Bubble Prison, tentacles, spikes), the drone and plasma bolt renderers, and the H /
 * double-tap-jump requests (sent from {@code ProjectHeroModClient}).
 */
public final class BlueBeetleClient {
	private BlueBeetleClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new BlueBeetleLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(BlueBeetleHud::render);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(BlueBeetleTakeoverClient::startTick);
		WorldRenderEvents.AFTER_ENTITIES.register(BlueBeetleEffectsRenderer::render);
		EntityRendererRegistry.register(BlueBeetleEntityTypes.SCARAB_DRONE, ScarabDroneRenderer::new);
		EntityRendererRegistry.register(BlueBeetleEntityTypes.PLASMA_BOLT, PlasmaBoltRenderer::new);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlueBeetleTech.clearSessionState());
	}

	public static boolean ownsH(LocalPlayer player) {
		return BlueBeetle.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new BlueBeetleActionPayload(BlueBeetleActionPayload.Action.TOGGLE_SUIT));
	}

	/** v0.16.5 (user): double-tap jump in the air while suited takes off / drops out of flight (the server re-checks). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return !player.onGround() && BlueBeetle.suited(player);
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new BlueBeetleActionPayload(BlueBeetleActionPayload.Action.TOGGLE_FLIGHT));
	}
}
