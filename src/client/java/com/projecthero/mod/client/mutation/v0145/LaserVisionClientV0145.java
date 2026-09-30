package com.projecthero.mod.client.mutation.v0145;

import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.client.mutation.MutationPose;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;

/** v0.14.5 Laser Vision rework: client registration (HUD theme and bars, poses, overlays, renderers). */
public final class LaserVisionClientV0145 {
	private LaserVisionClientV0145() {
	}

	public static void init() {
		// black-and-gray keys / name; the red Heat and Maximum Output Hairlines are registered meters (LaserVisionV0145)
		AbilityHudExtras.mono(LaserVisionHandlers.KEY);
		registerPoses();
		LaserBeamRenderer.init();
	}

	/** The beam moves' own animation names (the beam renderer keys off them) reuse the existing poses. */
	private static void registerPoses() {
		copyLoop("beam_eyes", LaserVisionHandlers.ANIM_BEAM);
		copyLoop("beam_eyes", LaserVisionHandlers.ANIM_MAX);
		copyLoop("crouch_charge", LaserVisionHandlers.ANIM_MAX_CHARGE);
		copyOneShot("p02.glare", LaserVisionHandlers.ANIM_PIERCE);
		copyOneShot("p02.glare", LaserVisionHandlers.ANIM_IGNITE);
	}

	private static void copyLoop(String from, String to) {
		MutationPose.Def d = MutationPose.get(from);
		if (d != null) {
			MutationPose.registerLoop(to, d.loops() ? d.loopFrom() : 0, d.frames());
		}
	}

	private static void copyOneShot(String from, String to) {
		MutationPose.Def d = MutationPose.get(from);
		if (d != null) {
			MutationPose.register(to, d.frames());
		}
	}
}
