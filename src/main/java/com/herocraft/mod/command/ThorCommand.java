package com.herocraft.mod.command;

import com.herocraft.mod.worthiness.Worthiness;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Testing-only commands for the worthiness system: {@code /thor worthy} and {@code /thor unworthy}
 * let you flip a player's hidden score without having to build the full scoring table first.
 */
public final class ThorCommand {
	private ThorCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("thor")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("worthy")
						.executes(ThorCommand::makeWorthy)
						.then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
								.executes(context -> makeWorthy(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player")))))
				.then(Commands.literal("unworthy")
						.executes(ThorCommand::makeUnworthy)
						.then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
								.executes(context -> makeUnworthy(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player")))))
				.then(Commands.literal("status")
						.executes(ThorCommand::showStatus)
						.then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
								.executes(context -> showStatus(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"))))));
	}

	private static int makeWorthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return makeWorthy(context, context.getSource().getPlayerOrException());
	}

	private static int makeWorthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer target) {
		// Command grant = always replace: wipe every power of every tier before making the player worthy.
		com.herocraft.mod.hero.HeroTiers.wipeAll(target);
		Worthiness.setScore(target, Worthiness.TEST_WORTHY_SCORE);
		context.getSource().sendSuccess(() -> Component.translatable("commands.herocraft.thor.worthy", target.getName()), true);
		return 1;
	}

	private static int makeUnworthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return makeUnworthy(context, context.getSource().getPlayerOrException());
	}

	private static int makeUnworthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer target) {
		Worthiness.setScore(target, 0);
		context.getSource().sendSuccess(() -> Component.translatable("commands.herocraft.thor.unworthy", target.getName()), true);
		return 1;
	}

	private static int showStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return showStatus(context, context.getSource().getPlayerOrException());
	}

	private static int showStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer target) {
		int score = Worthiness.getScore(target);
		boolean worthy = Worthiness.isWorthy(target);
		context.getSource().sendSuccess(() -> Component.translatable("commands.herocraft.thor.status",
				target.getName(), score, worthy), false);
		return score;
	}
}
