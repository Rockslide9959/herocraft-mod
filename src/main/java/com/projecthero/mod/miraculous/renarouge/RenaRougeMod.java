package com.projecthero.mod.miraculous.renarouge;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

/** v0.16.6: RenaRouge server entry point (called from {@code MiraculousMod.initialize}): registers the kit and its entities, items, payloads. */
public final class RenaRougeMod {
	private RenaRougeMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.TRIXX, new RenaRougeKit());
	}
}
