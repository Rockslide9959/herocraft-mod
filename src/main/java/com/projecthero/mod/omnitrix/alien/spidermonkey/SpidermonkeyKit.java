package com.projecthero.mod.omnitrix.alien.spidermonkey;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.2: the Recalibrated Omnitrix's Spidermonkey -- STUB until its kit is built (the alien stays "coming soon" on the dial while
 * {@link #register} registers nothing). Replace this class with the real {@code AlienKit}.
 */
public final class SpidermonkeyKit {
	public static final String ID = Aliens.SPIDERMONKEY.id();

	private SpidermonkeyKit() {
	}

	/** One line in {@code OmnitrixMod.initialize}: {@code Aliens.register(new SpidermonkeyKit())} once the kit exists. */
	public static void register() {
	}

	/** From {@code OmnitrixMod.clearSessionState} (the ServerStateReset hook): clear every static per-player map. */
	public static void clearSessionState() {
	}
}
