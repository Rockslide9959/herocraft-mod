package com.projecthero.mod.omnitrix.alien.echoecho;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.2: the Recalibrated Omnitrix's EchoEcho -- STUB until its kit is built (the alien stays "coming soon" on the dial while
 * {@link #register} registers nothing). Replace this class with the real {@code AlienKit}.
 */
public final class EchoEchoKit {
	public static final String ID = Aliens.ECHO_ECHO.id();

	private EchoEchoKit() {
	}

	/** One line in {@code OmnitrixMod.initialize}: {@code Aliens.register(new EchoEchoKit())} once the kit exists. */
	public static void register() {
	}

	/** From {@code OmnitrixMod.clearSessionState} (the ServerStateReset hook): clear every static per-player map. */
	public static void clearSessionState() {
	}
}
