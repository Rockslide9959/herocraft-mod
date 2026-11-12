package com.projecthero.mod.raven;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.raven.entity.RavenEntities;
import com.projecthero.mod.raven.network.RavenActionPayload;
import com.projecthero.mod.raven.network.RavenFxPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/**
 * v0.17.3 Raven (Teen Titans): one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the
 * entities, the Book of Azarath (use hook + loot, {@link RavenLearning}), the damage rules, the two payloads, meditation's "no
 * attacking", the block-restore clock and the lifecycle hooks; plus {@code /projecthero power tune raven (energy|composure <0-100> |
 * cooldowns)}. The per-player tick runs from {@code AbilityRouter.serverTick}. Static state is dropped by {@code ServerStateReset}
 * through {@link #clearSessionState}.
 */
public final class RavenMod {
	private RavenMod() {
	}

	public static void initialize() {
		RavenItems.initialize();
		RavenEntities.initialize();
		RavenLearning.initialize();
		RavenDamage.initialize();
		PayloadTypeRegistry.playS2C().register(RavenFxPayload.TYPE, RavenFxPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RavenActionPayload.TYPE, RavenActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RavenActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			context.server().execute(() -> {
				if (!RavenAbilityManager.hasContext(p)) {
					return;
				}
				switch (payload.action()) {
					case TOGGLE_FLIGHT -> RavenFlight.toggle(p);
					case FIRE_DEBRIS -> RavenMoves.fireDebris(p);
				}
			});
		});
		// meditating: no attacking
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				Raven.meditating(player) && !player.isSpectator() ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) ->
				Raven.meditating(player) && !player.isSpectator() ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(RavenRestore::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RavenRestore.restoreAll()); // torn-up blocks go home first
		ServerPlayerEvents.JOIN.register(Raven::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Raven.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Raven.hasPower(p)) {
				Raven.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Raven.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Raven.clearTransient(handler.getPlayer());
			Raven.forget(handler.getPlayer().getUUID());
		});
	}

	public static void clearSessionState() {
		Raven.clearSessionState();
	}

	/** {@code /projecthero power tune raven ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("raven")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("energy")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, RavenConfig.ENERGY_MAX))
								.executes(c -> {
									ServerPlayer player = raven(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Raven.setEnergy(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Dark Energy set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("composure")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, RavenConfig.COMPOSURE_MAX))
								.executes(c -> {
									ServerPlayer player = raven(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Raven.setComposure(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Composure set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = raven(c.getSource());
					if (player == null) {
						return 0;
					}
					Raven.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Raven cooldowns cleared"), false);
					return 1;
				}));
	}

	private static ServerPlayer raven(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!Raven.hasPower(player)) {
			source.sendFailure(Component.literal("You aren't Raven"));
			return null;
		}
		return player;
	}
}
