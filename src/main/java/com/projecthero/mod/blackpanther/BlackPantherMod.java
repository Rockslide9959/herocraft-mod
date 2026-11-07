package com.projecthero.mod.blackpanther;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projecthero.mod.blackpanther.entity.BlackPantherEntityTypes;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.blackpanther.network.BlackPantherActionPayload;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;

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
 * v0.17.3: one entry point for the whole Black Panther power, called once from {@code ProjectHeroMod.onInitialize}: the herb,
 * soup and thrown things, the damage rules, the payloads and the lifecycle hooks; plus
 * {@code /projecthero power tune black_panther kinetic <0-100>}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class BlackPantherMod {
	private BlackPantherMod() {
	}

	public static void initialize() {
		BlackPantherItems.initialize();
		BlackPantherEntityTypes.initialize();
		BlackPantherDamage.initialize();
		PayloadTypeRegistry.playC2S().register(BlackPantherActionPayload.TYPE, BlackPantherActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PantherSensesPayload.TYPE, PantherSensesPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlackPantherActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!BlackPanther.hasPower(p) || com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) { // Erased
				return;
			}
			if (payload.action() == BlackPantherActionPayload.Action.TOGGLE_SUIT) {
				BlackPanther.toggleSuit(p);
			}
		});
		ServerPlayerEvents.JOIN.register(BlackPanther::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> BlackPanther.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && BlackPanther.hasPower(p)) {
				BlackPanther.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> BlackPanther.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BlackPanther.clearTransient(handler.getPlayer()));
	}

	public static void clearSessionState() {
		BlackPanther.clearSessionState();
	}

	/** {@code /projecthero power tune black_panther kinetic <0-100>} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("black_panther")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("kinetic")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, BlackPantherConfig.KINETIC_MAX))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!BlackPanther.hasPower(player)) {
										c.getSource().sendFailure(Component.literal("You are not the Black Panther"));
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									BlackPanther.setKinetic(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Kinetic Energy set to " + Math.round(v)), false);
									return 1;
								})));
	}
}
