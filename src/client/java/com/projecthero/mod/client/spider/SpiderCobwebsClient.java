package com.projecthero.mod.client.spider;

import com.projecthero.mod.network.SpiderCobwebSyncPayload;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * v0.16.1: this client's copy of which cobwebs in its level are its own Spider-Man's / its squad's Spider-Man's (fed
 * by {@link SpiderCobwebSyncPayload}), so the local player walks through them like the server lets him. Cleared on a
 * world change.
 */
public final class SpiderCobwebsClient {
	private static final LongOpenHashSet MINE = new LongOpenHashSet();

	private SpiderCobwebsClient() {
	}

	public static void accept(SpiderCobwebSyncPayload p) {
		for (long pos : p.positions()) {
			if (p.add()) {
				MINE.add(pos);
			} else {
				MINE.remove(pos);
			}
		}
	}

	public static boolean contains(long pos) {
		return MINE.contains(pos);
	}

	public static void clear() {
		MINE.clear();
	}
}
