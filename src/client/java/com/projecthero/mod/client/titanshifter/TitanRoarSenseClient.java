package com.projecthero.mod.client.titanshifter;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/** Client-side store of the entities the local shifter's last Titan Roar outlined in blue (fed by {@code TitanRoarSensePayload}). */
public final class TitanRoarSenseClient {
	private static final Int2LongOpenHashMap MARKED = new Int2LongOpenHashMap();

	private TitanRoarSenseClient() {
	}

	public static void accept(int[] ids, int ticks, long now) {
		MARKED.values().removeIf(t -> t < now);
		for (int id : ids) {
			MARKED.put(id, now + ticks);
		}
	}

	public static boolean isMarked(int id, long now) {
		return MARKED.getOrDefault(id, -1L) >= now;
	}

	public static void clear() {
		MARKED.clear();
	}
}
