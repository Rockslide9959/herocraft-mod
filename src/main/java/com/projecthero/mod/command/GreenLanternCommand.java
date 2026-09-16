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
 * energy <0-10000> | mastery <0-4> | dismiss | status}. Op 2, same shape as {@link MaxSteelCommand}.
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
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, GreenLanternConfig.MAX_RING_CHARGE))
								.executes(c -> energy(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> energy(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("mastery")
						.then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4))
								.executes(c -> mastery(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> mastery(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("dismiss").executes(c -> dismiss(c, self(c))))
				.then(Commands.literal("status").executes(c -> status(c, self(c))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static int power(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean grant) {
		if (grant) {
			com.projecthero.mod.hero.HeroTiers.wipeAll(target);
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
		s.ringCharge = Math.max(0f, Math.min(GreenLanternConfig.MAX_RING_CHARGE, amount));
		GreenLantern.save(target, s);
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Ring Charge set to %.0f", GreenLantern.state(target).ringCharge)), true);
		return 1;
	}

	private static int mastery(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level");
		GreenLanternState s = GreenLantern.state(target).copy();
		s.masteryLevel = level;
		GreenLantern.save(target, s);
		c.getSource().sendSuccess(() -> Component.literal("Mastery set to " + level), true);
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
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Green Lantern: %s | charge %.0f/%.0f | suited %s | mastery %d | constructs %d/%d | spent %d",
				s.hasPower, s.ringCharge, GreenLanternConfig.MAX_RING_CHARGE, s.suited, s.masteryLevel,
				GreenLanternConstructs.activeWeight(target.getUUID()), GreenLanternConfig.CONSTRUCT_MAX_SLOTS,
				s.totalEnergySpent)), false);
		return 1;
	}
}
