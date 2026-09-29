package com.projecthero.mod.command;

import java.util.Arrays;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.projecthero.mod.darkseid.entity.DarkseidCombat;
import com.projecthero.mod.darkseid.entity.DarkseidEntity;
import com.projecthero.mod.darkseid.raid.DarkseidRaid;
import com.projecthero.mod.event.EventSavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.13.18: the Darkseid Raid's admin/test commands (op level 2), under {@code /projecthero raid}.
 * <ul>
 *   <li>{@code raid start darkseid} -- start an invasion where you stand, no beacon needed.</li>
 *   <li>{@code raid end darkseid} -- end (and fully clean up) every running invasion.</li>
 *   <li>{@code raid advancetimer darkseid} -- skip to the next beat: end the countdown, clear the wave, skip the
 *       entrance, break the shield, or drop Darkseid to the next phase threshold / defeat him.</li>
 *   <li>{@code raid removetimer darkseid} -- the invasion has no pending timer to cancel; reports status instead.</li>
 *   <li>{@code raid darkseid status | enrage | attack <name> | stagger} -- inspect the nearest invasion, trigger its
 *       soft enrage, make Darkseid use one attack now, or stagger him.</li>
 *   <li>{@code raid darkseid boxes} (v0.13.19) -- make the fight's Mother Box return happen now.</li>
 * </ul>
 */
public final class DarkseidRaidCommand {
	private DarkseidRaidCommand() {
	}

	static int verb(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		return switch (verb) {
			case "start" -> start(c);
			case "end" -> end(c);
			case "advancetimer" -> advance(c);
			default -> status(c);
		};
	}

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("darkseid")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("status").executes(DarkseidRaidCommand::status))
				.then(Commands.literal("enrage").executes(c -> {
					DarkseidRaid raid = nearest(c);
					if (raid == null) {
						return 0;
					}
					raid.debugEnrage();
					c.getSource().sendSuccess(() -> Component.literal("Soft enrage triggered."), true);
					return 1;
				}))
				.then(Commands.literal("boxes").executes(c -> {
					DarkseidRaid raid = nearest(c);
					if (raid == null) {
						return 0;
					}
					boolean ok = raid.debugBoxReturnNow();
					c.getSource().sendSuccess(() -> Component.literal(ok ? "Mother Box return triggered (needs every box dark)."
							: "Only during Darkseid's phases 1-3."), true);
					return ok ? 1 : 0;
				}))
				.then(Commands.literal("stagger").executes(c -> {
					DarkseidEntity d = nearestDarkseid(c);
					if (d == null) {
						return 0;
					}
					d.combat().forceStagger(100);
					c.getSource().sendSuccess(() -> Component.literal("Darkseid staggered."), true);
					return 1;
				}))
				.then(Commands.literal("attack").then(Commands.argument("attack", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
								Arrays.stream(DarkseidCombat.Attack.values()).map(a -> a.name().toLowerCase()), b))
						.executes(c -> {
							DarkseidEntity d = nearestDarkseid(c);
							if (d == null) {
								return 0;
							}
							DarkseidCombat.Attack attack;
							try {
								attack = DarkseidCombat.Attack.valueOf(StringArgumentType.getString(c, "attack").toUpperCase());
							} catch (IllegalArgumentException e) {
								c.getSource().sendFailure(Component.literal("Unknown attack."));
								return 0;
							}
							boolean ok = d.combat().forceAttack(c.getSource().getLevel(), attack);
							c.getSource().sendSuccess(() -> Component.literal(ok ? "Darkseid uses " + attack.name() + "."
									: "Darkseid is busy (or has no target)."), true);
							return ok ? 1 : 0;
						})));
	}

	private static int start(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (DarkseidRaid.startAt(player.serverLevel(), player.blockPosition(), player) == null) {
			c.getSource().sendFailure(Component.literal("An Apokolips Invasion is already running nearby."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Apokolips Invasion started here."), true);
		return 1;
	}

	private static int end(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		int n = 0;
		for (DarkseidRaid raid : DarkseidRaid.all(server)) {
			ServerLevel level = raid.dimension() == null ? null : server.getLevel(raid.dimension());
			if (level != null) {
				raid.abort(level);
				EventSavedData.get(level).remove(raid.id());
				n++;
			}
		}
		final int count = n;
		c.getSource().sendSuccess(() -> Component.literal("Ended " + count + " Apokolips Invasion(s)."), true);
		return n;
	}

	private static int advance(CommandContext<CommandSourceStack> c) {
		DarkseidRaid raid = nearest(c);
		if (raid == null) {
			return 0;
		}
		String what = raid.debugAdvance(c.getSource().getLevel());
		c.getSource().sendSuccess(() -> Component.literal("Apokolips Invasion: " + what), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		DarkseidRaid raid = nearest(c);
		if (raid == null) {
			return 0;
		}
		String line = raid.describe(c.getSource().getLevel());
		c.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static DarkseidRaid nearest(CommandContext<CommandSourceStack> c) {
		return nearest(c, true);
	}

	private static DarkseidRaid nearest(CommandContext<CommandSourceStack> c, boolean complain) {
		ServerLevel level = c.getSource().getLevel();
		DarkseidRaid best = null;
		double bestSq = Double.MAX_VALUE;
		for (DarkseidRaid raid : DarkseidRaid.all(c.getSource().getServer())) {
			if (raid.dimension() != level.dimension() || raid.center() == null) {
				continue;
			}
			double d = raid.center().distToCenterSqr(c.getSource().getPosition());
			if (d < bestSq) {
				bestSq = d;
				best = raid;
			}
		}
		if (best == null && complain) {
			c.getSource().sendFailure(Component.literal("No Apokolips Invasion is running in this dimension."));
		}
		return best;
	}

	private static DarkseidEntity nearestDarkseid(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		DarkseidRaid raid = nearest(c, false);
		DarkseidEntity d = raid == null ? null : raid.darkseid(level);
		if (d == null) {
			// a free-standing (summoned) Darkseid near the caller
			var found = level.getEntitiesOfClass(DarkseidEntity.class,
					new net.minecraft.world.phys.AABB(c.getSource().getPosition(), c.getSource().getPosition()).inflate(96));
			d = found.isEmpty() ? null : found.get(0);
		}
		if (d == null) {
			c.getSource().sendFailure(Component.literal("No Darkseid nearby."));
		}
		return d;
	}
}
