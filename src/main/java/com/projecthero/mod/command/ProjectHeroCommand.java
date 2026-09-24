package com.projecthero.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * v0.10.1: {@code /projecthero <sub> ...} is now the <b>only</b> command root the mod registers.
 * Every per-hero and per-event admin command that used to have its own top-level literal
 * ({@code /thor}, {@code /ironman}, {@code /symbiote}, {@code /heropower}, {@code /superhero},
 * {@code /heroraid}, ...) is gone; each of those classes now exposes a {@code build()} that returns
 * its subtree, and this class hangs them all under {@code /projecthero}.
 *
 * <table>
 *   <tr><td>{@code /projecthero thor}</td><td>Mjolnir worthiness testing</td></tr>
 *   <tr><td>{@code /projecthero ironman}</td><td>Tony Stark power / tech / suits</td></tr>
 *   <tr><td>{@code /projecthero spiderman}</td><td>Spider-Man power / web / symbiote spawn</td></tr>
 *   <tr><td>{@code /projecthero symbiote}</td><td>standalone Symbiote bond / activate</td></tr>
 *   <tr><td>{@code /projecthero maxsteel}</td><td>Max Steel power / energy / transform</td></tr>
 *   <tr><td>{@code /projecthero punisher}</td><td>Punisher power / training / arsenal</td></tr>
 *   <tr><td>{@code /projecthero titan}</td><td>spawn the Titan boss</td></tr>
 *   <tr><td>{@code /projecthero power}</td><td>experimental + Hero-Tier power admin (was {@code /heropower})</td></tr>
 *   <tr><td>{@code /projecthero hero}</td><td>survival self-service, any player (was {@code /superhero})</td></tr>
 *   <tr><td>{@code /projecthero raid}</td><td>start/stop any world event (was {@code /heroraid})</td></tr>
 *   <tr><td>{@code /projecthero supervillainraid}</td><td>Supervillain Raid dev commands</td></tr>
 *   <tr><td>{@code /projecthero zombieraid}</td><td>Zombie/Gravebound Raid dev commands (was {@code /heropack zombieraid})</td></tr>
 * </table>
 *
 * <p>Each subtree keeps its own {@code requires(...)} permission gate, so {@code /projecthero hero} is
 * still usable by any player while the rest stay op-only.
 *
 * <p>v0.10.10 adds one deliberate exception to the single-root rule: {@link SquadCommand} is also
 * registered as a bare {@code /squad}. Everything under {@code /projecthero} is admin tooling, but a
 * squad is something every player manages themselves and often mid-fight, so it earns the short name.
 * It is mirrored at {@code /projecthero squad} too, so it is still discoverable from the mod's root.
 */
public final class ProjectHeroCommand {
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
							.append(Component.literal("  /projecthero <thor|ironman|spiderman|symbiote|maxsteel"
									+ "|punisher|greenlantern|wolverine|titan|power|hero|raid|supervillainraid|zombieraid>")
									.withStyle(ChatFormatting.GRAY)), false);
					return 1;
				});

		root.then(ThorCommand.build());
		root.then(IronManCommand.build());
		root.then(SpiderManCommand.build());
		root.then(SymbioteCommand.build());
		root.then(MaxSteelCommand.build());
		root.then(PunisherCommand.build());
		root.then(GreenLanternCommand.build());
		root.then(WolverineCommand.build());
		root.then(TitanCommand.build());
		root.then(HeroCommand.build());
		root.then(SuperheroCommand.build());
		root.then(HeroRaidCommand.build());
		root.then(SupervillainRaidCommand.build());
		root.then(ZombieRaidCommand.build());
		root.then(SquadCommand.build());

		dispatcher.register(root);
		SquadCommand.initialize(dispatcher);
	}
}
