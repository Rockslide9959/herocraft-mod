package com.projecthero.mod.spider;

import com.projecthero.mod.network.SpiderFreerunPayload;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.2: wiring for Spider-Man's freerunning -- {@link SpiderWallRun} and {@link SpiderTricks}: their payload, their
 * per-tick upkeep and their session state.
 */
public final class SpiderFreerun {
	private SpiderFreerun() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(SpiderFreerunPayload.TYPE, SpiderFreerunPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpiderFreerunPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			switch (payload.action()) {
				case SpiderFreerunPayload.WALL_RUN_START -> SpiderWallRun.start(p, payload.arg());
				case SpiderFreerunPayload.WALL_RUN_STOP -> SpiderWallRun.stop(p);
				case SpiderFreerunPayload.TRICK -> SpiderTricks.tryTrick(p, payload.arg());
				default -> {
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (SpiderMan.hasPower(p)) {
					SpiderTricks.serverTick(p);
					SpiderWallRun.serverTick(p);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SpiderTricks.remove(handler.player.getUUID());
			SpiderWallRun.remove(handler.player.getUUID());
		});
	}

	/** From {@code ServerStateReset}. */
	public static void clearSessionState() {
		SpiderTricks.clearSessionState();
		SpiderWallRun.clearSessionState();
	}
}
