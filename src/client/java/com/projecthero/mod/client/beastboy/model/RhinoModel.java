package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's rhino (1.5 blocks tall, 2.6 long): a heavy barrel body with skin folds at the neck, shoulders and hips, short
 * pillar legs, a low head with the big front horn and a small second horn, tube ears, little tail. Moves: charge / ram, stomp.
 */
public final class RhinoModel {
	private RhinoModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float charge = s.isAny("charge", "ram") ? s.env(s.move, 0.12f, 0.15f) : 0f;
		float amt = Math.max(s.walkAmt, charge);
		float ph = s.walkPos * 0.6f + (charge > 0 ? t * 0.7f : 0f);
		float stomp = s.is("stomp") ? s.prog : -1f;

		float bob = -Math.abs(Mth.sin(ph)) * 1.0f * amt + Mth.sin(t * 0.1f) * 0.2f;
		float pitch = charge * 0.08f + Mth.sin(ph * 2f) * 0.02f * amt;
		if (stomp >= 0f) {
			pitch = stomp < 0.55f ? -BeastPose.smooth(stomp / 0.55f) * 0.32f : -0.32f * Math.max(0f, 1f - (stomp - 0.55f) / 0.1f);
		}
		Matrix4f body = Rig.mat(0f, 15f + bob, 0f, 0f, pitch, Mth.sin(ph) * 0.03f * amt);

		// ---- legs: pillar legs on IK
		float[][] hips = { { 6.2f, 0f, -11f }, { -6.2f, 0f, -11f }, { 6.6f, 0f, 9f }, { -6.6f, 0f, 9f } };
		float[] phs = { 0f, Mth.PI, Mth.PI * 0.5f, Mth.PI * 1.5f };
		for (int i = 0; i < 4; i++) {
			float side = Math.signum(hips[i][0]);
			boolean front = hips[i][2] > 0;
			float[] st = Rig.step(ph + phs[i], 4.5f * amt, 3f * amt);
			float[] hip = Rig.tp(body, hips[i][0], hips[i][1], hips[i][2]);
			float[] foot = v(hips[i][0] * 1.02f, 1.5f + st[1], hips[i][2] + st[0]);
			if (stomp >= 0f && front) {
				foot[1] = Math.max(foot[1], hip[1] - 13f);
			}
			float[] knee = ik(hip, foot, 7.4f, 7.2f, v(0f, 0f, front ? -1f : 1f));
			Rig.limb(pose, hip, knee, foot, 4.6f, 3.4f, 3.2f, WRINKLE, null);
			// round foot with three toenails
			taper(pose, foot[0], foot[1] - 1.5f, foot[2], 3.4f, 3.4f, 2.2f, 3.1f, 3.1f, 12, WRINKLE_V, true);
			for (int k = -1; k <= 1; k++) {
				ellipsoid(pose, foot[0] + k * 1.6f, foot[1] - 0.7f, foot[2] + 2.8f - Math.abs(k) * 0.6f, 0.95f, 0.8f, 0.7f, 8, 5, HORN, 0f, 1f);
			}
		}

		// ---- body
		Rig.push(pose, body);
		float br = 1f + Mth.sin(t * 0.09f) * 0.025f;
		float[][] spine = { { 0f, 2f, -19f }, { 0f, 1.5f, -12f }, { 0f, 1.6f, -2f }, { 0f, 2.6f, 7f }, { 0f, 1.4f, 13f }, { 0f, -0.6f, 17f } };
		float[] w = { 4f, 9f, 10.4f * br, 10.2f * br, 8f, 6f };
		float[] h = { 4.5f, 9f, 9.6f * br, 10.2f * br, 8.4f, 6.4f };
		loft(pose, spine, w, h, 3, 16, WRINKLE, 5, null);
		// skin folds: shoulder plate edge, hip plate edge, neck folds
		for (float[] f : new float[][] { { 4f, 10.7f, 10.5f }, { -9f, 9.6f, 9.7f }, { 13f, 8.6f, 8.8f } }) {
			pose.pushPose();
			pose.translate(0f, spine[3][1] - (f[0] > 10f ? 1.2f : 0.6f), f[0]);
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
			taper(pose, 0f, -0.8f, 0f, f[1], f[2], 1.6f, f[1] * 0.98f, f[2] * 0.98f, 18, WRINKLE_V, false);
			pose.popPose();
		}
		// shoulder hump
		ellipsoid(pose, 0f, 8.5f, 6f, 6f, 4f, 6f, WRINKLE_V);
		// tail
		float tw = Mth.sin(t * 0.25f) * 0.4f;
		float[][] tail = walk(v(0f, 3f, -19.5f), Mth.PI + tw * 0.3f, 0.9f, 2f, new float[] { tw * 0.2f, tw * 0.3f, 0f }, new float[] { 0.1f, 0.1f, 0f });
		loft(pose, tail, new float[] { 0.9f, 0.7f, 0.5f, 0.4f }, new float[] { 0.9f, 0.7f, 0.5f, 0.4f }, 2, 8, SKIN_DK, 0, null);
		ellipsoid(pose, tail[3][0], tail[3][1], tail[3][2], 0.8f, 1.4f, 0.8f, 8, 6, DARK, 0f, 1f);

		// head: low and forward
		float hy = s.yaw(0.5f) * 0.6f;
		float hp = 0.35f + s.pitch(0.5f) * 0.3f + charge * 0.3f - pitch * 0.5f + Mth.sin(ph * 2f) * 0.04f * amt;
		boneYXZ(pose, 0f, 0.5f, 16.5f, hy, hp, 0f);
		head(pose, t);
		pose.popPose();
		pose.popPose();
	}

	/** The rhino head (pivot at the neck, +Z forward, level). Also used by the quick-morph ram. */
	public static void head(PoseStack pose, float t) {
		ellipsoid(pose, 0f, 0.5f, 3.5f, 5.4f, 5.6f, 6.5f, WRINKLE_V);
		ellipsoid(pose, 0f, -1.4f, 9.5f, 4.4f, 4.6f, 5.2f, WRINKLE_V);
		ellipsoid(pose, 0f, -3f, 13f, 3.6f, 3.2f, 2.8f, WRINKLE_LT);
		// lips and nostrils
		ellipsoid(pose, 0f, -5f, 12.4f, 3.2f, 1.3f, 2.4f, WRINKLE_LT);
		ellipsoid(pose, 0f, -4.4f, 12.8f, 2.8f, 0.4f, 2.2f, 10, 4, DARK, 0.4f, 0.6f);
		for (int s = -1; s <= 1; s += 2) {
			ellipsoid(pose, s * 1.6f, -1.6f, 15.4f, 0.75f, 0.55f, 0.5f, 8, 5, DARK, 0f, 1f);
			eye(pose, s * 4.3f, 1.3f, 7.2f, 0.85f, s * 1.15f, 0f, EYE);
			ellipsoid(pose, s * 4.2f, 2.0f, 7.4f, 1.2f, 0.6f, 1.4f, 8, 5, WRINKLE_V, 0f, 1f);
			// tube ears, flicking
			float flick = Mth.sin(t * 0.21f + s) > 0.93f ? 0.4f : 0f;
			float[][] ear = { { s * 3.2f, 4.5f, 1.5f }, { s * 4.4f, 7.4f, 0.4f - flick }, { s * 5.0f, 9.6f, -0.4f - flick * 2f } };
			loft(pose, ear, new float[] { 1.2f, 1.4f, 0.4f }, new float[] { 1.0f, 1.3f, 0.3f }, 3, 10, WRINKLE, 0, v(0f, 0f, 1f));
			ellipsoid(pose, s * 4.5f, 7.8f, 0.9f, 0.6f, 1.2f, 0.25f, 8, 6, INNER_EAR, 0f, 1f);
		}
		// the big front horn and the small second horn
		horn(pose, new float[][] { { 0f, 0.8f, 12.2f }, { 0f, 4.5f, 14.4f }, { 0f, 8.6f, 15.2f }, { 0f, 11.6f, 14.2f } }, 2.6f, HORN);
		horn(pose, new float[][] { { 0f, 3.6f, 8.4f }, { 0f, 5.6f, 9.3f }, { 0f, 7.2f, 9.2f } }, 1.6f, HORN);
	}
}
