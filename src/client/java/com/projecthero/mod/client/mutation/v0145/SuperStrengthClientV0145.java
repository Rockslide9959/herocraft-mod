package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;

/** v0.14.5 Super Strength rework: client registration (HUD theme and bars, poses, overlays, renderers). */
public final class SuperStrengthClientV0145 {
	private SuperStrengthClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(SuperStrengthHandlers.KEY);
	}
}
