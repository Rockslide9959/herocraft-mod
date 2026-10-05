package com.projecthero.mod.syndicate;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.14.25: {@code /projecthero raid <verb> syndicate}. {@code start} builds a warehouse right in front of you (its
 * roller door facing you, if the ground there will take one) and starts the bust; {@code end} stops every bust (their
 * stashes lock again); {@code advancetimer} jumps the nearest one to its next stage; {@code removetimer} has nothing to do.
 */
public final class SyndicateCommand {
	private SyndicateCommand() {
	}

	public static int verb(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = player.serverLevel();
				Direction forward = player.getDirection();
				BlockPos centre = player.blockPosition().relative(forward, SyndicateHideout.HALF + 6);
				SyndicateHideout.Site site = SyndicateHideout.check(level, centre.getX(), centre.getZ(), forward);
				if (site == null) {
					c.getSource().sendFailure(Component.literal("No room for a warehouse in front of you (needs fairly flat, dry, open ground)."));
					return 0;
				}
				BlockPos stash = SyndicateHideout.build(level, site, level.random);
				level.setBlock(stash, SyndicateItems.STASH.defaultBlockState().setValue(SyndicateStashBlock.FACING, forward), 3);
				if (!SyndicateBust.start(level, stash)) {
					c.getSource().sendFailure(Component.literal("Warehouse built, but a bust is already running nearby."));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.literal("Syndicate warehouse built and the bust started."), true);
				return 1;
			}
			case "end" -> {
				int n = 0;
				for (EventInstance e : EventManager.active(server)) {
					ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
					if (level != null && e instanceof SyndicateBust) {
						e.abort(level);
						EventSavedData.get(level).remove(e.id());
						n++;
					}
				}
				int ended = n;
				c.getSource().sendSuccess(() -> Component.literal("Ended " + ended + " Syndicate bust(s)."), true);
				return n;
			}
			case "advancetimer" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				SyndicateBust best = null;
				for (EventInstance e : EventManager.active(server)) {
					if (e instanceof SyndicateBust bust && bust.isAt(player.serverLevel(), player.blockPosition(), 200)) {
						best = bust;
					}
				}
				if (best == null) {
					c.getSource().sendFailure(Component.literal("No Syndicate bust near you."));
					return 0;
				}
				best.debugAdvance(player.serverLevel());
				c.getSource().sendSuccess(() -> Component.literal("Advanced the Syndicate bust."), true);
				return 1;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("A Syndicate bust has no timer to remove."), false);
				return 0;
			}
		}
	}
}
