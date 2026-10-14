package com.projecthero.mod.city;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

/**
 * v0.16.2 City part B: {@code /projecthero city ...} (ops). {@code status} shows the City you stand in;
 * {@code repair} sends a worker now (no delay, any time of day); {@code optout <radius>} / {@code reset <radius>} mark or
 * clear player-renovated cells around you; {@code sweep} diffs your chunk against the blueprint now.
 */
public final class CityCommands {
	private CityCommands() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("city")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("status").executes(CityCommands::status))
				.then(Commands.literal("repair").executes(CityCommands::repairNow))
				.then(Commands.literal("sweep").executes(CityCommands::sweepHere))
				.then(Commands.literal("optout").then(Commands.argument("radius", IntegerArgumentType.integer(0, 64))
						.executes(c -> mark(c, true))))
				.then(Commands.literal("reset").then(Commands.argument("radius", IntegerArgumentType.integer(0, 64))
						.executes(c -> mark(c, false))));
	}

	private static CityArea here(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		CityArea city = CityLocator.cityAround(level, BlockPos.containing(c.getSource().getPosition()));
		if (city == null) {
			c.getSource().sendFailure(Component.literal("You are not in a City."));
		}
		return city;
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		CityArea city = here(c);
		if (city == null) {
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		CitySavedData.CityRecord rec = CitySavedData.get(level).city(city.id());
		int workers = CityRepair.workers(level, city).size();
		CityRepair.Job job = CityRepair.job(level, city.id());
		c.getSource().sendSuccess(() -> Component.literal("City " + city.id() + " " + city.bounds() + ": " + city.pieces().size() + " pieces, "
				+ rec.pendingBlocks() + " damaged blocks in " + rec.areas.size() + " areas, " + rec.optOut.size() + " renovated cells, "
				+ workers + " Repair Worker(s)" + (job == null ? "" : ", a trip running (" + job.placed() + "/" + job.size() + ")")), false);
		return 1;
	}

	private static int repairNow(CommandContext<CommandSourceStack> c) {
		CityArea city = here(c);
		if (city == null) {
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		if (CityRepair.job(level, city.id()) != null) {
			c.getSource().sendFailure(Component.literal("A repair trip is already running."));
			return 0;
		}
		List<Villager> workers = CityRepair.workers(level, city);
		Villager worker = workers.isEmpty() ? null : workers.get(0);
		CityRepair.Job job = CityRepair.startTripNow(level, city, worker);
		if (job == null) {
			c.getSource().sendFailure(Component.literal("Nothing to repair."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Repair trip started: " + job.size() + " block(s)"
				+ (worker == null ? " (no Repair Worker: filling in on its own)." : ".")), true);
		return 1;
	}

	private static int sweepHere(CommandContext<CommandSourceStack> c) {
		CityArea city = here(c);
		if (city == null) {
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		int n = CityDamageTracker.sweepChunk(level, city, new ChunkPos(BlockPos.containing(c.getSource().getPosition())), CitySavedData.get(level),
				level.getGameTime());
		c.getSource().sendSuccess(() -> Component.literal("Sweep queued " + n + " damaged block(s) in this chunk."), false);
		return n;
	}

	private static int mark(CommandContext<CommandSourceStack> c, boolean optOut) throws CommandSyntaxException {
		CityArea city = here(c);
		if (city == null) {
			return 0;
		}
		int r = IntegerArgumentType.getInteger(c, "radius");
		ServerLevel level = c.getSource().getLevel();
		CitySavedData data = CitySavedData.get(level);
		BlockPos centre = BlockPos.containing(c.getSource().getPosition());
		int n = 0;
		for (BlockPos cell : BlockPos.betweenClosed((centre.getX() - r) >> 2, (centre.getY() - r) >> 2, (centre.getZ() - r) >> 2,
				(centre.getX() + r) >> 2, (centre.getY() + r) >> 2, (centre.getZ() + r) >> 2)) {
			BlockPos p = new BlockPos(cell.getX() << 2, cell.getY() << 2, cell.getZ() << 2);
			if (optOut) {
				data.optOut(city.id(), p);
			} else {
				data.city(city.id()).optOut.remove(CitySavedData.cellOf(p));
			}
			n++;
		}
		data.setDirty();
		int cells = n;
		c.getSource().sendSuccess(() -> Component.literal((optOut ? "Marked " : "Cleared ") + cells + " cell(s)."), true);
		return cells;
	}
}
