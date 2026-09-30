package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;

/** v0.14.5 Laser Vision rework: client registration (HUD theme and bars, poses, overlays, renderers). */
public final class LaserVisionClientV0145 {
	private LaserVisionClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(LaserVisionHandlers.KEY);
	}
}
