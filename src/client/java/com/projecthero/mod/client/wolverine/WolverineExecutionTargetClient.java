package com.projecthero.mod.client.wolverine;

/**
 * Client-side store of the local Wolverine's current Adamantium Execution lock-on, fed by
 * {@link com.projecthero.mod.network.WolverineExecutionTargetPayload}, read by {@code EntityGlowMixin}.
 * Unlike {@link WolverineSenseClient}'s hunters/sniffed sets, this has no self-expiry -- the server
 * explicitly sends {@code -1} the moment the execution resolves (hit or miss), so the glow always
 * matches the ability's real server-side state rather than guessing a timeout.
 */
public final class WolverineExecutionTargetClient {
	private static int targetId = -1;

	private WolverineExecutionTargetClient() {
	}

	public static void accept(int id) {
		targetId = id;
	}

	public static boolean isTarget(int id) {
		return targetId >= 0 && targetId == id;
	}

	public static void clear() {
		targetId = -1;
	}
}
