package com.projecthero.mod.miraculous.ladybug;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.ladybug.entity.LadybugEntityTypes;
import com.projecthero.mod.miraculous.ladybug.item.LadybugItems;
import com.projecthero.mod.miraculous.ladybug.network.LadybugGlowPayload;
import com.projecthero.mod.miraculous.ladybug.network.LadybugPingPayload;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * v0.16.6: Ladybug server entry point (called from {@code MiraculousMod.initialize}): the kit, the Lucky Charm items and
 * the thrown TNT, the outline / ping payloads, and the server-wide ticks (roots, flings, the Miraculous Ladybug waves).
 * Block changes reach {@link LadybugRestore} through {@code CityLevelChunkMixin}.
 */
public final class LadybugMod {
	private LadybugMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.TIKKI, new LadybugKit());
		LadybugItems.initialize();
		LadybugEntityTypes.initialize();
		PayloadTypeRegistry.playS2C().register(LadybugGlowPayload.TYPE, LadybugGlowPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LadybugPingPayload.TYPE, LadybugPingPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			LadybugMoves.serverTick(server);
			LadybugRestore.serverTick(server);
		});
		ServerEntityEvents.ENTITY_LOAD.register(LuckyCharm::onEntityLoad);
	}
}
