package com.projecthero.mod.miraculous.ladybug;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

/** v0.16.6: Ladybug server entry point (called from {@code MiraculousMod.initialize}): registers the kit and its entities, items, payloads. */
public final class LadybugMod {
	private LadybugMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.TIKKI, new LadybugKit());
	}
}
