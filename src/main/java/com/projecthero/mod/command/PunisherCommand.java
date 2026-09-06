package com.projecthero.mod.command;

import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.VigilanteTraining;
import com.projecthero.mod.punisher.data.PunisherState;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin / testing commands for the Punisher:
 * {@code /punisher power grant|revoke | training start | arsenal unlock <weapon>|all | status}. Op 2.
 * The survival route is finding an Abandoned Vigilante Safehouse and completing Vigilante Training.
 */
public final class PunisherCommand {
	private PunisherCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("punisher")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("power")
						.then(Commands.literal("grant")
								.executes(c -> power(c, self(c), true))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> power(c, EntityArgument.getPlayer(c, "player"), true))))
						.then(Commands.literal("revoke")
								.executes(c -> power(c, self(c), false))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> power(c, EntityArgument.getPlayer(c, "player"), false)))))
				.then(Commands.literal("training")
						.then(Commands.literal("start").executes(c -> {
							VigilanteTraining.begin(self(c));
							return 1;
						})))
				.then(Commands.literal("arsenal")
						.then(Commands.literal("all").executes(c -> {
							ServerPlayer p = self(c);
							for (String w : new String[] { Firearms.RIFLE, Firearms.SHOTGUN, Firearms.SNIPER }) {
								Punisher.unlockWeapon(p, w);
							}
							return 1;
						}))
						.then(Commands.argument("weapon", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Firearms.all().keySet(), b))
								.executes(c -> {
									Punisher.unlockWeapon(self(c), StringArgumentType.getString(c, "weapon"));
									return 1;
								})))
				.then(Commands.literal("status").executes(c -> status(c, self(c)))));
	}

	private static int power(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean grant) {
		if (grant) {
			com.projecthero.mod.hero.HeroTiers.wipeAll(target);
			boolean ok = Punisher.grant(target);
			c.getSource().sendSuccess(() -> Component.literal(ok ? "Granted the Punisher" : "Already the Punisher"), true);
		} else {
			Punisher.revoke(target);
			c.getSource().sendSuccess(() -> Component.literal("Revoked the Punisher"), true);
		}
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		PunisherState s = Punisher.state(p);
		c.getSource().sendSuccess(() -> Component.literal(
				"Punisher=" + s.hasPower + " arsenal=" + s.unlockedWeapons
						+ " training=" + s.trainingActive
						+ " (kills " + s.killCount + " ranged " + s.rangedKillCount + " headshots " + s.headshotCount
						+ " craft " + s.craftedFirearm + " captain " + s.defeatedCaptain + ")"), false);
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}
}
