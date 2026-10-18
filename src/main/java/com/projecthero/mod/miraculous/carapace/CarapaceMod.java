package com.projecthero.mod.miraculous.carapace;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

/** v0.16.6: Carapace server entry point (called from {@code MiraculousMod.initialize}): registers the kit and its entities, items, payloads. */
public final class CarapaceMod {
	private CarapaceMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.WAYZZ, new CarapaceKit());
	}
}
