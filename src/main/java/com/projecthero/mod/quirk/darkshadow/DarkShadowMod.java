package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

/** v0.16.7: the Dark Shadow Quirk's server init -- registers {@link DarkShadowKit}. */
public final class DarkShadowMod {
	private DarkShadowMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.DARK_SHADOW, new DarkShadowKit());
	}
}
