package com.projecthero.mod.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.13.11 (Hulk Phase 1): TEMPORARY op-only test commands --
 * {@code /hulk grant [players]}, {@code /hulk revoke [players]}, {@code /hulk setrage <0-100> [players]}.
 * Phase 4 (the Gamma Serum) removes or locks these down. The permanent admin path is
 * {@code /projecthero power grant hulk}, which goes through {@link #grant}.
 */
public final class HulkCommand {
	private HulkCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("hulk")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("grant")
						.executes(c -> grantAll(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> grantAll(c, EntityArgument.getPlayers(c, "targets")))))
				.then(Commands.literal("revoke")
						.executes(c -> revokeAll(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> revokeAll(c, EntityArgument.getPlayers(c, "targets")))))
				.then(Commands.literal("setrage")
						.then(Commands.argument("rage", FloatArgumentType.floatArg(0.0f, HulkConfig.RAGE_MAX))
								.executes(c -> setRage(c, List.of(c.getSource().getPlayerOrException())))
								.then(Commands.argument("targets", EntityArgument.players())
										.executes(c -> setRage(c, EntityArgument.getPlayers(c, "targets"))))));
	}

	/** The single-player grant used by both {@code /hulk grant} and {@code /projecthero power grant hulk}. */
	public static int grant(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String name = target.getGameProfile().getName();
		if (!Hulk.grant(target)) {
			c.getSource().sendFailure(Component.literal(name + " already has the Gamma power"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Gave " + name + " the Gamma power (Hulk)"), true);
		return 1;
	}

	private static int grantAll(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
		int n = 0;
		for (ServerPlayer p : targets) {
			n += grant(c, p);
		}
		return n;
	}

	private static int revokeAll(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) throws CommandSyntaxException {
		int n = 0;
		for (ServerPlayer p : targets) {
			if (!Hulk.hasPower(p)) {
				c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " does not have the Gamma power"));
				continue;
			}
			Hulk.revoke(p);
			c.getSource().sendSuccess(() -> Component.literal("Took the Gamma power from " + p.getGameProfile().getName()), true);
			n++;
		}
		return n;
	}

	private static int setRage(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
		float rage = FloatArgumentType.getFloat(c, "rage");
		int n = 0;
		for (ServerPlayer p : targets) {
			if (!Hulk.hasPower(p)) {
				c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " does not have the Gamma power"));
				continue;
			}
			Hulk.setRage(p, rage);
			c.getSource().sendSuccess(() -> Component.literal("Set " + p.getGameProfile().getName() + "'s rage to "
					+ Math.round(rage)), true);
			n++;
		}
		return n;
	}
}
