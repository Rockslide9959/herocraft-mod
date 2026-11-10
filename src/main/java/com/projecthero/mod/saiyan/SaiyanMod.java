package com.projecthero.mod.saiyan;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
 * v0.17.3: one entry point for the whole Saiyan power, called once from {@code ProjectHeroMod.onInitialize}: the gamerule,
 * Saiyan Blood, the damage rules, the payload and the lifecycle hooks; plus
 * {@code /projecthero power tune saiyan (ki <0-100> | form <name> | unlock_all)}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class SaiyanMod {
	private SaiyanMod() {
	}

	public static void initialize() {
		Saiyan.registerRules();
		SaiyanItems.initialize();
		SaiyanDamage.initialize();
		PayloadTypeRegistry.playC2S().register(SaiyanActionPayload.TYPE, SaiyanActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SaiyanActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Saiyan.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case CHARGE_STOP -> Saiyan.setCharging(p, false);
				case CHARGE_START -> {
					if (!com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) { // Erased
						Saiyan.setCharging(p, true);
					}
				}
				case TOGGLE_FLIGHT -> {
					if (!com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) {
						SaiyanFlight.toggle(p);
					}
				}
				case PICK_FORM -> {
					if (!com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) {
						Saiyan.transform(p, SaiyanForm.byOrdinal(payload.arg()));
					}
				}
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
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!Saiyan.hasPower(player)) {
										c.getSource().sendFailure(Component.literal("You are not a Saiyan"));
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									Saiyan.setKi(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Ki set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("unlock_all").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					if (!Saiyan.hasPower(player)) {
						c.getSource().sendFailure(Component.literal("You are not a Saiyan"));
						return 0;
					}
					Saiyan.unlockAll(player);
					c.getSource().sendSuccess(() -> Component.literal("Every Saiyan form unlocked"), false);
					return 1;
				}))
				.then(Commands.literal("form")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests((c, b) -> {
									for (SaiyanForm f : SaiyanForm.values()) {
										b.suggest(f.key);
									}
									return b.buildFuture();
								})
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									SaiyanForm f = SaiyanForm.byKey(StringArgumentType.getString(c, "name"));
									if (!Saiyan.hasPower(player) || f == null) {
										c.getSource().sendFailure(Component.literal("Not a Saiyan, or no such form"));
										return 0;
									}
									return Saiyan.transform(player, f) ? 1 : 0;
								})));
	}
}
