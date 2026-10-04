package com.projecthero.mod.command;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.event.entity.PillagerSpy;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.raid.SupervillainMark;
import com.projecthero.mod.event.raid.SupervillainRaid;
import com.projecthero.mod.event.raid.SupervillainRaidStarter;
import com.projecthero.mod.event.raid.SupervillainVillages;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;


import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

/**
 * {@code /supervillainraid start|mark [targets]|omen|markvillage|boss|spy|clear|status} -- Supervillain Village Raid
 * development and testing commands (spec section 47). Op-only ({@code hasPermission(2)}); the survival trigger is a
 * Pillager Spy marking a player (its hit, or its death) and that player then entering a village (v0.14.21).
 */
public final class SupervillainRaidCommand {
	private SupervillainRaidCommand() {
	}


	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("supervillainraid")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("start").executes(SupervillainRaidCommand::start))
				.then(Commands.literal("mark").executes(c -> giveMark(c, java.util.List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> giveMark(c, EntityArgument.getPlayers(c, "targets")))))
				.then(Commands.literal("omen").executes(SupervillainRaidCommand::omen))
				.then(Commands.literal("markvillage").executes(SupervillainRaidCommand::mark))
				.then(Commands.literal("boss").executes(SupervillainRaidCommand::boss))
				.then(Commands.literal("spy").executes(SupervillainRaidCommand::spy))
				.then(Commands.literal("clear").executes(SupervillainRaidCommand::clear))
				.then(Commands.literal("status").executes(SupervillainRaidCommand::status));
	}

	/** Start the event at the nearest village and immediately skip the 10-minute countdown. */
	private static int start(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		if (!SupervillainRaidStarter.startAt(level, player.blockPosition())) {
			c.getSource().sendFailure(Component.literal("Could not start a Supervillain Raid here (one already active nearby?)."));
			return 0;
		}
		SupervillainRaid raid = raidHere(player);
		if (raid != null) {
			raid.debugSkipCountdown(level);
		}
		c.getSource().sendSuccess(() -> Component.literal("Supervillain Raid started at " + player.blockPosition()
				+ " (countdown skipped)"), true);
		return 1;
	}

	/** v0.14.21: give the Supervillain's Mark (the Pillager Spy's Bad Omen) to players. */
	private static int giveMark(CommandContext<CommandSourceStack> c, java.util.Collection<ServerPlayer> targets) {
		int n = 0;
		for (ServerPlayer p : targets) {
			if (SupervillainMark.mark(p)) {
				n++;
			}
		}
		final int marked = n;
		if (marked == 0) {
			c.getSource().sendFailure(Component.literal("Nobody was marked (spectator, or an omen already running)."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Gave the Supervillain's Mark to " + marked + " player(s)."), true);
		return marked;
	}

	/** v0.14.21: turn your mark into the omen here if you can, or make a running omen fire now. */
	private static int omen(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (SupervillainMark.hasOmen(player)) {
			SupervillainMark.expireOmenNow(player);
			c.getSource().sendSuccess(() -> Component.literal("Supervillain Omen fires next tick."), true);
			return 1;
		}
		if (!SupervillainMark.isMarked(player)) {
			c.getSource().sendFailure(Component.literal("You are not marked -- use /supervillainraid mark first."));
			return 0;
		}
		if (!SupervillainMark.tryConvert(player)) {
			c.getSource().sendFailure(Component.literal(
					"The mark cannot turn here (not in a village, Peaceful, or a Supervillain Raid is already running nearby)."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Mark turned into the Supervillain Omen."), true);
		return 1;
	}

	/** Mark the nearest village directly (the pre-v0.14.21 trigger) and run the full preparation timer. */
	private static int mark(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!SupervillainRaidStarter.startAt(player.serverLevel(), player.blockPosition())) {
			c.getSource().sendFailure(Component.literal("Could not mark a village here."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Village marked -- 10-minute countdown running."), true);
		return 1;
	}

	/** Skip straight to Wave 6 (the Supervillain), starting a raid first if needed. */
	private static int boss(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		SupervillainRaid raid = raidHere(player);
		if (raid == null) {
			SupervillainRaidStarter.startAt(level, player.blockPosition());
			raid = raidHere(player);
		}
		if (raid == null) {
			c.getSource().sendFailure(Component.literal("Could not start a raid to skip."));
			return 0;
		}
		raid.debugSkipToBoss(level);
		c.getSource().sendSuccess(() -> Component.literal("Skipped to Wave 6 -- the Supervillain is inbound."), true);
		return 1;
	}

	private static int spy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		PillagerSpy spy = RaidEntityTypes.PILLAGER_SPY.create(level);
		if (spy == null) {
			return 0;
		}
		var pos = player.blockPosition().relative(player.getDirection(), 4);
		spy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0.0f);
		spy.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
		level.addFreshEntity(spy);
		c.getSource().sendSuccess(() -> Component.literal("Spawned a Pillager Spy."), true);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		SupervillainRaid raid = raidHere(player);
		int cleared = 0;
		for (EventInstance e : EventManager.active(level.getServer())) {
			if (e instanceof SupervillainRaid sv) {
				sv.abort(level);
				EventSavedData.get(level).remove(sv.id());
				cleared++;
			}
		}
		SupervillainVillages.get(level).clearCooldown(player.blockPosition());
		SupervillainMark.clear(player);
		final int n = cleared;
		c.getSource().sendSuccess(() -> Component.literal("Cleared " + n
				+ " Supervillain Raid(s), this village's cooldown and your Supervillain's Mark."), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		SupervillainRaid raid = raidHere(player);
		long cd = SupervillainVillages.get(level).cooldownSecondsRemaining(level, player.blockPosition());
		String mark = SupervillainMark.hasOmen(player) ? "omen running"
				: SupervillainMark.isMarked(player) ? "marked" : "none";
		if (raid == null) {
			c.getSource().sendSuccess(() -> Component.literal("No Supervillain Raid here. Village cooldown: "
					+ (cd > 0 ? (cd / 60) + "m" : "ready") + " | your mark: " + mark), false);
			return 0;
		}
		String villain = raid.variant() == null ? "pending" : raid.variant().id();
		String power = raid.powerKey().isEmpty() ? "pending" : BossPowers.displayName(raid.powerKey()).getString();
		int participants = raid.participants().eligibleCount();
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Supervillain Raid @ %s | phase %s | wave %d/%d | %s remaining | villain %s | power %s | participants %d",
				raid.center(), raid.phaseName(), raid.wave(), raid.totalWaves(),
				raid.inCountdown() ? raid.secondsRemaining() + "s" : "-", villain, power, participants)), false);
		return 1;
	}

	private static SupervillainRaid raidHere(ServerPlayer player) {
		EventInstance event = EventManager.at(player.serverLevel(), player.blockPosition());
		if (event instanceof SupervillainRaid raid) {
			return raid;
		}
		event = EventManager.forPlayer(player);
		return event instanceof SupervillainRaid raid ? raid : null;
	}
}
