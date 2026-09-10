package com.projecthero.mod.client.squad;

import com.projecthero.mod.network.SquadInfoPayload;

/**
 * The client's copy of its own squad roster, refreshed by {@link SquadInfoPayload} a few times a second
 * while the player is in a squad. The squad screen reads this rather than the client world, because a
 * squadmate in another dimension, a far-off chunk, or simply offline is not a client-side entity at all
 * and there is nothing to read from.
 */
public final class SquadClient {
	private static volatile SquadInfoPayload latest = SquadInfoPayload.none();

	private SquadClient() {
	}

	public static void accept(SquadInfoPayload payload) {
		latest = payload;
	}

	public static SquadInfoPayload get() {
		return latest;
	}

	public static boolean inSquad() {
		return !latest.squadName().isEmpty();
	}

	/** Wipe on disconnect so a fresh world never opens showing the last server's squad. */
	public static void clear() {
		latest = SquadInfoPayload.none();
	}
}
