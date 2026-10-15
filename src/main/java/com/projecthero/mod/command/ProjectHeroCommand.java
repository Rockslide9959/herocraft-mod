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
							.append(Component.literal("  /projecthero <power|locate|raid|meteor|uniquepowers|crime|city>")
									.withStyle(ChatFormatting.GRAY)), false);
					return 1;
				});

		// v0.16.5 (user: the powers should not be listed under /projecthero -- power grant gives them): each hero's own
		// test tools (set Solar Energy, Nova Force, Hellfire, Power Core, Scarab Energy...) live under ONE entry,
		// /projecthero power tune <hero> ...
		root.then(HeroCommand.buildAdmin().then(Commands.literal("tune")
				.then(com.projecthero.mod.kryptonian.KryptonianCommand.buildKryptonian())
				.then(com.projecthero.mod.nova.NovaCommand.build())
				.then(com.projecthero.mod.ghostrider.GhostRiderCommand.build())
				.then(com.projecthero.mod.cyborg.CyborgCommand.build())
				.then(com.projecthero.mod.bluebeetle.BlueBeetleMod.command())));
		root.then(LocateCommand.build());
		root.then(RaidAdminCommand.build());
		// v0.14.8: /projecthero meteor [here] -- the Kryptonite Meteor world event (op only)
		root.then(com.projecthero.mod.kryptonian.KryptonianCommand.buildMeteor());
		// v0.16.1: /projecthero uniquepowers list | release <power> (the projectheroUniquePowers gamerule)
		root.then(com.projecthero.mod.hero.UniquePowers.buildCommand());
		// v0.16.2: /projecthero crime start <type> | end | trigger | status (street crime)
		root.then(com.projecthero.mod.crime.Crimes.buildCommand());
		// v0.16.2 City part B: /projecthero city status | repair | sweep | optout <r> | reset <r>
		root.then(com.projecthero.mod.city.CityCommands.build());

		dispatcher.register(root);
		// v0.13.11: Hulk Phase 1 test commands (op-only; removed / locked down in Phase 4)
		dispatcher.register(HulkCommand.build());
		// v0.13.19: Moon Knight debug / test commands (op only), as the user asked: /moonknight ...
		dispatcher.register(MoonKnightCommand.build());
		SquadCommand.initialize(dispatcher);
	}
}
