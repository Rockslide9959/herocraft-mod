package com.projecthero.mod.darkseid.raid;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventSavedData;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Darkseid Raid's hooks into the rest of the game:
 * <ul>
 *   <li><b>player death</b> -- starts that participant's re-entry delay and checks for a defeat;</li>
 *   <li><b>server stopping</b> -- every running invasion is ended and cleaned up before the world saves (the design
 *       calls for cleanup on a reload), and the activator gets their Boom Tube Beacon back;</li>
 *   <li><b>server started</b> -- anything that survived anyway (a crash skips the stop hook) is ended too; its entities
 *       remove themselves through their orphan guards as their chunks load.</li>
 * </ul>
 */
public final class DarkseidRaidEvents {
	private DarkseidRaidEvents() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
				for (DarkseidRaid raid : DarkseidRaid.all(level.getServer())) {
					if (raid.dimension() == level.dimension() && raid.isParticipant(player.getUUID())) {
						raid.onParticipantDied(level, player);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> endAll(server, true, "server stopping"));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> endAll(server, false, "left over from the last session"));
	}

	private static void endAll(MinecraftServer server, boolean refund, String why) {
		EventSavedData data = EventSavedData.get(server);
		List<EventInstance> ended = new ArrayList<>();
		for (EventInstance e : data.events()) {
			if (!(e instanceof DarkseidRaid raid)) {
				continue;
			}
			ServerLevel level = raid.dimension() == null ? null : server.getLevel(raid.dimension());
			if (level != null) {
				raid.refundOnAbort = refund;
				raid.abort(level);
			}
			ended.add(e);
		}
		for (EventInstance e : ended) {
			data.remove(e.id());
			ProjectHeroMod.LOGGER.info("[ProjectHero] Darkseid Raid {} ended ({})", e.id(), why);
		}
	}
}
