package com.projecthero.mod.command;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.data.AllMightState;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin / debug commands for All Might: {@code /projecthero allmight grant|remove [player]}, {@code form [full|base]},
 * {@code ofa <0-100>} and {@code status}. Op 2. Survival route: craft and use a Vestige of One For All.
 */
public final class AllMightCommand {
	private AllMightCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("allmight")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("grant")
						.executes(c -> grant(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> grant(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("remove")
						.executes(c -> remove(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> remove(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("form").executes(c -> form(c, self(c))))
				.then(Commands.literal("ofa")
						.then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000)).executes(c -> ofa(c, self(c)))))
				.then(Commands.literal("status").executes(c -> status(c, self(c))));
	}

	/** Also used by {@code /projecthero power grant all_might}. */
	public static int grant(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String name = target.getGameProfile().getName();
		if (!AllMight.grant(target)) {
			c.getSource().sendFailure(Component.literal(name + " already has One For All"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Granted All Might to " + name), true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		AllMight.revoke(target);
		c.getSource().sendSuccess(() -> Component.literal("Removed All Might from " + target.getGameProfile().getName()), true);
		return 1;
	}

	private static int form(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		AllMight.toggleForm(target);
		return 1;
	}

	private static int ofa(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		int amount = IntegerArgumentType.getInteger(c, "amount");
		AllMightState s = AllMight.state(target).copy();
		s.ofa = Math.min(AllMightConfig.OFA_MAX, amount);
		target.setAttached(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, s);
		c.getSource().sendSuccess(() -> Component.literal("OFA set to " + (int) s.ofa), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		AllMightState s = AllMight.state(p);
		long now = p.level().getGameTime();
		c.getSource().sendSuccess(() -> Component.literal("AllMight=" + s.hasPower + " full=" + s.fullPower + " ofa=" + (int) s.ofa
				+ " cowl=" + Math.max(0, (s.cowlUntil - now) / 20) + "s"), false);
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}
}
