package com.projecthero.mod.starfire;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.starfire.item.StarfireItems;
import com.projecthero.mod.starfire.network.StarfireActionPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.3 Starfire: one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the items, the
 * Starstone hooks and loot ({@link StarfireLearning}), the damage rules, the flight request payload, the bolts' hit handler, the
 * light cleanup and the lifecycle hooks; plus {@code /projecthero power tune starfire (starbolt <0-100> | mood <-100..100> |
 * cooldowns)}. The per-player tick runs from {@code AbilityRouter.serverTick}. Static state is dropped by {@code ServerStateReset}
 * through {@link #clearSessionState}.
 */
public final class StarfireMod {
	private StarfireMod() {
	}

	public static void initialize() {
		StarfireItems.initialize();
		StarfireLearning.initialize();
		StarfireDamage.initialize();
		StarfireMoves.initialize();
		StarfireRadiance.initialize();
		PayloadTypeRegistry.playC2S().register(StarfireActionPayload.TYPE, StarfireActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StarfireActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			context.server().execute(() -> {
				if (payload.action() == StarfireActionPayload.TOGGLE_FLIGHT && Starfire.hasPower(p)) {
					StarfireFlight.toggle(p);
				}
			});
		});
		ServerTickEvents.END_SERVER_TICK.register(StarfireMoves::serverTick);
		ServerPlayerEvents.JOIN.register(Starfire::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Starfire.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Starfire.hasPower(p)) {
				Starfire.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Starfire.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Starfire.clearTransient(handler.getPlayer());
			Starfire.forget(handler.getPlayer().getUUID());
		});
	}

	public static void clearSessionState() {
		Starfire.clearSessionState();
	}

	/** {@code /projecthero power tune starfire ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("starfire")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("starbolt")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, StarfireConfig.STARBOLT_MAX))
								.executes(c -> {
									ServerPlayer player = starfire(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Starfire.setStarbolt(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Starbolt set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("mood")
						.then(Commands.argument("value", FloatArgumentType.floatArg(-StarfireConfig.MOOD_MAX, StarfireConfig.MOOD_MAX))
								.executes(c -> {
									ServerPlayer player = starfire(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "value");
									Starfire.setMood(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Mood set to " + Math.round(v) + " (-100 Joy, 100 Fury)"), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = starfire(c.getSource());
					if (player == null) {
						return 0;
					}
					Starfire.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Starfire cooldowns cleared"), false);
					return 1;
				}));
	}

	private static ServerPlayer starfire(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!Starfire.hasPower(player)) {
			source.sendFailure(Component.literal("You aren't Starfire"));
			return null;
		}
		return player;
	}
}
