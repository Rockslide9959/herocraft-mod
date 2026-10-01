package com.projecthero.mod.horde;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.14.12: {@code /projecthero raid <verb> zombie_horde|skeleton_horde|spider_horde}. {@code start} places the horde
 * block at your feet and wakes it; {@code end} stops every horde of that kind (its block goes back to sleep);
 * {@code advancetimer} jumps the nearest one to its next stage (countdown -> wave 1, a wave -> cleared, the boss ->
 * dead); {@code removetimer} has nothing to do for a horde.
 */
public final class HordeCommand {
	private HordeCommand() {
	}

	public static int verb(CommandContext<CommandSourceStack> c, String verb, HordeKind kind) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = player.serverLevel();
				BlockPos pos = player.blockPosition();
				level.setBlock(pos, kind.block().defaultBlockState(), 3);
				if (!Hordes.start(level, pos, kind)) {
					c.getSource().sendFailure(Component.literal("A " + kind.typeId() + " is already running nearby."));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.literal(kind.typeId() + " started here."), true);
				return 1;
			}
			case "end" -> {
				int n = 0;
				for (EventInstance e : EventManager.active(server)) {
					ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
					if (level != null && e instanceof HordeRaid raid && raid.kind() == kind) {
						e.abort(level);
						EventSavedData.get(level).remove(e.id());
						n++;
					}
				}
				int ended = n;
				c.getSource().sendSuccess(() -> Component.literal("Ended " + ended + " " + kind.typeId() + "(s)."), true);
				return n;
			}
			case "advancetimer" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				HordeRaid best = null;
				for (EventInstance e : EventManager.active(server)) {
					if (e instanceof HordeRaid raid && raid.kind() == kind && raid.isAt(player.serverLevel(), player.blockPosition(), 200)) {
						best = raid;
					}
				}
				if (best == null) {
					c.getSource().sendFailure(Component.literal("No " + kind.typeId() + " near you."));
					return 0;
				}
				best.debugAdvance(player.serverLevel());
				c.getSource().sendSuccess(() -> Component.literal("Advanced the " + kind.typeId() + "."), true);
				return 1;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("Hordes have no timer to remove."), false);
				return 0;
			}
		}
	}
}
