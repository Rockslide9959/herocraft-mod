package com.projecthero.mod.command;

import com.projecthero.mod.worthiness.Worthiness;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Testing-only commands for the worthiness system: {@code /projecthero thor worthy} and
 * {@code /projecthero thor unworthy} let you flip a player's hidden score without having to build the
 * full scoring table first. Nested under {@code /projecthero} by {@link ProjectHeroCommand}.
 */
public final class ThorCommand {
	private ThorCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("thor")
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
								.executes(context -> showStatus(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player")))));
	}

	private static int makeWorthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return makeWorthy(context, context.getSource().getPlayerOrException());
	}

	private static int makeWorthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer target) {
		// Claim a Primary slot (replacing the oldest power if the player already holds two).
		com.projecthero.mod.hero.HeroTiers.claimPrimary(target, "thor");
		Worthiness.setScore(target, Worthiness.TEST_WORTHY_SCORE);
		context.getSource().sendSuccess(() -> Component.translatable("commands.projecthero.thor.worthy", target.getName()), true);
		return 1;
	}

	private static int makeUnworthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return makeUnworthy(context, context.getSource().getPlayerOrException());
	}

	private static int makeUnworthy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer target) {
		Worthiness.setScore(target, 0);
		context.getSource().sendSuccess(() -> Component.translatable("commands.projecthero.thor.unworthy", target.getName()), true);
		return 1;
	}

	private static int showStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return showStatus(context, context.getSource().getPlayerOrException());
	}

	private static int showStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer target) {
		int score = Worthiness.getScore(target);
		boolean worthy = Worthiness.isWorthy(target);
		context.getSource().sendSuccess(() -> Component.translatable("commands.projecthero.thor.status",
				target.getName(), score, worthy), false);
		return score;
	}
}
