package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;

/** v0.14.5 Super Regeneration rework: client registration (HUD theme and bars, poses, overlays, renderers). */
public final class SuperRegenerationClientV0145 {
	private SuperRegenerationClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(SuperRegenerationHandlers.KEY);
	}
}
