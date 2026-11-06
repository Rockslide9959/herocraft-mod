package com.projecthero.mod.stand;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.stand.data.StandState;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.2 admin / test commands (op only), hung off {@code /projecthero power tune}:
 * <ul>
 *   <li>{@code stand stamina <0-100>} -- sets your Stamina;</li>
 *   <li>{@code stand cooldowns} -- clears every move's cooldown.</li>
 * </ul>
 * The power itself is granted like every other hero's: {@code /projecthero power grant <player> stand}.
 */
public final class StandCommand {
	private StandCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("stand")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("stamina")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, StandConfig.STAMINA_MAX))
								.executes(StandCommand::stamina)))
				.then(Commands.literal("cooldowns").executes(StandCommand::cooldowns));
	}

	private static ServerPlayer user(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!Stands.hasPower(player)) {
			c.getSource().sendFailure(Component.literal("You have no Stand"));
			return null;
		}
		return player;
	}

	private static int stamina(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = user(c);
		if (player == null) {
			return 0;
		}
		float v = FloatArgumentType.getFloat(c, "amount");
		Stands.setStamina(player, v);
		c.getSource().sendSuccess(() -> Component.literal("Stamina set to " + Math.round(v)), false);
		return 1;
	}

	private static int cooldowns(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = user(c);
		if (player == null) {
			return 0;
		}
		StandState n = Stands.state(player).copy();
		n.abilityReadyAt.clear();
		Stands.save(player, n);
		c.getSource().sendSuccess(() -> Component.literal("Stand cooldowns cleared"), false);
		return 1;
	}
}
