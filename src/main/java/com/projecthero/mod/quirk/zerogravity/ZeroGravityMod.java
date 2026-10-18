package com.projecthero.mod.quirk.zerogravity;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7: the Zero Gravity Quirk's server init -- the kit, and the safety nets that make sure nothing she floats is ever
 * left weightless: a floated player who logs out is let go first; anything loaded (or a player joining / respawning)
 * with the float tag that nobody floats gets its gravity back; orphaned rubble is removed; on SERVER_STOPPING every
 * float ends and every piece of rubble goes (the ground itself is put back by {@code GeoTerrain.restoreAll}).
 */
public final class ZeroGravityMod {
	private ZeroGravityMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.ZERO_GRAVITY, new ZeroGravityKit());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			ZeroGravityFloat.cleanup(entity);
			ZeroGravityDebris.cleanup(entity);
		});
		ServerPlayerEvents.JOIN.register(ZeroGravityFloat::cleanup);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ZeroGravityFloat.releaseTarget(oldPlayer);
			ZeroGravityFloat.cleanup(newPlayer);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			ZeroGravityFloat.releaseTarget(p);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (com.projecthero.mod.quirk.Quirks.holds(p, Quirk.ZERO_GRAVITY)) {
					ZeroGravityMoves.endAll(p);
				}
			}
			ZeroGravityFloat.restoreEverything(server);
			ZeroGravityDebris.removeEverything(server);
		});
	}
}
