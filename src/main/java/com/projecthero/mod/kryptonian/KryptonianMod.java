package com.projecthero.mod.kryptonian;

import com.projecthero.mod.kryptonian.item.KryptonianItems;
import com.projecthero.mod.kryptonian.meteor.MeteorManager;
import com.projecthero.mod.kryptonian.network.KryptonianActionPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/**
 * v0.14.8: one entry point for the whole Kryptonian power, called once from {@code ProjectHeroMod.onInitialize}: the
 * meteor's blocks / items / entity, the damage rules, the flight-toggle payload, the lifecycle hooks (join, respawn,
 * death, world change, logout) and the Kryptonite Meteor's nightly roll. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset}.
 */
public final class KryptonianMod {
	private KryptonianMod() {
	}

	public static void initialize() {
		KryptonianItems.initialize();
		KryptonianDamage.initialize();

		PayloadTypeRegistry.playC2S().register(KryptonianActionPayload.TYPE, KryptonianActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KryptonianActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Kryptonian.hasPower(p)) {
				return;
			}
			if (payload.action() == KryptonianActionPayload.Action.TOGGLE_FLIGHT) {
				// v0.14.16: flight costs 0.1 Solar Energy a second -- an empty bar cannot take off
				if (!Kryptonian.isFlying(p) && Kryptonian.solar(p) <= 0f && Kryptonian.empowered(p)) {
					p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.kryptonian.flight_no_solar")
							.withStyle(net.minecraft.ChatFormatting.RED), true);
					return;
				}
				KryptonianFlight.toggle(p);
			}
		});

		// v0.14.16: attacking the creature you are carrying (Shift+V) throws it instead of hitting it
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && KryptonianAbilities.isCarriedBy(sp, entity)) {
				KryptonianAbilities.throwHeld(sp);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		ServerPlayerEvents.JOIN.register(Kryptonian::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Kryptonian.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Kryptonian.hasPower(p)) {
				KryptonianFlight.stop(p, false);
				Kryptonian.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Kryptonian.onWorldChange(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Kryptonian.clearTransient(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(MeteorManager::tick);
	}

	/** Every static scratch collection of the power (called by {@code ServerStateReset}). */
	public static void clearSessionState() {
		Kryptonian.clearSessionState();
		MeteorManager.clearSessionState();
	}
}
