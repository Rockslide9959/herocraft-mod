package com.projecthero.mod.client.spider;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** v0.16.2: client wiring for the Wall Run ({@link SpiderWallRunClient}) and the swing tricks ({@link SpiderTrickClient}). */
public final class SpiderFreerunClient {
	private SpiderFreerunClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			SpiderWallRunClient.clientTick(mc);
			SpiderTrickClient.clientTick(mc);
			if (mc.player == null) {
				SpiderWallRunPose.clear();
			}
		});
		HudRenderCallback.EVENT.register(SpiderTrickClient::renderHud);
	}
}
