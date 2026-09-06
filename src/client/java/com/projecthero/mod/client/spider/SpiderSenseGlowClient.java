package com.projecthero.mod.client.spider;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Client-side store of the entity ids the local player's Spider-Sense currently flags as a threat
 * (v0.9.3). Fed by {@link com.projecthero.mod.network.SpiderSenseGlowPayload}; read by
 * {@code EntityGlowMixin} to outline exactly those entities red, for this viewer only.
 *
 * <p>The set is replaced wholesale on every packet and expires on its own a second after the last
 * update, so a mob stops glowing the moment the server stops sending it (or the player leaves the
 * server / the sense scan stops).
 */
public final class SpiderSenseGlowClient {
	private static final long EXPIRY_TICKS = 25L;

	private static IntSet ids = new IntOpenHashSet();
	private static long updatedAtTick;

	private SpiderSenseGlowClient() {
	}

	public static void accept(int[] incoming, long nowTick) {
		IntOpenHashSet next = new IntOpenHashSet(incoming.length);
		for (int id : incoming) {
			next.add(id);
		}
		ids = next;
		updatedAtTick = nowTick;
	}

	/** Whether {@code entityId} is a current Spider-Sense threat for the local player. */
	public static boolean isThreat(int entityId, long nowTick) {
		long age = nowTick - updatedAtTick;
		if (ids.isEmpty() || age < 0L || age > EXPIRY_TICKS) {
			return false;
		}
		return ids.contains(entityId);
	}

	public static void clear() {
		ids = new IntOpenHashSet();
		updatedAtTick = 0L;
	}
}
