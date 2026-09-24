package com.projecthero.mod.client.wolverine;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Client-side store of what the local Wolverine's senses currently pick up (fed by
 * {@link com.projecthero.mod.network.WolverineSensePayload}, read by {@code EntityGlowMixin}). Hunters
 * are a short self-expiring set refreshed by the server's scan; sniffed entities keep their own expiry.
 */
public final class WolverineSenseClient {
	private static final long HUNTER_EXPIRY = 25L;

	private static final Int2LongOpenHashMap HUNTERS = new Int2LongOpenHashMap();
	private static final Int2LongOpenHashMap SNIFFED = new Int2LongOpenHashMap();

	private WolverineSenseClient() {
	}

	public static void accept(int[] hunters, int[] sniffed, int sniffTicks, long now) {
		HUNTERS.clear();
		for (int id : hunters) {
			HUNTERS.put(id, now + HUNTER_EXPIRY);
		}
		for (int id : sniffed) {
			SNIFFED.put(id, now + sniffTicks);
		}
		SNIFFED.values().removeIf(t -> t < now);
	}

	public static boolean isHunter(int id, long now) {
		return HUNTERS.getOrDefault(id, -1L) >= now;
	}

	public static boolean isSniffed(int id, long now) {
		return SNIFFED.getOrDefault(id, -1L) >= now;
	}

	public static void clear() {
		HUNTERS.clear();
		SNIFFED.clear();
	}
}
