package com.projecthero.mod.beastboy;

import java.util.Locale;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.beastboy.network.BeastBoyFxPayload;
import com.projecthero.mod.beastboy.network.BeastBoyPickPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

/**
 * v0.17.3 Beast Boy: one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the serum item,
 * getting it ({@link BeastBoyLearning}), the payloads, the damage rule (form health), riders, the world effects tick and the
 * lifecycle hooks; plus {@code /projecthero power tune beast_boy (stamina <0-100> | cooldowns | ocelot | form <name>)}. The
 * per-player tick runs from {@code AbilityRouter.serverTick}. Static state is dropped by {@code ServerStateReset} through
 * {@link #clearSessionState}.
 */
public final class BeastBoyMod {
	private BeastBoyMod() {
	}

	public static void initialize() {
		BeastBoyItems.initialize();
		BeastBoyLearning.initialize();
		PayloadTypeRegistry.playS2C().register(BeastBoyFxPayload.TYPE, BeastBoyFxPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BeastBoyPickPayload.TYPE, BeastBoyPickPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BeastBoyPickPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer p = context.player();
			if (p != null) {
				BeastBoyAbilityManager.pick(p, BeastForm.byId(payload.form()));
			}
		}));
		ServerTickEvents.END_SERVER_TICK.register(BeastBoyMoves::tickWorld);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p)
				|| BeastBoy.allowDamage(p, source, amount));
		ServerEntityEvents.ENTITY_LOAD.register(BeastBoyLearning::onLoad);
		// the fly and the mouse can't attack; no animal uses items or blocks
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				BeastBoyForms.harmless(BeastBoy.form(player)) ? InteractionResult.FAIL : InteractionResult.PASS);
		// he's vegetarian in every form (checked first so the animals get the message too)
		UseItemCallback.EVENT.register((player, level, hand) -> BeastBoyDiet.refuses(player, player.getItemInHand(hand)) || BeastBoy.morphed(player)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> BeastBoy.morphed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			InteractionResult ride = BeastBoyRiding.interact(player, entity);
			if (ride != InteractionResult.PASS) {
				return ride;
			}
			return BeastBoy.morphed(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		ServerPlayerEvents.JOIN.register(BeastBoy::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> BeastBoy.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && BeastBoy.hasPower(p)) {
				BeastBoy.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> BeastBoy.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (p != null) {
				BeastBoy.clearTransient(p);
				BeastBoy.forget(p.getUUID());
			}
		});
	}

	public static void clearSessionState() {
		BeastBoy.clearSessionState();
	}

	/** {@code /projecthero power tune beast_boy ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("beast_boy")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("stamina")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, BeastBoyConfig.STAMINA_MAX))
								.executes(c -> {
									ServerPlayer player = beastBoy(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									BeastBoy.setStamina(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Morph Stamina set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = beastBoy(c.getSource());
					if (player == null) {
						return 0;
					}
					BeastBoy.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Beast Boy cooldowns cleared"), false);
					return 1;
				}))
				.then(Commands.literal("ocelot").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					boolean ok = BeastBoyLearning.spawnGreen(player) != null;
					c.getSource().sendSuccess(() -> Component.literal(ok ? "A green (Sakutia) ocelot appeared" : "No ocelot"), false);
					return ok ? 1 : 0;
				}))
				.then(Commands.literal("form")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests((c, b) -> {
									for (BeastForm f : BeastForm.values()) {
										b.suggest(f.name().toLowerCase(Locale.ROOT));
									}
									return b.buildFuture();
								})
								.executes(c -> {
									ServerPlayer player = beastBoy(c.getSource());
									if (player == null) {
										return 0;
									}
									String name = StringArgumentType.getString(c, "name").toUpperCase(Locale.ROOT);
									BeastForm f;
									try {
										f = BeastForm.valueOf(name);
									} catch (IllegalArgumentException e) {
										c.getSource().sendFailure(Component.literal("No such form: " + name));
										return 0;
									}
									BeastBoy.setForm(player, f);
									c.getSource().sendSuccess(() -> Component.literal("Now a " + f.name().toLowerCase(Locale.ROOT)), false);
									return 1;
								})));
	}

	private static ServerPlayer beastBoy(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!BeastBoy.hasPower(player)) {
			source.sendFailure(Component.literal("You aren't Beast Boy"));
			return null;
		}
		return player;
	}
}
