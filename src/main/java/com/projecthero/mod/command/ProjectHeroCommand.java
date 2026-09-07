package com.projecthero.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * v0.9.24: a single command tree, {@code /projecthero <sub> ...}, that nests every per-hero and
 * per-event admin command the mod registers. The original roots ({@code /thor}, {@code /symbiote},
 * {@code /heropower}, ...) are kept as aliases -- each subcommand here simply {@code redirect}s onto
 * the already-registered root node, so there is no duplicated argument wiring and the two forms can
 * never drift apart.
 *
 * <p>{@link #initialize()} is called last in {@code ProjectHeroMod} so every root literal exists on
 * the dispatcher by the time {@link #register} looks it up.
 */
public final class ProjectHeroCommand {
	/** {registered root literal, name it is nested under in /projecthero}. */
	private static final String[][] SUBS = {
			{"thor", "thor"},
			{"ironman", "ironman"},
			{"spiderman", "spiderman"},
			{"symbiote", "symbiote"},
			{"maxsteel", "maxsteel"},
			{"punisher", "punisher"},
			{"titan", "titan"},
			{"heropower", "power"},
			{"superhero", "hero"},
			{"heroraid", "raid"},
			{"supervillainraid", "supervillainraid"},
			{"heropack", "zombieraid"},
	};

	private ProjectHeroCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("projecthero")
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal("Project Hero")
							.withStyle(ChatFormatting.LIGHT_PURPLE)
							.append(Component.literal("  /projecthero <" + names() + ">")
									.withStyle(ChatFormatting.GRAY)), false);
					return 1;
				});

		for (String[] pair : SUBS) {
			CommandNode<CommandSourceStack> target = dispatcher.getRoot().getChild(pair[0]);
			if (target instanceof LiteralCommandNode<CommandSourceStack> literal) {
				root.then(Commands.literal(pair[1])
						.requires(literal.getRequirement())
						.redirect(literal));
			}
		}

		dispatcher.register(root);
	}

	private static String names() {
		StringBuilder sb = new StringBuilder();
		for (String[] pair : SUBS) {
			if (sb.length() > 0) {
				sb.append('|');
			}
			sb.append(pair[1]);
		}
		return sb.toString();
	}
}
