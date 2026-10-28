package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;

/**
 * v0.17.1: a first-person arm clip for an alien move: frames {@code {tick, pitch, yaw, roll, x, y, z}} (degrees about the arm's
 * first-person shoulder; pitch negative = the fist rises into view, yaw positive = toward the centre; offsets in blocks, x
 * toward the centre, y up, z negative = into the screen), smoothstepped between and mirrored for the off hand.
 */
public record FirstPersonClip(float[][] frames, boolean twoHanded) {
	public static float[] f(float tick, float pitch, float yaw, float roll, float x, float y, float z) {
		return new float[] { tick, pitch, yaw, roll, x, y, z };
	}

	public static FirstPersonClip of(float[]... frames) {
		return new FirstPersonClip(frames, false);
	}

	public FirstPersonClip both() {
		return new FirstPersonClip(frames, true);
	}

	public float length() {
		return frames[frames.length - 1][0];
	}

	/** Applies the clip at {@code tick} ({@code side} +1 right hand, -1 left). False once it is over. */
	public boolean apply(PoseStack pose, float tick, float side) {
		if (tick < 0 || tick > length()) {
			return false;
		}
		float[] a = frames[0];
		float[] b = frames[frames.length - 1];
		for (int i = 0; i + 1 < frames.length; i++) {
			if (tick >= frames[i][0] && tick <= frames[i + 1][0]) {
				a = frames[i];
				b = frames[i + 1];
				break;
			}
		}
		float span = Math.max(0.001f, b[0] - a[0]);
		float t = Mth.clamp((tick - a[0]) / span, 0f, 1f);
		t = t * t * (3 - 2 * t);
		float pitch = Mth.lerp(t, a[1], b[1]);
		float yaw = Mth.lerp(t, a[2], b[2]);
		float roll = Mth.lerp(t, a[3], b[3]);
		pose.translate(-side * Mth.lerp(t, a[4], b[4]), Mth.lerp(t, a[5], b[5]), Mth.lerp(t, a[6], b[6]));
		pose.translate(side * 0.55, -0.45, -0.7);
		pose.mulPose(Axis.YP.rotationDegrees(-side * yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(side * roll));
		pose.translate(-side * 0.55, 0.45, 0.7);
		return true;
	}
}
