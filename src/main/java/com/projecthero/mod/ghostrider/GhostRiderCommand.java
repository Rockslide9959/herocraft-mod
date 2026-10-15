package com.projecthero.mod.ghostrider;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.5 admin / test commands (op only), hung off {@code /projecthero}:
 * <ul>
 *   <li>{@code /projecthero power tune ghost_rider hellfire <0-100>} -- sets your Hellfire;</li>
 *   <li>{@code /projecthero power tune ghost_rider vengeance <0-100>} -- sets your Vengeance;</li>
 *   <li>{@code /projecthero power tune ghost_rider guilt <targets> <points>} -- makes creatures guilty.</li>
 * </ul>
 * The power itself is granted like every other hero's: {@code /projecthero power grant <player> ghost_rider}.
 */
public final class GhostRiderCommand {
	private GhostRiderCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("ghost_rider")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("hellfire")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, GhostRiderConfig.HELLFIRE_MAX))
								.executes(c -> set(c, true))))
				.then(Commands.literal("vengeance")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, GhostRiderConfig.VENGEANCE_MAX))
								.executes(c -> set(c, false))))
				.then(Commands.literal("guilt")
						.then(Commands.argument("targets", EntityArgument.entities())
								.then(Commands.argument("points", IntegerArgumentType.integer(1, GhostRiderConfig.GUILT_MAX))
										.executes(GhostRiderCommand::guilt))));
	}

	private static int set(CommandContext<CommandSourceStack> c, boolean hellfire) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!GhostRider.hasPower(player)) {
			c.getSource().sendFailure(Component.literal("You are not bound to the Spirit of Vengeance"));
			return 0;
		}
		float v = FloatArgumentType.getFloat(c, "amount");
		if (hellfire) {
			GhostRider.setHellfire(player, v);
		} else {
			GhostRider.setVengeance(player, v);
		}
		c.getSource().sendSuccess(() -> Component.literal((hellfire ? "Hellfire" : "Vengeance") + " set to " + Math.round(v)), false);
		return 1;
	}

	private static int guilt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		int points = IntegerArgumentType.getInteger(c, "points");
		int n = 0;
		for (Entity e : EntityArgument.getEntities(c, "targets")) {
			if (e instanceof LivingEntity le) {
				GhostRiderDamage.addGuilt(le, points);
				n++;
			}
		}
		int count = n;
		c.getSource().sendSuccess(() -> Component.literal(count + " creature(s) made guilty"), false);
		return n;
	}
}
