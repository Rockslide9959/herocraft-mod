package com.projecthero.mod.falcon;

import com.projecthero.mod.falcon.entity.FalconEntityTypes;
import com.projecthero.mod.falcon.item.FalconItems;
import com.projecthero.mod.falcon.network.FalconActionPayload;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: one entry point for the whole Falcon power, called once from {@code ProjectHeroMod.onInitialize}: the gear, the
 * Redwing entity, the damage rules, the flight payload, the glide (a custom elytra while the wings are out) and the
 * lifecycle hooks. The per-player tick runs from {@code AbilityRouter.serverTick}; static state is dropped by
 * {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class FalconMod {
	private FalconMod() {
	}

	public static void initialize() {
		FalconItems.initialize();
		FalconEntityTypes.initialize();
		FalconDamage.initialize();
		PayloadTypeRegistry.playC2S().register(FalconActionPayload.TYPE, FalconActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FalconActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Falcon.hasPower(p) || com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) { // Erased
				return;
			}
			switch (payload.action()) {
				case TAKE_OFF -> Falcon.takeOff(p);
				case FLAP -> Falcon.flap(p);
				case BOOST_ON -> Falcon.setBoost(p, true);
				case BOOST_OFF -> Falcon.setBoost(p, false);
			}
		});
		// the wings are a custom elytra while they are out (both sides read the synced state)
		EntityElytraEvents.CUSTOM.register((entity, tickElytra) -> entity instanceof Player pl && Falcon.flying(pl) && !pl.getAbilities().flying);
		ServerPlayerEvents.JOIN.register(Falcon::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Falcon.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Falcon.peek(p) != null) {
				Falcon.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Falcon.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Falcon.clearTransient(handler.getPlayer()));
	}

	public static void clearSessionState() {
		Falcon.clearSessionState();
	}
}
