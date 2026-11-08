package com.projecthero.mod.andy;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyEntityTypes;
import com.projecthero.mod.andy.item.AndyItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: one entry point for the whole Andy power, called once from {@code ProjectHeroMod.onInitialize}: the card, the
 * flying pieces and clones, the damage rules, the missing-hand rules and the lifecycle hooks; plus
 * {@code /projecthero power tune andy (break <part|all> | restore | regen <slow|normal|burst> | style <streaming|build|bone>)}. The per-player tick runs
 * from {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through
 * {@link #clearSessionState}.
 */
public final class AndyMod {
	private static final String[] PART_NAMES = { "head", "body", "right_arm", "left_arm", "right_leg", "left_leg" };

	private AndyMod() {
	}

	public static void initialize() {
		AndySoundEvents.initialize(); // v0.17.3 Andy sounds: projecthero:andy.*
		AndyItems.initialize();
		AndyEntityTypes.initialize();
		AndyDamage.initialize();
		// a missing hand can't hit or use anything (main arm), or hold anything (off arm)
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> handGone(player, hand)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		ServerPlayerEvents.JOIN.register(Andy::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Andy.clearTransient(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Andy.hasPower(p)) {
				AndyAbilities.clear(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> AndyAbilities.clear(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AndyAbilities.clear(handler.getPlayer());
			Andy.forget(handler.getPlayer().getUUID());
		});
	}

	/** The hand used is on an arm Andy doesn't have right now. */
	public static boolean handGone(Player player, InteractionHand hand) {
		if (!Andy.hasPower(player) || player.isSpectator()) {
			return false;
		}
		return hand == InteractionHand.MAIN_HAND ? Andy.mainArmMissing(player) : Andy.offArmMissing(player);
	}

	public static void clearSessionState() {
		Andy.clearSessionState();
	}

	/** {@code /projecthero power tune andy ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("andy")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("break")
						.then(Commands.argument("part", StringArgumentType.word())
								.suggests((c, b) -> {
									for (String n : PART_NAMES) {
										b.suggest(n);
									}
									b.suggest("all");
									return b.buildFuture();
								})
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!Andy.hasPower(player)) {
										c.getSource().sendFailure(Component.literal("You are not Andy"));
										return 0;
									}
									String name = StringArgumentType.getString(c, "part");
									if ("all".equals(name)) {
										Andy.shatter(player);
										return 1;
									}
									int part = java.util.Arrays.asList(PART_NAMES).indexOf(name);
									if (part < 0) {
										c.getSource().sendFailure(Component.literal("Unknown part: " + name));
										return 0;
									}
									Andy.breakPart(player, part, AndyState.KIND_BURST);
									return 1;
								})))
				.then(Commands.literal("restore").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					Andy.restore(player);
					c.getSource().sendSuccess(() -> Component.literal("Every part regrown"), false);
					return 1;
				}))
				.then(Commands.literal("regen")
						.then(Commands.literal("slow").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_SLOW)))
						.then(Commands.literal("normal").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_NORMAL)))
						.then(Commands.literal("burst").executes(c -> setSpeed(c.getSource(), AndyConfig.SPEED_BURST))))
				.then(Commands.literal("style")
						.then(Commands.literal("streaming").executes(c -> setStyle(c.getSource(), AndyState.STYLE_STREAMING)))
						.then(Commands.literal("build").executes(c -> setStyle(c.getSource(), AndyState.STYLE_BUILD)))
						.then(Commands.literal("bone").executes(c -> setStyle(c.getSource(), AndyState.STYLE_BONE))));
	}

	private static int setStyle(CommandSourceStack source, int style) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Andy.setStyle(source.getPlayerOrException(), style);
		return 1;
	}

	private static int setSpeed(CommandSourceStack source, int speed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Andy.setSpeed(source.getPlayerOrException(), speed);
		return 1;
	}
}
