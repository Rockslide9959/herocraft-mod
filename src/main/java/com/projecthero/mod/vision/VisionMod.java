package com.projecthero.mod.vision;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.vision.network.VisionActionPayload;

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
 * v0.17.3 Vision: one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the Synthezoid Cradle,
 * the damage rules, the flight request payload and the lifecycle hooks; plus {@code /projecthero power tune vision (energy <0-100> |
 * cooldowns)}. The per-player tick runs from {@code AbilityRouter.serverTick}. Static state is dropped by {@code ServerStateReset}
 * through {@link #clearSessionState}.
 */
public final class VisionMod {
	private VisionMod() {
	}

	public static void initialize() {
		com.projecthero.mod.vision.data.VisionAttachments.initialize();
		VisionCradle.initialize();
		VisionDamage.initialize();
		PayloadTypeRegistry.playC2S().register(VisionActionPayload.TYPE, VisionActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VisionActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			context.server().execute(() -> {
				if (payload.action() == VisionActionPayload.TOGGLE_FLIGHT && Vision.hasPower(p)) {
					VisionFlight.toggle(p);
				}
			});
		});
		ServerPlayerEvents.JOIN.register(Vision::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Vision.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Vision.hasPower(p)) {
				Vision.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Vision.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Vision.clearTransient(handler.getPlayer());
			Vision.forget(handler.getPlayer().getUUID());
		});
	}

	public static void clearSessionState() {
		Vision.clearSessionState();
	}

	/** {@code /projecthero power tune vision ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("vision")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("energy")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, VisionConfig.ENERGY_MAX))
								.executes(c -> {
									ServerPlayer player = vision(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Vision.setEnergy(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Mind Stone energy set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = vision(c.getSource());
					if (player == null) {
						return 0;
					}
					Vision.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Vision cooldowns cleared"), false);
					return 1;
				}));
	}

	private static ServerPlayer vision(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!Vision.hasPower(player)) {
			source.sendFailure(Component.literal("You aren't Vision"));
			return null;
		}
		return player;
	}
}
