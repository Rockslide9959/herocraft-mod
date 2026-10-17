package com.projecthero.mod.miraculous.catnoir;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

/** v0.16.6: CatNoir server entry point (called from {@code MiraculousMod.initialize}): registers the kit and its entities, items, payloads. */
public final class CatNoirMod {
	private CatNoirMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.PLAGG, new CatNoirKit());
	}
}
