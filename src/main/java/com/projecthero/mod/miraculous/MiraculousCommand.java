package com.projecthero.mod.miraculous;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.6 admin / test commands (op only), hung off {@code /projecthero power tune}:
 * <ul>
 *   <li>{@code ... miraculous kwami} -- your Kwami, form, hunger, countdown;</li>
 *   <li>{@code ... miraculous feed} -- the Kwami is fed (no food needed);</li>
 *   <li>{@code ... miraculous countdown <seconds>} -- sets the running countdown (starts one if transformed);</li>
 *   <li>{@code ... miraculous toggle} -- transform / detransform without saying the phrase.</li>
 * </ul>
 * The powers are granted like every other hero's: {@code /projecthero power grant <player> ladybug|cat_noir|rena_rouge}.
 */
public final class MiraculousCommand {
	private MiraculousCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("miraculous")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("kwami").executes(MiraculousCommand::info))
				.then(Commands.literal("feed").executes(MiraculousCommand::feed))
				.then(Commands.literal("toggle").executes(c -> {
					ServerPlayer p = holder(c);
					if (p == null) {
						return 0;
					}
					Miraculous.toggleForm(p);
					return 1;
				}))
				.then(Commands.literal("countdown")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, MiraculousConfig.COUNTDOWN_TICKS / 20))
								.executes(MiraculousCommand::countdown)));
	}

	private static ServerPlayer holder(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!Miraculous.hasPower(p)) {
			c.getSource().sendFailure(Component.literal("You don't hold a Miraculous"));
			return null;
		}
		return p;
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = holder(c);
		if (p == null) {
			return 0;
		}
		MiraculousState s = Miraculous.state(p);
		Kwami k = Miraculous.kwami(p);
		int left = Miraculous.countdownLeft(p) / 20;
		boolean out = KwamiCompanion.find(p) != null;
		c.getSource().sendSuccess(() -> Component.literal(k.id + " (" + k.heroKey + "): " + (s.transformed ? "transformed" : "detransformed")
				+ ", hungry " + s.hungry + ", countdown " + (left > 0 ? left + "s" : "none") + ", Kwami out " + out), false);
		return 1;
	}

	private static int feed(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = holder(c);
		if (p == null) {
			return 0;
		}
		Miraculous.edit(p, n -> n.hungry = false);
		c.getSource().sendSuccess(() -> Component.literal("Your Kwami is fed"), false);
		return 1;
	}

	private static int countdown(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = holder(c);
		if (p == null) {
			return 0;
		}
		if (!Miraculous.transformed(p)) {
			c.getSource().sendFailure(Component.literal("Transform first"));
			return 0;
		}
		int seconds = IntegerArgumentType.getInteger(c, "seconds");
		long end = p.level().getGameTime() + seconds * 20L;
		Miraculous.edit(p, n -> n.countdownEnd = end);
		c.getSource().sendSuccess(() -> Component.literal("Countdown: " + seconds + "s"), false);
		return 1;
	}
}
