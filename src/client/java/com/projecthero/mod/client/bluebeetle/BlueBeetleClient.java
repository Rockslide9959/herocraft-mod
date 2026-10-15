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
 * v0.16.5: Blue Beetle's client side -- the armour layer (wings, blade, shield), the HUD (and the bonding flash), the
 * cannon beam / Bubble Prison, the drones' renderer and the H request (sent from {@code ProjectHeroModClient}).
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
		WorldRenderEvents.AFTER_ENTITIES.register(BlueBeetleEffectsRenderer::render);
		EntityRendererRegistry.register(BlueBeetleEntityTypes.SCARAB_DRONE, ScarabDroneRenderer::new);
	}

	public static boolean ownsH(LocalPlayer player) {
		return BlueBeetle.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new BlueBeetleActionPayload(BlueBeetleActionPayload.Action.TOGGLE_SUIT));
	}
}
