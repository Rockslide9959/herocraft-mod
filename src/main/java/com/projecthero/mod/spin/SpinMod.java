package com.projecthero.mod.spin;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.spin.entity.SpinEntities;
import com.projecthero.mod.spin.network.SpinFxPayload;

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
 * v0.17.3 the Spin (the Spin): one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the
 * sounds, the Steel Ball entity, the item hooks and loot ({@link SpinLearning}), the damage rules, the effects payload and the
 * lifecycle hooks; plus {@code /projecthero power tune spin (spin <0-100> | cooldowns)}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; the marks tick once a server tick here. Static state is dropped by {@code ServerStateReset}
 * through {@link #clearSessionState}.
 */
public final class SpinMod {
	private SpinMod() {
	}

	public static void initialize() {
		SpinEntities.initialize(); // (SpinSounds / SpinItems are initialised by ProjectHeroMod)
		SpinLearning.initialize();
		SpinDamage.initialize();
		PayloadTypeRegistry.playS2C().register(SpinFxPayload.TYPE, SpinFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> SpinMarks.tickAll());
		ServerPlayerEvents.JOIN.register(Spin::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Spin.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Spin.hasPower(p)) {
				Spin.clearTransient(p);
			} else if (entity instanceof AbstractHorse horse) {
				SpinValkyrie.onDeath(horse);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> SpinValkyrie.onLoad(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
				SpinBalls.onItemEntityGone(item); // a destroyed ball comes back after 30 s
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Spin.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Spin.clearTransient(handler.getPlayer());
			Spin.forget(handler.getPlayer().getUUID());
		});
	}

	public static void clearSessionState() {
		Spin.clearSessionState();
	}

	/** {@code /projecthero power tune spin ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("spin")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("spin")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, SpinConfig.SPIN_MAX))
								.executes(c -> {
									ServerPlayer player = spin(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Spin.setSpin(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Spin set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("balls").executes(c -> {
					ServerPlayer player = spin(c.getSource());
					if (player == null) {
						return 0;
					}
					SpinBalls.replace(player);
					c.getSource().sendSuccess(() -> Component.literal("Gave you a new pair of Steel Balls (any older ones are gone)"), false);
					return 1;
				}))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = spin(c.getSource());
					if (player == null) {
						return 0;
					}
					Spin.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Spin cooldowns cleared"), false);
					return 1;
				}));
	}

	private static ServerPlayer spin(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!Spin.hasPower(player)) {
			source.sendFailure(Component.literal("You don't have the Spin"));
			return null;
		}
		return player;
	}
}
