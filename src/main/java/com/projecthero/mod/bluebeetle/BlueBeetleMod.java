package com.projecthero.mod.bluebeetle;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projecthero.mod.bluebeetle.entity.BlueBeetleEntityTypes;
import com.projecthero.mod.bluebeetle.item.BlueBeetleItems;
import com.projecthero.mod.bluebeetle.network.BlueBeetleActionPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.5: one entry point for the whole Blue Beetle power, called once from {@code ProjectHeroMod.onInitialize}: the
 * Scarab (+ its loot), the drone and bolt entities, the damage rules, camouflage's break-on-touch hooks, the H /
 * double-tap-jump payload and the lifecycle hooks; plus the
 * {@code /projecthero power tune blue_beetle energy <0-100>} test command. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class BlueBeetleMod {
	private BlueBeetleMod() {
	}

	public static void initialize() {
		BlueBeetleItems.initialize();
		BlueBeetleEntityTypes.initialize();
		BlueBeetleDamage.initialize();
		BlueBeetleAbilities.registerCamoBreakers();
		PayloadTypeRegistry.playC2S().register(BlueBeetleActionPayload.TYPE, BlueBeetleActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlueBeetleActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!BlueBeetle.hasPower(p) || com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) { // v0.16.7: Erased
				return;
			}
			if (payload.action() == BlueBeetleActionPayload.Action.TOGGLE_SUIT) {
				BlueBeetle.toggleSuit(p);
			} else if (payload.action() == BlueBeetleActionPayload.Action.TOGGLE_FLIGHT) {
				// v0.16.5 (user): double-tap jump in the air takes off / drops out of flight
				BlueBeetle.toggleFlight(p);
			}
		});
		ServerPlayerEvents.JOIN.register(BlueBeetle::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> BlueBeetle.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && BlueBeetle.hasPower(p)) {
				BlueBeetle.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> BlueBeetle.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BlueBeetle.clearTransient(handler.getPlayer()));
	}

	public static void clearSessionState() {
		BlueBeetle.clearSessionState();
	}

	/** {@code /projecthero power tune blue_beetle energy <0-100>} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("blue_beetle")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("energy")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, BlueBeetleConfig.ENERGY_MAX))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!BlueBeetle.hasPower(player)) {
										c.getSource().sendFailure(Component.literal("You are not bonded to the Scarab"));
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									BlueBeetle.setEnergy(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Scarab Energy set to " + Math.round(v)), false);
									return 1;
								})));
	}
}
