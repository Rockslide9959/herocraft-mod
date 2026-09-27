package com.projecthero.mod.client.squad;

/**
 * Whether the squad locator bar (v0.13.4) is shown. Purely a client-side view preference -- like
 * {@code SymbioteFxClient}'s Predator Vision toggle, nothing is sent to the server and nothing here
 * affects any other player's screen. Toggled from a button on {@code SquadScreen} (the P menu).
 */
public final class SquadLocatorClient {
	private static boolean enabled = true;

	private SquadLocatorClient() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean toggle() {
		enabled = !enabled;
		return enabled;
	}
}
