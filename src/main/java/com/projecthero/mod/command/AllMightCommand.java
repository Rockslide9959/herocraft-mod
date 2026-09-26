package com.projecthero.mod.command;

import com.projecthero.mod.allmight.AllMight;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Grant helper for All Might, reached only through {@code /projecthero power grant all_might} (no sub-command of its own). */
public final class AllMightCommand {
	private AllMightCommand() {
	}

	public static int grant(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String name = target.getGameProfile().getName();
		if (!AllMight.grant(target)) {
			c.getSource().sendFailure(Component.literal(name + " already has One For All"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Granted All Might to " + name), true);
		return 1;
	}
}
