package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.network.MoonKnightKhonshuFxPayload;
import com.projecthero.mod.network.MoonKnightScholarSightPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Client wiring for Moon Knight Phases 5 and 6 (V Alters, Z Khonshu since v0.13.21): the radial alter picker, Scholar's Sight
 * outlines, the Moonbeam column, the Eye of Khonshu sky skull and the resurrection flash. One call from
 * {@code ProjectHeroModClient}.
 */
public final class MoonKnightAltersKhonshuClient {
	private MoonKnightAltersKhonshuClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(MoonKnightScholarSightPayload.TYPE,
				(payload, context) -> MoonKnightScholarSightRenderer.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(MoonKnightKhonshuFxPayload.TYPE,
				(payload, context) -> MoonKnightKhonshuFxClient.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			MoonKnightAlterPicker.tick(client);
			MoonKnightKhonshuFxClient.tick(client);
		});
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			MoonKnightKhonshuFxClient.render(context);
			MoonKnightScholarSightRenderer.render(context);
		});
		HudRenderCallback.EVENT.register(MoonKnightKhonshuFxClient::renderFlash);
		HudRenderCallback.EVENT.register(MoonKnightAlterPicker::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MoonKnightScholarSightRenderer.clear();
			MoonKnightKhonshuFxClient.clear();
		});
	}
}
