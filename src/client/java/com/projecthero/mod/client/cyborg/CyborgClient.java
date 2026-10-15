package com.projecthero.mod.client.cyborg;

import com.projecthero.mod.cyborg.Cyborg;
import com.projecthero.mod.cyborg.network.CyborgActionPayload;
import com.projecthero.mod.cyborg.network.CyborgScanPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.5: Cyborg's client side -- the plates layer, the HUD, the cannon beam / Defense Matrix, the Scan outlines and
 * the H request (sent from {@code ProjectHeroModClient}). The Scan's ore boxes reuse Moon Knight's Scholar's Sight renderer.
 */
public final class CyborgClient {
	private CyborgClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new CyborgLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(CyborgHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(CyborgEffectsRenderer::render);
		ClientPlayNetworking.registerGlobalReceiver(CyborgScanPayload.TYPE, (payload, context) -> CyborgScanClient.receive(payload));
		// v0.16.5: Shift+V -- the Boom Tube menu (the Bifrost's screen in Mother Box colours)
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.cyborg.network.CyborgBoomScreenPayload.TYPE,
				(payload, context) -> context.client().execute(() -> com.projecthero.mod.client.gui.BifrostScreen.receiveBoomTube(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CyborgTech.clearSessionState());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CyborgScanClient.reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				CyborgScanClient.reset();
			}
		});
	}

	public static boolean ownsH(LocalPlayer player) {
		return Cyborg.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new CyborgActionPayload(CyborgActionPayload.Action.TOGGLE_FORM));
	}
}
