package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

/** v0.14.5 Super Speed rework: client registration (HUD theme and bars, poses, overlays, renderers). */
public final class SuperSpeedClientV0145 {
	private SuperSpeedClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(SuperSpeedHandlers.KEY);
	}
}
