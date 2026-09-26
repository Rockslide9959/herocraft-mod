package com.projecthero.mod.command;

import com.projecthero.mod.titanshifter.TitanShifter;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Grant helper for the Titan Shifter, reached only through {@code /projecthero power grant titan_shifter} (no sub-command of its own). */
public final class TitanShifterCommand {
	private TitanShifterCommand() {
	}

	public static int grant(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String name = target.getGameProfile().getName();
		if (!TitanShifter.grant(target)) {
			c.getSource().sendFailure(Component.literal(name + " already has Titan Shifting"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Granted Titan Shifting to " + name), true);
		return 1;
	}
}
