package com.projecthero.mod.command;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.raid.SupervillainRaid;
import com.projecthero.mod.event.raid.SupervillainRaidStarter;
import com.projecthero.mod.event.raid.ZombieRaidStarter;
import com.projecthero.mod.grave.GraveboundCurse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /heroraid start <supervillain|gravebound> | stop | cleartimers} -- one place to drive both
 * ProjectHero raids while testing, without remembering which sub-verb each of {@code /supervillainraid}
 * and {@code /heropack zombieraid} uses. Op-only ({@code hasPermission(2)}).
 *
 * <ul>
 *   <li>{@code start} begins the chosen raid right where you are standing, skipping its timer.</li>
 *   <li>{@code stop} force-ends <em>every</em> active ProjectHero world event and clears every online
 *       player's Gravebound Curse.</li>
 *   <li>{@code cleartimers} removes the pending timers only -- every curse countdown, and any raid
 *       still in its pre-wave countdown -- while leaving a raid that has already started running.</li>
 * </ul>
 */
public final class HeroRaidCommand {
	private HeroRaidCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("heroraid")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("start")
						.then(Commands.literal("supervillain").executes(c -> startSupervillain(c)))
						.then(Commands.literal("gravebound").executes(c -> startGravebound(c)))
						.then(Commands.literal("zombie").executes(c -> startGravebound(c))))
				.then(Commands.literal("stop").executes(HeroRaidCommand::stopAll))
				.then(Commands.literal("cleartimers").executes(HeroRaidCommand::clearTimers)));
	}

	private static int startSupervillain(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		if (!SupervillainRaidStarter.startAt(level, player.blockPosition())) {
			c.getSource().sendFailure(Component.literal("Could not start a Supervillain Raid here (one active nearby?)."));
			return 0;
		}
		EventInstance ev = EventManager.at(level, player.blockPosition());
		if (ev instanceof SupervillainRaid raid) {
			raid.debugSkipCountdown(level);
		}
		c.getSource().sendSuccess(() -> Component.literal("Supervillain Raid started here (countdown skipped)."), true);
		return 1;
	}

	private static int startGravebound(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!ZombieRaidStarter.start(player.serverLevel(), player.blockPosition())) {
			c.getSource().sendFailure(Component.literal("A Gravebound Raid is already running nearby."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Gravebound Raid started here."), true);
		return 1;
	}

	private static int stopAll(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		int events = 0;
		for (EventInstance e : EventManager.active(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level == null) {
				continue;
			}
			e.abort(level);
			EventSavedData.get(level).remove(e.id());
			events++;
		}
		int curses = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (GraveboundCurse.clear(player, false)) {
				curses++;
			}
		}
		final int ev = events;
		final int cu = curses;
		c.getSource().sendSuccess(() -> Component.literal(
				"Stopped " + ev + " world event(s) and cleared " + cu + " curse(s)."), true);
		return 1;
	}

	private static int clearTimers(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		int curses = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (GraveboundCurse.clear(player, false)) {
				curses++;
			}
		}
		int countdowns = 0;
		for (EventInstance e : EventManager.active(server)) {
			if (!(e instanceof SupervillainRaid raid) || !raid.inCountdown()) {
				continue;
			}
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level == null) {
				continue;
			}
			raid.abort(level);
			EventSavedData.get(level).remove(e.id());
			countdowns++;
		}
		final int cu = curses;
		final int cd = countdowns;
		c.getSource().sendSuccess(() -> Component.literal(
				"Cleared " + cu + " curse timer(s) and " + cd + " pending raid countdown(s). Running raids left alone."),
				true);
		return 1;
	}
}
