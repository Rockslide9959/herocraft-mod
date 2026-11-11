package com.projecthero.mod.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3 Undead kit rework (Z, Shift+Z, V, Shift+V, the katana, the shoulder sword): its lifecycle in one place, reached from
 * {@link UndeadBoneSword} (initialize, the per-player tick, the session state, clear on death / world change / leaving).
 * Everything static here is dropped by {@link #clearSessionState} (from {@code ServerStateReset} through
 * {@code UndeadAbilities.clearSessionState}).
 */
public final class UndeadKit {
	/** player -> the shoulder sword's wear last sent to the viewers. */
	private static final Map<UUID, Integer> SENT_WEAR = new ConcurrentHashMap<>();

	private UndeadKit() {
	}

	static void initialize() {
		UndeadKitNet.initialize();
		UndeadShoulderSword.initialize();
		ServerTickEvents.END_SERVER_TICK.register(UndeadKit::serverTick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				UndeadShoulderSword.onDeath(p);
			}
		});
	}

	static void clearSessionState() {
		UndeadVortex.clearSessionState();
		UndeadMeteor.clearSessionState();
		UndeadSpineWhip.clearSessionState();
		UndeadRibShot.clearSessionState();
		SENT_WEAR.clear();
	}

	static void forget(UUID id) {
		UndeadVortex.forget(id);
		UndeadMeteor.forget(id);
		UndeadSpineWhip.forget(id);
		UndeadRibShot.forget(id);
		SENT_WEAR.remove(id);
	}

	/** Death, a world change, leaving: the twists, the charge, the rib shot and the spine whip end (the meteor already falling still lands). */
	static void clear(ServerPlayer p) {
		UndeadVortex.cancel(p);
		UndeadMeteor.cancel(p);
		UndeadSpineWhip.cancel(p);
		UndeadRibShot.cancel(p);
		UndeadVortex.forget(p.getUUID());
	}

	/** Every tick for an Undead (from {@link UndeadBoneSword#tick}). */
	static void tick(ServerPlayer p) {
		UndeadVortex.tick(p);
		UndeadMeteor.tick(p);
		UndeadSpineWhip.tick(p);
		UndeadRibShot.tick(p);
	}

	/** Every server tick: the meteors landing; every player's shoulder sword (also once the power is gone). */
	private static void serverTick(MinecraftServer server) {
		UndeadMeteor.serverTick(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ItemStack s = p.getAttachedOrElse(UndeadShoulderSword.ATTACHMENT, null);
			if (s == null) {
				continue;
			}
			UndeadShoulderSword.tick(p);
			ItemStack now = p.getAttachedOrElse(UndeadShoulderSword.ATTACHMENT, null);
			if (now != null && !now.isEmpty()) {
				Integer last = SENT_WEAR.put(p.getUUID(), now.getDamageValue());
				if (last != null && last != now.getDamageValue()) {
					p.setAttached(UndeadShoulderSword.ATTACHMENT, now); // the wear (changed in place by the hits) to the viewers
				}
			} else {
				SENT_WEAR.remove(p.getUUID());
			}
		}
	}
}
