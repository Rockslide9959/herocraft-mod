package com.projecthero.mod.miraculous.hawkmoth;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

/** v0.16.6: HawkMoth server entry point (called from {@code MiraculousMod.initialize}): registers the kit and its entities, items, payloads. */
public final class HawkMothMod {
	private HawkMothMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.NOOROO, new HawkMothKit());
	}
}
