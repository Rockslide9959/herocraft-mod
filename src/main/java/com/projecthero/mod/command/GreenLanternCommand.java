package com.projecthero.mod.command;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin/testing commands for Green Lantern: {@code /projecthero greenlantern power grant|revoke |
 * energy <0-10000> | dismiss | status}. Op 2, same shape as {@link MaxSteelCommand}.
 */
public final class GreenLanternCommand {
	private GreenLanternCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("greenlantern")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("power")
						.then(Commands.literal("grant")
								.executes(c -> power(c, self(c), true))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> power(c, EntityArgument.getPlayer(c, "player"), true))))
						.then(Commands.literal("revoke")
								.executes(c -> power(c, self(c), false))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> power(c, EntityArgument.getPlayer(c, "player"), false)))))
				.then(Commands.literal("energy")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, GreenLanternConfig.MAX_RING_CHARGE * 2f))
								.executes(c -> energy(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> energy(c, EntityArgument.getPlayer(c, "player"))))))
				// v0.17.1: Lantern Ranks for testing
				.then(Commands.literal("rank")
						.then(Commands.argument("rank", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
								.executes(c -> rank(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> rank(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("dismiss").executes(c -> dismiss(c, self(c))))
				.then(Commands.literal("status").executes(c -> status(c, self(c))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static int power(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean grant) {
		if (grant) {
			if (!GreenLantern.bond(target)) {
				c.getSource().sendFailure(Component.literal(target.getGameProfile().getName() + " already has Green Lantern"));
				return 0;
			}
		} else {
			GreenLantern.revoke(target);
		}
		c.getSource().sendSuccess(() -> Component.literal((grant ? "Granted Green Lantern to " : "Revoked Green Lantern from ")
				+ target.getGameProfile().getName()), true);
		return 1;
	}

	private static int energy(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		float amount = FloatArgumentType.getFloat(c, "amount");
		GreenLanternState s = GreenLantern.state(target).copy();
		s.ringCharge = Math.max(0f, Math.min(com.projecthero.mod.greenlantern.GreenLanternRanks.maxCharge(target), amount));
		GreenLantern.save(target, s);
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Ring Charge set to %.0f", GreenLantern.state(target).ringCharge)), true);
		return 1;
	}

	private static int rank(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		int r = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "rank");
		com.projecthero.mod.greenlantern.GreenLanternRanks.setRank(target, r);
		c.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " is now Lantern Rank " + r), true);
		return 1;
	}

	private static int dismiss(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		GreenLanternConstructs.dismissAll(target.getUUID());
		c.getSource().sendSuccess(() -> Component.literal("Dismissed all constructs for "
				+ target.getGameProfile().getName()), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		GreenLanternState s = GreenLantern.state(target);
		// v0.11.7: no more generic construct-slot cap to report against -- just the raw active weight.
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Green Lantern: %s | charge %.0f/%.0f | suited %s | rank %d (%d XP) | constructs %d",
				s.hasPower, s.ringCharge, com.projecthero.mod.greenlantern.GreenLanternRanks.maxCharge(target), s.suited,
				com.projecthero.mod.greenlantern.GreenLanternRanks.rankFor(s.willpowerXp), s.willpowerXp,
				GreenLanternConstructs.activeWeight(target.getUUID()))), false);
		return 1;
	}
}
