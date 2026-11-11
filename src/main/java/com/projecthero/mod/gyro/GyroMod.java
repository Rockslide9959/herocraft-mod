package com.projecthero.mod.gyro;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.gyro.entity.GyroEntities;
import com.projecthero.mod.gyro.network.GyroFxPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * v0.17.3 Gyro Zeppeli (the Spin): one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the
 * sounds, the Steel Ball entity, the item hooks and loot ({@link GyroLearning}), the damage rules, the effects payload and the
 * lifecycle hooks; plus {@code /projecthero power tune gyro (spin <0-100> | cooldowns)}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; the marks tick once a server tick here. Static state is dropped by {@code ServerStateReset}
 * through {@link #clearSessionState}.
 */
public final class GyroMod {
	private GyroMod() {
	}

	public static void initialize() {
		GyroEntities.initialize(); // (GyroSounds / GyroItems are initialised by ProjectHeroMod)
		GyroLearning.initialize();
		GyroDamage.initialize();
		PayloadTypeRegistry.playS2C().register(GyroFxPayload.TYPE, GyroFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> GyroMarks.tickAll());
		ServerPlayerEvents.JOIN.register(Gyro::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Gyro.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Gyro.hasPower(p)) {
				Gyro.clearTransient(p);
			} else if (entity instanceof AbstractHorse horse) {
				GyroValkyrie.onDeath(horse);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> GyroValkyrie.onLoad(entity));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Gyro.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Gyro.clearTransient(handler.getPlayer());
			Gyro.forget(handler.getPlayer().getUUID());
		});
	}

	public static void clearSessionState() {
		Gyro.clearSessionState();
	}

	/** {@code /projecthero power tune gyro ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("gyro")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("spin")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, GyroConfig.SPIN_MAX))
								.executes(c -> {
									ServerPlayer player = gyro(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Gyro.setSpin(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Spin set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = gyro(c.getSource());
					if (player == null) {
						return 0;
					}
					Gyro.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Spin cooldowns cleared"), false);
					return 1;
				}));
	}

	private static ServerPlayer gyro(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!Gyro.hasPower(player)) {
			source.sendFailure(Component.literal("You don't have the Spin"));
			return null;
		}
		return player;
	}
}
