package com.projecthero.mod.sentry;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projecthero.mod.sentry.block.SentryBlocks;
import com.projecthero.mod.sentry.data.SentryAttachments;
import com.projecthero.mod.sentry.item.SentryItems;
import com.projecthero.mod.sentry.network.SentryFxPayload;
import com.projecthero.mod.sentry.network.ShameRoomPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Sentry &amp; the Void: one entry point, called once from {@code ProjectHeroMod.onInitialize}: the attachment, blocks, items,
 * damage rules, the {@code projectheroVoidGriefing} gamerule, the payloads, the world ticks (the eaten world coming back, the Void's
 * zones), the click rules (a Sentry in the takeover or in Calm can't attack; others reach the Void with an empty hand) and the lifecycle
 * hooks; plus {@code /projecthero power tune sentry (sun|void <0-100> | cooldowns | takeover | release)}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}. Static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class SentryMod {
	private SentryMod() {
	}

	public static void initialize() {
		SentryAttachments.initialize();
		SentryBlocks.initialize();
		SentryItems.initialize();
		SentryDamage.initialize();
		VoidMatter.initialize();
		PayloadTypeRegistry.playS2C().register(SentryFxPayload.TYPE, SentryFxPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ShameRoomPayload.TYPE, ShameRoomPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShameRoomPayload.TYPE, (payload, context) -> ShameRooms.report(context.player(), payload));
		// v0.17.5 rework: double-tap Space flight and the Let It Out trial's result
		PayloadTypeRegistry.playC2S().register(com.projecthero.mod.sentry.network.SentryActionPayload.TYPE,
				com.projecthero.mod.sentry.network.SentryActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(com.projecthero.mod.sentry.network.SentryActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (payload.action() == com.projecthero.mod.sentry.network.SentryActionPayload.TOGGLE_FLIGHT) {
				SentryFlight.toggle(p);
			} else if (payload.action() == com.projecthero.mod.sentry.network.SentryActionPayload.TRIAL_RESULT) {
				LetItOut.report(p, payload.won());
			}
		});
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			VoidMatter.tickLevel(level);
			VoidMoves.tickLevel(level);
		});
		ServerPlayerEvents.JOIN.register(Sentry::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Sentry.onPlayerRespawn(newPlayer));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (Sentry.inTakeover(player)) {
				VoidTakeover.end(player, VoidTakeover.End.LOGGED_OUT); // a portal / teleport mid-takeover lets go (both worlds heal)
			}
			Sentry.clearTransient(player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (Sentry.inTakeover(p)) {
				VoidTakeover.end(p, VoidTakeover.End.LOGGED_OUT);
			}
			Sentry.clearTransient(p);
			Sentry.forget(p.getUUID());
		});
		// the body is the Void's: his own clicks do nothing; in Calm he can't attack
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				!level.isClientSide() && (Sentry.inTakeover(player) || Sentry.calming(player)) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) ->
				!level.isClientSide() && Sentry.inTakeover(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				!level.isClientSide() && Sentry.inTakeover(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				!level.isClientSide() && Sentry.inTakeover(player) ? InteractionResultHolder.fail(player.getItemInHand(hand))
						: InteractionResultHolder.pass(player.getItemInHand(hand)));
		// outside help: hold right click on the Void's body with an empty hand
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (Sentry.inTakeover(player)) {
				return level.isClientSide() ? InteractionResult.PASS : InteractionResult.FAIL;
			}
			if (entity instanceof Player body && Sentry.inTakeover(body) && player.getMainHandItem().isEmpty()) {
				if (!level.isClientSide() && player instanceof ServerPlayer sp && body instanceof ServerPlayer sb) {
					VoidTakeover.help(sp, sb);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	public static void clearSessionState() {
		Sentry.clearSessionState();
	}

	/** {@code /projecthero power tune sentry ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("sentry")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("sun")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, SentryConfig.SUN_MAX))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									float v = FloatArgumentType.getFloat(c, "amount");
									Sentry.setSun(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Sun set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("void")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, SentryConfig.VOID_MAX))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									float v = FloatArgumentType.getFloat(c, "amount");
									Sentry.setVoid(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Void set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					Sentry.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Sentry cooldowns cleared"), false);
					return 1;
				}))
				.then(Commands.literal("takeover").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					if (!Sentry.hasPower(player) || Sentry.inTakeover(player)) {
						c.getSource().sendFailure(Component.literal("Not the Sentry, or the Void already has you"));
						return 0;
					}
					VoidTakeover.begin(player);
					c.getSource().sendSuccess(() -> Component.literal("The Void takes over"), false);
					return 1;
				}))
				.then(Commands.literal("release").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					if (!Sentry.inTakeover(player)) {
						c.getSource().sendFailure(Component.literal("No takeover running"));
						return 0;
					}
					VoidTakeover.end(player, VoidTakeover.End.COMMAND);
					c.getSource().sendSuccess(() -> Component.literal("The Void is thrown out"), false);
					return 1;
				}));
	}
}
