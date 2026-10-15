package com.projecthero.mod.cyborg;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.5 admin / test command (op only): {@code /projecthero cyborg core <0-100>} sets your Power Core. The power itself
 * is granted like every other hero's: {@code /projecthero power grant <player> cyborg}.
 */
public final class CyborgCommand {
	private CyborgCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("cyborg")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("core")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, CyborgConfig.CORE_MAX))
								.executes(CyborgCommand::setCore)));
	}

	private static int setCore(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!Cyborg.hasPower(player)) {
			c.getSource().sendFailure(Component.literal("You are not Cyborg"));
			return 0;
		}
		float v = FloatArgumentType.getFloat(c, "amount");
		Cyborg.setCore(player, v);
		c.getSource().sendSuccess(() -> Component.literal("Power Core set to " + Math.round(v)), false);
		return 1;
	}
}
