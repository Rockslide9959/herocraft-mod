package com.projecthero.mod.quirk.engine;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

/** v0.16.7: the Engine Quirk's server init -- registers {@link EngineKit}. */
public final class EngineMod {
	private EngineMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.ENGINE, new EngineKit());
	}
}
