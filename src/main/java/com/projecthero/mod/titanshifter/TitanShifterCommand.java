package com.projecthero.mod.titanshifter;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5: {@code /projecthero power tune titan ...} (op 2) -- test tools for Titan charges.
 * <pre>
 *   charges &lt;n&gt;          exactly n charges (clamped to the Titan's maximum); the timer restarts
 *   chargetime &lt;seconds&gt; the next charge is back in that many seconds
 *   status               charges, maximum, seconds to the next one
 * </pre>
 */
public final class TitanShifterCommand {
	private TitanShifterCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("titan")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("charges")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 9))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!TitanShifter.isShifter(player)) {
										c.getSource().sendFailure(Component.literal("Not a Titan Shifter"));
										return 0;
									}
									TitanShifter.setCharges(player, IntegerArgumentType.getInteger(c, "n"));
									c.getSource().sendSuccess(() -> Component.literal(status(player)), false);
									return 1;
								})))
				.then(Commands.literal("chargetime")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!TitanShifter.isShifter(player)) {
										c.getSource().sendFailure(Component.literal("Not a Titan Shifter"));
										return 0;
									}
									TitanShifter.setChargeTicksLeft(player, IntegerArgumentType.getInteger(c, "seconds") * 20);
									c.getSource().sendSuccess(() -> Component.literal(status(player)), false);
									return 1;
								})))
				.then(Commands.literal("status").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					c.getSource().sendSuccess(() -> Component.literal(TitanShifter.isShifter(player) ? status(player) : "Not a Titan Shifter"), false);
					return 1;
				}));
	}

	private static String status(ServerPlayer player) {
		var s = TitanShifter.state(player);
		int next = (TitanCharges.ticksToNext(s) + 19) / 20;
		return "Titan charges " + TitanCharges.current(s) + "/" + TitanCharges.max(s)
				+ (TitanCharges.full(s) ? " (full)" : ", next in " + next + "s");
	}
}
