package com.projecthero.mod.client.quirk;

import com.projecthero.mod.hero.PowerSuppression;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * v0.16.7: the local player's Erased flag ({@link PowerSuppression#clientUntil}), from the server's
 * {@link PowerSuppression.SyncPayload}. The HUD's ERASED banner and every client check of
 * {@link PowerSuppression#isSuppressed} for the local player read it.
 */
public final class PowerSuppressionClient {
	private PowerSuppressionClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(PowerSuppression.SyncPayload.TYPE, (payload, context) -> {
			PowerSuppression.clientPlayer = context.player().getUUID();
			PowerSuppression.clientUntil = payload.until();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			PowerSuppression.clientUntil = 0L;
			PowerSuppression.clientPlayer = null;
		});
	}
}
