package com.projecthero.mod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

/**
 * v0.16.1: pitches the torso about its BOTTOM edge (the hips) instead of vanilla's pivot at its top edge (the neck).
 *
 * <p>Vanilla's {@code body} part hangs from the neck, so setting {@code body.xRot} on its own swings the bottom of the
 * torso -- 12 px below the pivot -- forward (lean back, negative xRot) or backward (lean forward, positive xRot), off
 * the tops of the legs, which stay put: the "torso detaches from the legs" look several suit-up / transformation poses
 * had. Here the hip point is kept where it is and the neck moves instead; the head follows the neck and the arms follow
 * the shoulders (2 px down the leaning torso), so nothing separates. The GeckoLib armour bones and the player's jacket /
 * sleeve layers copy the vanilla parts' positions as well as their rotations, so they follow too.
 *
 * <p>Works from whatever the model already holds (vanilla's crouch included): the current hip point is measured from
 * the current body pivot and pitch, so blending in and out stays seamless. {@code body.z}, {@code head.z} and
 * {@code hat.z} are never reset by vanilla -- {@code HumanoidModelMixin} zeroes them for players every frame before
 * any pose runs.
 */
public final class HipBend {
	/** The torso is 12 px tall; its pivot is the top edge. */
	private static final float TORSO = 12.0f;
	/** The shoulder pivots sit 2 px below the neck. */
	private static final float SHOULDER = 2.0f;

	private HipBend() {
	}

	/** {@code body.xRot = lerp(w, body.xRot, target)}, bent about the hips (see the class doc). */
	public static void pitch(HumanoidModel<?> m, float w, float target) {
		float a0 = m.body.xRot;
		float a1 = Mth.lerp(w, a0, target);
		if (Math.abs(a1 - a0) < 1.0e-5f) {
			return;
		}
		float c0 = Mth.cos(a0);
		float s0 = Mth.sin(a0);
		float c1 = Mth.cos(a1);
		float s1 = Mth.sin(a1);
		// the neck moves so the hip point (pivot + 12 px down the torso) stays where it was
		float dy = TORSO * (c0 - c1);
		float dz = TORSO * (s0 - s1);
		// the shoulders also turn with the torso about the (moved) neck
		float sdy = dy + SHOULDER * (c1 - c0);
		float sdz = dz + SHOULDER * (s1 - s0);
		m.body.xRot = a1;
		m.body.y += dy;
		m.body.z += dz;
		m.head.y += dy;
		m.head.z += dz;
		m.hat.y = m.head.y;
		m.hat.z = m.head.z;
		m.rightArm.y += sdy;
		m.rightArm.z += sdz;
		m.leftArm.y += sdy;
		m.leftArm.z += sdz;
	}
}
