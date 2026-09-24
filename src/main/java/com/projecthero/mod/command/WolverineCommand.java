package com.projecthero.mod.command;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.data.WolverineState;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin / testing commands for Wolverine: {@code /wolverine power grant|revoke [player] | claws deploy|retract
 * | status}. Op 2. Survival route: own Super Regeneration, craft an Adamantium Serum, use it.
 */
public final class WolverineCommand {
	private WolverineCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("wolverine")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("power")
						.then(Commands.literal("grant")
								.executes(c -> grant(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> grant(c, EntityArgument.getPlayer(c, "player")))))
						.then(Commands.literal("revoke")
								.executes(c -> revoke(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> revoke(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("claws")
						.then(Commands.literal("deploy").executes(c -> {
							Wolverine.setClaws(self(c), true);
							return 1;
						}))
						.then(Commands.literal("retract").executes(c -> {
							Wolverine.setClaws(self(c), false);
							return 1;
						})))
				.then(Commands.literal("status").executes(c -> status(c, self(c))));
	}

	/** Grant = give Super Regeneration if missing (the prerequisite), then ascend it -- same as {@code /hero grant}. */
	public static int grant(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String name = target.getGameProfile().getName();
		if (Wolverine.hasPower(target)) {
			c.getSource().sendFailure(Component.literal(name + " is already Wolverine"));
			return 0;
		}
		if (!Wolverine.hasSuperRegeneration(target)) {
			Power sr = Powers.byKey(Wolverine.SUPER_REGENERATION_KEY);
			if (sr == null || !ExperimentalPowers.grant(target, sr)) {
				c.getSource().sendFailure(Component.literal(
						"Could not give " + name + " the Super Regeneration prerequisite (mutation slots full?)"));
				return 0;
			}
		}
		if (!Wolverine.ascendFromSuperRegeneration(target)) {
			c.getSource().sendFailure(Component.literal("Could not ascend " + name + " into Wolverine"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Ascended " + name + " into Wolverine"), true);
		return 1;
	}

	private static int revoke(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		Wolverine.revoke(target);
		c.getSource().sendSuccess(() -> Component.literal("Revoked Wolverine"), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		WolverineState s = Wolverine.state(p);
		long now = p.level().getGameTime();
		c.getSource().sendSuccess(() -> Component.literal("Wolverine=" + s.hasPower + " claws=" + s.clawsOut
				+ " rage=" + Math.max(0, (s.rageUntil - now) / 20) + "s emergencyReadyIn="
				+ Math.max(0, (s.emergencyReadyAt - now) / 20) + "s"), false);
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}
}
