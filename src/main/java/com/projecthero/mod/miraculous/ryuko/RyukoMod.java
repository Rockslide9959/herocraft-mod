package com.projecthero.mod.miraculous.ryuko;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

/** v0.17.3: Ryuko's server entry point (called from {@code MiraculousMod.initialize}). */
public final class RyukoMod {
	private RyukoMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.LONGG, new RyukoKit());
	}
}
