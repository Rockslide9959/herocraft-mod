package com.projecthero.mod.miraculous;

import com.projecthero.mod.miraculous.network.MiraculousActionPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.6: one entry point for the Miraculous powers, called once from {@code ProjectHeroMod.onInitialize}: the core
 * (payloads, lifecycle) and the three kits. The per-player tick runs from {@code AbilityRouter.serverTick}; static
 * state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class MiraculousMod {
	private MiraculousMod() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(MiraculousActionPayload.TYPE, MiraculousActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MiraculousActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			switch (payload.action()) {
				case TOGGLE_FORM -> {
					if (Miraculous.hasPower(p)) {
						Miraculous.toggleForm(p);
					}
				}
			}
		});

		com.projecthero.mod.miraculous.ladybug.LadybugMod.initialize();
		com.projecthero.mod.miraculous.catnoir.CatNoirMod.initialize();
		com.projecthero.mod.miraculous.renarouge.RenaRougeMod.initialize();
		com.projecthero.mod.miraculous.hawkmoth.HawkMothMod.initialize();

		ServerPlayerEvents.JOIN.register(Miraculous::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Miraculous.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Miraculous.hasPower(p)) {
				Miraculous.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Miraculous.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Miraculous.clearTransient(handler.getPlayer()));
	}

	public static void clearSessionState() {
		Miraculous.clearSessionState();
	}
}
