package com.projecthero.mod.sentinel;

import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.sentinel.entity.SentinelEntityTypes;
import com.projecthero.mod.sentinel.item.SentinelItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.15.1: the Sentinel Purge -- one entry point, called from {@code ProjectHeroMod.onInitialize}: the robots, the items,
 * the event type and the "a fighter died" hook. See {@link SentinelPurgeEvent}.
 *
 * <p>Three ways in: the natural trigger ({@link SentinelSpawner}, superhumans only), the Trask Signal item, and
 * {@code /projecthero raid start sentinel_purge}.
 */
public final class SentinelPurge {
	private SentinelPurge() {
	}

	public static void initialize() {
		SentinelEntityTypes.initialize();
		SentinelItems.initialize();
		EventTypes.register(SentinelPurgeEvent.TYPE_ID, SentinelPurgeEvent::new);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				for (SentinelPurgeEvent purge : SentinelPurgeEvent.all(p.server)) {
					if (purge.dimension() == p.level().dimension() && purge.isFighter(p.getUUID())) {
						purge.onFighterDied(p.serverLevel(), p);
					}
				}
			}
		});
	}

	/**
	 * Starts a purge at {@code at}, triggered by {@code activator} (may be null). Everyone eligible nearby is locked in
	 * as a fighter. Null if another purge is already running too close.
	 */
	public static SentinelPurgeEvent start(ServerLevel level, BlockPos at, ServerPlayer activator) {
		if (EventManager.anyActiveNear(level, at, SentinelPurgeEvent.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
			return null;
		}
		SentinelPurgeEvent purge = new SentinelPurgeEvent(UUID.randomUUID());
		if (!EventManager.start(level, purge, at)) {
			return null;
		}
		purge.activate(level, activator);
		return purge;
	}

	/** Ends (aborts and forgets) every running purge. */
	public static int endAll(MinecraftServer server) {
		int n = 0;
		for (EventInstance e : EventManager.active(server)) {
			ServerLevel level = e.dimension() == null ? null : server.getLevel(e.dimension());
			if (level != null && e instanceof SentinelPurgeEvent) {
				e.abort(level);
				EventSavedData.get(level).remove(e.id());
				n++;
			}
		}
		return n;
	}

	/**
	 * {@code /projecthero raid <verb> sentinel_purge}: {@code start} begins a purge where you stand; {@code end} stops every
	 * purge; {@code advancetimer} jumps the nearest to its next stage (detection -> wave 1, a wave -> cleared, Master Mold
	 * -> destroyed); {@code removetimer} has nothing to do.
	 */
	public static int command(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				if (start(player.serverLevel(), player.blockPosition(), player) == null) {
					c.getSource().sendFailure(Component.literal("A Sentinel Purge is already running nearby."));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.literal("Sentinel Purge started here."), true);
				return 1;
			}
			case "end" -> {
				int n = endAll(server);
				c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " Sentinel Purge(s)."), true);
				return n;
			}
			case "advancetimer" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				SentinelPurgeEvent best = null;
				for (SentinelPurgeEvent p : SentinelPurgeEvent.all(server)) {
					if (p.isAt(player.serverLevel(), player.blockPosition(), 256)) {
						best = p;
					}
				}
				if (best == null) {
					c.getSource().sendFailure(Component.literal("No Sentinel Purge near you."));
					return 0;
				}
				best.debugAdvance(player.serverLevel());
				c.getSource().sendSuccess(() -> Component.literal("Advanced the Sentinel Purge."), true);
				return 1;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("The Sentinel Purge has no timer to remove."), false);
				return 0;
			}
		}
	}
}
