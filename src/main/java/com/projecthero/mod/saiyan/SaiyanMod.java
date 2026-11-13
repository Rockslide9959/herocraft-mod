package com.projecthero.mod.saiyan;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.saiyan.item.SaiyanItems;
import com.projecthero.mod.saiyan.network.SaiyanActionPayload;

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
 * v0.17.3 Saiyan revamp: one entry point for the whole Saiyan power, called once from {@code ProjectHeroMod.onInitialize}: Saiyan
 * Blood, the damage rules, the payload and the lifecycle hooks; plus
 * {@code /projecthero power tune saiyan (ki <0-100> | rage <0-100> | ssj | end_ssj | kaioken)}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class SaiyanMod {
	private SaiyanMod() {
	}

	public static void initialize() {
		SaiyanItems.initialize();
		SaiyanDamage.initialize();
		PayloadTypeRegistry.playC2S().register(SaiyanActionPayload.TYPE, SaiyanActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SaiyanActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Saiyan.hasPower(p)) {
				return;
			}
			if (com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) { // Erased
				return;
			}
			switch (payload.action()) {
				case CHARGE_STOP -> Saiyan.setCharging(p, false);
				case CHARGE_START -> Saiyan.setCharging(p, true);
				case TOGGLE_FLIGHT -> SaiyanFlight.toggle(p);
				case TRANSFORM -> Saiyan.transform(p);
			}
		});
		ServerPlayerEvents.JOIN.register(Saiyan::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Saiyan.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Saiyan.hasPower(p)) {
				Saiyan.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Saiyan.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Saiyan.clearTransient(handler.getPlayer());
			Saiyan.forget(handler.getPlayer().getUUID());
		});
	}

	public static void clearSessionState() {
		Saiyan.clearSessionState();
	}

	/** {@code /projecthero power tune saiyan ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("saiyan")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("ki")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, SaiyanConfig.KI_MAX))
								.executes(c -> {
									ServerPlayer player = saiyan(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Saiyan.setKi(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Ki set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("rage")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, SaiyanConfig.RAGE_MAX))
								.executes(c -> {
									ServerPlayer player = saiyan(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Saiyan.setRage(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Rage set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("ssj").executes(c -> {
					ServerPlayer player = saiyan(c.getSource());
					if (player == null) {
						return 0;
					}
					Saiyan.clearLockout(player);
					Saiyan.setRage(player, SaiyanConfig.RAGE_MAX);
					return Saiyan.transform(player) ? 1 : 0;
				}))
				.then(Commands.literal("end_ssj").executes(c -> {
					ServerPlayer player = saiyan(c.getSource());
					if (player == null) {
						return 0;
					}
					Saiyan.forceEndSsj(player);
					return 1;
				}))
				.then(Commands.literal("kaioken").executes(c -> {
					ServerPlayer player = saiyan(c.getSource());
					if (player == null) {
						return 0;
					}
					if (Saiyan.kaioken(player)) {
						Saiyan.stopKaioken(player, true);
						return 1;
					}
					return Saiyan.startKaioken(player) ? 1 : 0;
				}))
				// v0.17.3 (op, testing the secret form; not in the Guidebook): unlock Ultra Instinct and enter it now
				.then(Commands.literal("ultra").executes(c -> {
					ServerPlayer player = saiyan(c.getSource());
					if (player == null) {
						return 0;
					}
					if (!SaiyanUltraInstinct.unlocked(player)) {
						SaiyanUltraInstinct.unlock(player);
					}
					SaiyanUltraInstinct.clearLockout(player);
					if (!Saiyan.ssj(player)) {
						Saiyan.clearLockout(player);
						Saiyan.setRage(player, SaiyanConfig.RAGE_MAX);
						Saiyan.transform(player);
					}
					Saiyan.setKi(player, SaiyanConfig.KI_MAX);
					return SaiyanUltraInstinct.enter(player) ? 1 : 0;
				}));
	}

	private static ServerPlayer saiyan(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!Saiyan.hasPower(player)) {
			source.sendFailure(Component.literal("You are not a Saiyan"));
			return null;
		}
		return player;
	}
}
