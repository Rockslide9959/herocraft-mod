package com.projecthero.mod.command;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.worthiness.Worthiness;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;


import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /superhero} -- the <b>survival</b>, non-op self-service command. Any player can run it on
 * themselves:
 * <ul>
 *   <li>{@code /superhero worthiness} -- read your hidden Mjolnir worthiness score;</li>
 *   <li>{@code /superhero status} -- a summary of your Hero-Tier powers;</li>
 *   <li>{@code /superhero arcreactor remove} -- extract your Arc Reactor: lose the Tony Stark power
 *       and get an Arc Reactor item back (re-activate it any time to become Tony Stark again).</li>
 * </ul>
 * Admin verbs stay on {@code /thor} and {@code /ironman} (op 2).
 */
public final class SuperheroCommand {
	private SuperheroCommand() {
	}


	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("hero")
				.then(Commands.literal("worthiness").executes(SuperheroCommand::worthiness))
				.then(Commands.literal("status").executes(SuperheroCommand::status))
				.then(Commands.literal("arcreactor")
						.then(Commands.literal("remove").executes(SuperheroCommand::removeReactor)));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static int worthiness(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = self(c);
		int score = Worthiness.getScore(p);
		boolean worthy = Worthiness.isWorthy(p);
		c.getSource().sendSuccess(() -> Component.translatable("commands.projecthero.superhero.worthiness",
				score, Worthiness.THRESHOLD, Component.translatable(worthy
						? "commands.projecthero.superhero.worthy" : "commands.projecthero.superhero.unworthy")
						.withStyle(worthy ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
		return score;
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = self(c);
		var s = TonyStark.state(p);
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Worthy of Mjolnir: %s | Tony Stark: %s%s", Worthiness.isWorthy(p), s.hasPower,
				s.hasPower ? "  (tech " + s.techLevel + ", suits: " + TonyStark.builtSuitIds(p) + ")" : "")), false);
		return 1;
	}

	private static int removeReactor(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = self(c);
		if (!TonyStark.hasPower(p)) {
			c.getSource().sendFailure(Component.translatable("commands.projecthero.superhero.no_reactor"));
			return 0;
		}
		if (com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(p)) {
			c.getSource().sendFailure(Component.translatable("commands.projecthero.superhero.remove_suit_first"));
			return 0;
		}
		TonyStark.revoke(p);
		ItemStack reactor = new ItemStack(IronManItems.ARC_REACTOR);
		if (!p.getInventory().add(reactor)) {
			p.drop(reactor, false);
		}
		if (p.level() instanceof net.minecraft.server.level.ServerLevel level) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(),
					net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.7f);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
					p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
		}
		c.getSource().sendSuccess(() -> Component.translatable("commands.projecthero.superhero.reactor_removed")
				.withStyle(ChatFormatting.AQUA), false);
		return 1;
	}
}
