package com.projecthero.mod.miraculous.pegasus;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

/** v0.16.6: Pegasus server entry point (called from {@code MiraculousMod.initialize}): registers the kit and its entities, items, payloads. */
public final class PegasusMod {
	private PegasusMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.KAALKI, new PegasusKit());
	}
}
