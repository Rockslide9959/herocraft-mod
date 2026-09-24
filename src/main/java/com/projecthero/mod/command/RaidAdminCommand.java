package com.projecthero.mod.command;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.raid.SupervillainRaid;
import com.projecthero.mod.event.raid.SupervillainRaidStarter;
import com.projecthero.mod.event.raid.ZombieRaid;
import com.projecthero.mod.event.raid.ZombieRaidStarter;
import com.projecthero.mod.grave.GraveboundCurse;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * v0.12.16: {@code /projecthero raid <start|end|removetimer|advancetimer> <supervillain|gravebound>}, op-only.
 *
 * <ul>
 *   <li>{@code start} begins the chosen raid where you stand, skipping its countdown.</li>
 *   <li>{@code end} force-ends every running raid of that kind (the Gravebound one also clears every curse).</li>
 *   <li>{@code removetimer} cancels the pending timer: a Supervillain raid still in its countdown is dropped,
 *       or every Gravebound Curse countdown is cleared. A raid already running is left alone.</li>
 *   <li>{@code advancetimer} finishes the pending timer now: the Supervillain countdown is skipped, or every
 *       Gravebound Curse countdown expires immediately and the raid begins.</li>
 * </ul>
 */
public final class RaidAdminCommand {
	private static final List<String> RAIDS = List.of("supervillain", "gravebound");
	private static final SuggestionProvider<CommandSourceStack> RAID_KEYS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(RAIDS, builder);

	private RaidAdminCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("raid")
				.requires(source -> source.hasPermission(2))
				.then(verb("start", RaidAdminCommand::start))
				.then(verb("end", RaidAdminCommand::end))
				.then(verb("removetimer", RaidAdminCommand::removeTimer))
				.then(verb("advancetimer", RaidAdminCommand::advanceTimer));
	}

	private interface Action {
		int run(CommandContext<CommandSourceStack> c, boolean supervillain) throws CommandSyntaxException;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> verb(String name, Action action) {
		return Commands.literal(name)
				.then(Commands.argument("raid", StringArgumentType.word()).suggests(RAID_KEYS)
						.executes(c -> {
							String raid = StringArgumentType.getString(c, "raid");
							if (!RAIDS.contains(raid)) {
								c.getSource().sendFailure(Component.literal("Unknown raid (supervillain, gravebound)"));
								return 0;
							}
							return action.run(c, raid.equals("supervillain"));
						}));
	}

	private static int start(CommandContext<CommandSourceStack> c, boolean supervillain) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		if (supervillain) {
			if (!SupervillainRaidStarter.startAt(level, player.blockPosition())) {
				c.getSource().sendFailure(Component.literal("Could not start a Supervillain Raid here (one active nearby?)."));
				return 0;
			}
			if (EventManager.at(level, player.blockPosition()) instanceof SupervillainRaid raid) {
				raid.debugSkipCountdown(level);
			}
			c.getSource().sendSuccess(() -> Component.literal("Supervillain Raid started here (countdown skipped)."), true);
			return 1;
		}
		if (!ZombieRaidStarter.start(level, player.blockPosition())) {
			c.getSource().sendFailure(Component.literal("A Gravebound Raid is already running nearby."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Gravebound Raid started here."), true);
		return 1;
	}

	private static boolean matches(EventInstance e, boolean supervillain) {
		return supervillain ? e instanceof SupervillainRaid : e instanceof ZombieRaid;
	}

	private static int end(CommandContext<CommandSourceStack> c, boolean supervillain) {
		MinecraftServer server = c.getSource().getServer();
		int events = 0;
		for (EventInstance e : EventManager.active(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level == null || !matches(e, supervillain)) {
				continue;
			}
			e.abort(level);
			EventSavedData.get(level).remove(e.id());
			events++;
		}
		int curses = 0;
		if (!supervillain) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (GraveboundCurse.clear(player, false)) {
					curses++;
				}
			}
		}
		final int ev = events;
		final int cu = curses;
		c.getSource().sendSuccess(() -> Component.literal("Ended " + ev + " raid(s)"
				+ (supervillain ? "." : " and cleared " + cu + " curse(s).")), true);
		return events + curses;
	}

	private static int removeTimer(CommandContext<CommandSourceStack> c, boolean supervillain) {
		MinecraftServer server = c.getSource().getServer();
		int n = 0;
		if (supervillain) {
			for (EventInstance e : EventManager.active(server)) {
				ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
				if (level != null && e instanceof SupervillainRaid raid && raid.inCountdown()) {
					raid.abort(level);
					EventSavedData.get(level).remove(e.id());
					n++;
				}
			}
		} else {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (GraveboundCurse.clear(player, false)) {
					n++;
				}
			}
		}
		final int count = n;
		c.getSource().sendSuccess(() -> Component.literal("Removed " + count + " pending "
				+ (supervillain ? "Supervillain countdown(s)." : "Gravebound curse timer(s).")), true);
		return n;
	}

	private static int advanceTimer(CommandContext<CommandSourceStack> c, boolean supervillain) {
		MinecraftServer server = c.getSource().getServer();
		int n = 0;
		if (supervillain) {
			for (EventInstance e : EventManager.active(server)) {
				ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
				if (level != null && e instanceof SupervillainRaid raid && raid.inCountdown()) {
					raid.debugSkipCountdown(level);
					n++;
				}
			}
		} else {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (GraveboundCurse.isCursed(player)) {
					GraveboundCurse.expireNow(player);
					n++;
				}
			}
		}
		final int count = n;
		c.getSource().sendSuccess(() -> Component.literal("Advanced " + count + " pending "
				+ (supervillain ? "Supervillain countdown(s)." : "Gravebound curse timer(s).")), true);
		return n;
	}
}
