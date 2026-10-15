package com.projecthero.mod.cyborg;

import com.projecthero.mod.cyborg.network.CyborgActionPayload;
import com.projecthero.mod.cyborg.network.CyborgScanPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.5: one entry point for the whole Cyborg power, called once from {@code ProjectHeroMod.onInitialize}: the damage
 * rules, the payloads (H in, the Scan out) and the lifecycle hooks. The Mother Box itself (the Darkseid raid's item) calls
 * {@link Cyborg#grant} when used on 4 hearts or less. The per-player tick runs from {@code AbilityRouter.serverTick};
 * static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class CyborgMod {
	private CyborgMod() {
	}

	public static void initialize() {
		CyborgDamage.initialize();
		PayloadTypeRegistry.playC2S().register(CyborgActionPayload.TYPE, CyborgActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CyborgScanPayload.TYPE, CyborgScanPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CyborgActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Cyborg.hasPower(p)) {
				return;
			}
			if (payload.action() == CyborgActionPayload.Action.TOGGLE_FORM) {
				Cyborg.toggleForm(p);
			} else if (payload.action() == CyborgActionPayload.Action.TOGGLE_FLIGHT) {
				// v0.16.5 (user): double-tap jump in the air takes off on the jetpack / drops out of flight
				if (Cyborg.state(p).hovering || !p.onGround()) {
					CyborgAbilities.toggleHover(p);
				}
			}
		});
		ServerPlayerEvents.JOIN.register(Cyborg::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Cyborg.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Cyborg.hasPower(p)) {
				Cyborg.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Cyborg.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Cyborg.clearTransient(handler.getPlayer()));
	}

	public static void clearSessionState() {
		Cyborg.clearSessionState();
	}
}
