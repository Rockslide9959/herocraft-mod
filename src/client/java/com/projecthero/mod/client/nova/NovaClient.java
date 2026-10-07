package com.projecthero.mod.client.nova;

import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.nova.network.NovaActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.15.13: Nova's client side -- the uniform layer, the HUD, the world effects (beam, shield, Gravity Well), the
 * Worldmind outlines, the Centurion's renderer, and the H / double-tap-jump requests (sent from
 * {@code ProjectHeroModClient}).
 */
public final class NovaClient {
	private NovaClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new NovaSuitLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(NovaHud::render);
		NovaEffectsRenderer.init();
		NovaWorldmindClient.init();
		NovaCenturionRenderer.initialize();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NovaWorldmindClient.reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				NovaWorldmindClient.reset();
			}
		});
	}

	/** Whether plain H belongs to Nova for this player (he carries the Nova Force). */
	public static boolean ownsH(LocalPlayer player) {
		return Nova.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new NovaActionPayload(NovaActionPayload.Action.TOGGLE_SUIT));
	}

	/** The double-tap jump in the air while suited (the server re-validates). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return !player.onGround() && Nova.suited(player);
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new NovaActionPayload(NovaActionPayload.Action.TOGGLE_FLIGHT));
	}
}
