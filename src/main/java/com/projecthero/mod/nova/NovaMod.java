package com.projecthero.mod.nova;

import com.projecthero.mod.nova.item.NovaItems;
import com.projecthero.mod.nova.network.NovaActionPayload;
import com.projecthero.mod.nova.network.NovaScanPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.15.13: one entry point for the whole Nova power, called once from {@code ProjectHeroMod.onInitialize}: the helmet,
 * the Centurion, the damage rules, the payloads (H / flight in, the Worldmind Scan out) and the lifecycle hooks (join,
 * respawn, death, world change, logout). The per-player tick runs from {@code AbilityRouter.serverTick}; static state is
 * dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class NovaMod {
	private NovaMod() {
	}

	public static void initialize() {
		NovaItems.initialize();
		NovaDamage.initialize();

		PayloadTypeRegistry.playC2S().register(NovaActionPayload.TYPE, NovaActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NovaScanPayload.TYPE, NovaScanPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NovaActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Nova.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case TOGGLE_SUIT -> Nova.toggleSuit(p);
				case TOGGLE_FLIGHT -> NovaFlight.toggle(p);
				case HELMET_REMOVE_START -> Nova.helmetRemoveStart(p);
				case HELMET_REMOVE_STOP -> Nova.helmetRemoveStop(p);
			}
		});

		ServerPlayerEvents.JOIN.register(Nova::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Nova.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Nova.hasPower(p)) {
				NovaFlight.stop(p, false);
				Nova.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Nova.onWorldChange(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Nova.clearTransient(handler.getPlayer()));
	}

	/** Every static scratch collection of the power (called by {@code ServerStateReset}). */
	public static void clearSessionState() {
		Nova.clearSessionState();
	}
}
