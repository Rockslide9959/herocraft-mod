package com.projecthero.mod.quirk;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7 Quirk admin / test commands (op only), at {@code /projecthero quirk} and {@code /projecthero power tune quirk}:
 * <ul>
 *   <li>{@code costume} -- (v0.16.7) only says the costume is now a craftable suit;</li>
 *   <li>{@code meter <0-100>} -- sets the kit's meter (Heat, Dry Eyes...);</li>
 *   <li>{@code erase <seconds>} -- Erases YOU for testing (every power off; {@code 0} lifts it);</li>
 *   <li>{@code info} -- which Quirk, meter, overload.</li>
 * </ul>
 * The Quirks are granted like every other hero: {@code /projecthero power grant quirk_hellflame [player]}.
 */
public final class QuirkCommand {
	private QuirkCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("quirk")
				.requires(source -> source.hasPermission(2))
				// v0.16.7 (user): the costume is a craftable suit now -- a Quirk no longer changes your look
				.then(Commands.literal("costume").executes(c -> {
					c.getSource().sendFailure(Component.translatable("message.projecthero.quirk.costume_is_suit"));
					return 0;
				}))
				.then(Commands.literal("meter")
						.then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
							ServerPlayer p = holder(c);
							if (p == null) {
								return 0;
							}
							int v = IntegerArgumentType.getInteger(c, "value");
							Quirks.setMeter(p, v);
							c.getSource().sendSuccess(() -> Component.literal("Quirk meter set to " + v), false);
							return 1;
						})))
				.then(Commands.literal("erase")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600)).executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							int s = IntegerArgumentType.getInteger(c, "seconds");
							if (s == 0) {
								PowerSuppression.release(p);
							} else {
								PowerSuppression.suppress(p, s * 20);
							}
							c.getSource().sendSuccess(() -> Component.literal(s == 0 ? "Erasure lifted" : "Erased for " + s + " s"), false);
							return 1;
						})))
				.then(Commands.literal("info").executes(QuirkCommand::info));
	}

	private static ServerPlayer holder(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!Quirks.hasPower(p)) {
			c.getSource().sendFailure(Component.literal("You don't have a Quirk"));
			return null;
		}
		return p;
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = holder(c);
		if (p == null) {
			return 0;
		}
		Quirk q = Quirks.quirk(p);
		if (q != null && q.standalone) {
			// v0.16.7: One For All -- the form and the timer
			var am = com.projecthero.mod.allmight.AllMight.state(p);
			String t = q.quirkName + " (" + q.heroName + ") " + (am.fullPower ? "Power Form" : "Base Form") + ", timer "
					+ com.projecthero.mod.allmight.AllMight.clock(com.projecthero.mod.allmight.AllMight.timeLeft(am)) + " / "
					+ com.projecthero.mod.allmight.AllMight.clock(com.projecthero.mod.allmight.AllMight.timerMax())
					+ (am.plusUltra ? ", PLUS ULTRA" : "");
			c.getSource().sendSuccess(() -> Component.literal(t), false);
			return 1;
		}
		QuirkState s = Quirks.state(p);
		String text = q.quirkName + " (" + q.heroName + ") meter " + Math.round(Quirks.meter(p))
				+ (Quirks.overloaded(p) ? ", overloaded " + Quirks.overloadLeft(p) / 20 + " s" : "")
				+ (PowerSuppression.isSuppressed(p) ? ", ERASED " + PowerSuppression.remaining(p) / 20 + " s" : "");
		c.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
