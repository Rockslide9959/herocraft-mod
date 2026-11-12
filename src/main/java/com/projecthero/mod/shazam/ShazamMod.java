package com.projecthero.mod.shazam;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.shazam.entity.ShazamEntities;
import com.projecthero.mod.shazam.network.ShazamFlightPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;

/**
 * v0.17.3 Shazam: one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the sounds, the Wizard,
 * the damage rules, the flight payload, the <b>chat hook</b> (the word), the lightning watcher and the lifecycle hooks; plus
 * {@code /projecthero power tune shazam (lightning <0-100> | cooldowns | word | wizard | pure)}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}. Static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class ShazamMod {
	private ShazamMod() {
	}

	public static void initialize() {
		ShazamSounds.initialize();
		ShazamEntities.initialize();
		ShazamDamage.initialize();
		PayloadTypeRegistry.playC2S().register(ShazamFlightPayload.TYPE, ShazamFlightPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShazamFlightPayload.TYPE, (payload, context) -> ShazamFlight.toggle(context.player()));
		// the word: a chat message that is just "Shazam" is never posted -- the lightning answers instead
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.signedContent();
			if (!wouldSwallow(sender, text)) {
				return true;
			}
			sender.server.execute(() -> Shazam.speak(sender));
			return false;
		});
		ServerTickEvents.END_WORLD_TICK.register(ShazamWizards::tickLevel);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LightningBolt bolt && level instanceof ServerLevel server) {
				Shazam.onLightning(server, bolt);
			}
		});
		ServerPlayerEvents.JOIN.register(Shazam::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Shazam.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Shazam.peek(p) != null) {
				Shazam.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Shazam.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Shazam.clearTransient(handler.getPlayer());
			Shazam.forget(handler.getPlayer().getUUID());
		});
	}

	/** Whether {@code text} from {@code sender} is the word and he may say it (so it is kept out of chat). */
	public static boolean wouldSwallow(ServerPlayer sender, String text) {
		return ShazamWord.isWord(text) && Shazam.canSpeak(sender);
	}

	/**
	 * The chat path without the chat (for the tests): what the hook does with {@code text}. Returns null when the message would be
	 * posted normally, else what saying the word did.
	 */
	public static Shazam.Spoken onChat(ServerPlayer sender, String text) {
		if (!wouldSwallow(sender, text)) {
			return null;
		}
		return Shazam.speak(sender);
	}

	public static void clearSessionState() {
		Shazam.clearSessionState();
	}

	/** {@code /projecthero power tune shazam ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("shazam")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("lightning")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, ShazamConfig.LIGHTNING_MAX))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									float v = FloatArgumentType.getFloat(c, "amount");
									Shazam.setLightning(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Lightning set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					Shazam.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Shazam cooldowns cleared"), false);
					return 1;
				}))
				.then(Commands.literal("word").executes(c -> {
					ServerPlayer player = shazam(c.getSource());
					if (player == null) {
						return 0;
					}
					Shazam.Spoken r = Shazam.speak(player);
					c.getSource().sendSuccess(() -> Component.literal("Said the word: " + r), false);
					return 1;
				}))
				.then(Commands.literal("wizard").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					var w = ShazamWizards.spawnNear(player, 3, 5, true);
					c.getSource().sendSuccess(() -> Component.literal(w == null ? "No room for the Wizard here" : "Wizard Shazam appears"), false);
					return w == null ? 0 : 1;
				}))
				.then(Commands.literal("pure").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					boolean pure = ShazamWizards.pure(player);
					c.getSource().sendSuccess(() -> Component.literal(pure ? "Pure of heart" : "Not pure of heart"), false);
					return 1;
				}));
	}

	private static ServerPlayer shazam(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!Shazam.canSpeak(player)) {
			source.sendFailure(Component.literal("You aren't Shazam or of the Shazam Family"));
			return null;
		}
		return player;
	}
}
