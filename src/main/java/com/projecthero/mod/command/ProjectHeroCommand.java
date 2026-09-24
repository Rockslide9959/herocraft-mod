package com.projecthero.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * v0.12.16: {@code /projecthero} now has exactly three subcommands (all op-only):
 *
 * <table>
 *   <tr><td>{@code /projecthero power grant|remove|stack <power> [player]}</td><td>every power in the mod</td></tr>
 *   <tr><td>{@code /projecthero locate <structure>}</td><td>every structure the mod adds</td></tr>
 *   <tr><td>{@code /projecthero raid start|end|removetimer|advancetimer <raid>}</td><td>the mod's raids</td></tr>
 * </table>
 *
 * Every per-hero, event and survival command that used to hang off this root (thor, ironman, spiderman,
 * symbiote, maxsteel, punisher, greenlantern, wolverine, titan, hero, supervillainraid, zombieraid) is no
 * longer registered -- their classes remain, unreachable. The one deliberate exception is the bare
 * player-facing {@code /squad}, which is gameplay, not admin tooling.
 */
public final class ProjectHeroCommand {
	private ProjectHeroCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("projecthero")
				.requires(source -> source.hasPermission(2))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal("Project Hero")
							.withStyle(ChatFormatting.LIGHT_PURPLE)
							.append(Component.literal("  /projecthero <power|locate|raid>")
									.withStyle(ChatFormatting.GRAY)), false);
					return 1;
				});

		root.then(HeroCommand.buildAdmin());
		root.then(LocateCommand.build());
		root.then(RaidAdminCommand.build());

		dispatcher.register(root);
		SquadCommand.initialize(dispatcher);
	}
}
