package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's rhino (1.5 blocks tall, 2.6 long). Fix round rework: a heavy countershaded barrel under two armour-plate
 * shells (shoulder and hip, with dark fold lips), neck folds, a raised shoulder hump, thick pillar legs with elbow folds and
 * three-toed feet, a big head carried higher (so the horns and ears show from behind) with a long front horn on a boss, a second
 * horn, square lips, brow folds, upright tube ears, and a tufted tail. Moves: charge / ram, stomp.
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

		// ---- legs: thick pillars on IK
		float[][] hips = { { 6.4f, 0f, -11f }, { -6.4f, 0f, -11f }, { 6.8f, 0f, 9f }, { -6.8f, 0f, 9f } };
		float[] phs = { 0f, Mth.PI, Mth.PI * 0.5f, Mth.PI * 1.5f };
		for (int i = 0; i < 4; i++) {
			float side = Math.signum(hips[i][0]);
			boolean front = hips[i][2] > 0;
			float[] st = Rig.step(ph + phs[i], 4.5f * amt, 3f * amt);
			float[] hip = Rig.tp(body, hips[i][0], hips[i][1], hips[i][2]);
			float[] foot = v(hips[i][0] * 1.02f, 1.6f + st[1], hips[i][2] + st[0]);
			if (stomp >= 0f && front) {
				foot[1] = Math.max(foot[1], hip[1] - 13f);
			}
			float[] knee = ik(hip, foot, 7.4f, 7.2f, v(0f, 0f, front ? -1f : 1f));
			// the upper leg bulges into the body (shoulder / thigh mass)
			float[] up = lerp(hip, knee, 0.35f);
			ellipsoid(pose, up[0] + side * 0.4f, up[1] + 1f, up[2], 4.6f, 6f, 5.4f, WRINKLE_V);
			Rig.limb(pose, hip, knee, foot, 5.4f, 4.3f, 3.9f, WRINKLE, v(side, 0.4f, 0f));
			// fold rings at the knee and just above the foot
			ellipsoid(pose, knee[0], knee[1], knee[2], 4.6f, 1.5f, 4.6f, 14, 6, SKIN_DK, 0f, 1f);
			ellipsoid(pose, foot[0], foot[1] + 2.4f, foot[2], 4.2f, 1.1f, 4.2f, 14, 6, WRINKLE_V, 0f, 1f);
			// broad round foot with three toenails
			taper(pose, foot[0], foot[1] - 1.6f, foot[2], 4.1f, 4.1f, 2.4f, 3.8f, 3.8f, 14, WRINKLE_V, true);
			for (int k = -1; k <= 1; k++) {
				ellipsoid(pose, foot[0] + k * 2.0f, foot[1] - 0.8f, foot[2] + 3.4f - Math.abs(k) * 0.8f, 1.15f, 0.95f, 0.85f, 8, 5, HORN, 0f, 1f);
			}
		}

		// ---- body
		Rig.push(pose, body);
		float br = 1f + Mth.sin(t * 0.09f) * 0.025f;
		float[][] spine = { { 0f, 2f, -19f }, { 0f, 1.5f, -12f }, { 0f, 1.4f, -2f }, { 0f, 2.6f, 7f }, { 0f, 1.6f, 13f }, { 0f, 0.6f, 17f } };
		float[] w = { 4.5f, 9.4f, 10.6f * br, 10.4f * br, 8.2f, 6.4f };
		float[] h = { 5f, 9.2f, 9.6f * br, 10.4f * br, 8.6f, 6.8f };
		loft(pose, spine, w, h, 3, 18, WRINKLE, 0, null);
		// the armour plates: a shoulder shell and a hip shell, slightly proud of the body, with dark lips at their edges
		ellipsoid(pose, 0f, 4.4f, 6.8f, 11.3f * br, 9.6f, 8.6f, 18, 10, PLATE_V, 0f, 0.6f);
		ellipsoid(pose, 0f, 3.6f, -11.2f, 10.6f * br, 9.0f, 9.0f, 18, 10, PLATE_V, 0f, 0.6f);
		// the rump rounds off behind the hip shell
		ellipsoid(pose, 0f, 2.6f, -15.5f, 7.6f, 8.0f, 5.4f, WRINKLE_V);
		// shoulder hump
		ellipsoid(pose, 0f, 9.4f, 6.4f, 6.2f, 4.2f, 6.2f, PLATE_V);
		// belly sag
		ellipsoid(pose, 0f, -4.6f, -1.5f, 8.2f, 4.6f, 11f, WRINKLE_V);
		// neck folds: two thick collars between the shoulder shell and the head
		for (int k = 0; k < 2; k++) {
			float z = 13.4f + k * 2.2f;
			ellipsoidR(pose, 0f, 1.8f - k * 0.6f, z, 7.6f - k * 0.9f, 7.8f - k * 0.9f, 1.4f, 0f, 0.15f, 0f, k == 0 ? SKIN_DK : WRINKLE_V);
		}
		// tail: hangs from the rump, a slow swish, a dark tuft
		float tw = Mth.sin(t * 0.13f) * 0.3f + Mth.sin(ph) * 0.15f * amt;
		float[][] tail = walk(v(0f, 5.5f, -19.6f), Mth.PI + tw * 0.25f, 1.15f, 1.9f, new float[] { tw * 0.15f, tw * 0.2f, tw * 0.2f, 0f },
				new float[] { 0.08f, 0.06f, 0.04f, 0f });
		loft(pose, tail, new float[] { 1.3f, 1.0f, 0.8f, 0.65f, 0.55f }, new float[] { 1.3f, 1.0f, 0.8f, 0.65f, 0.55f }, 2, 8, SKIN_DK, 0, null);
		float[] tip = tail[4];
		ellipsoid(pose, tip[0], tip[1] - 0.6f, tip[2], 1.0f, 1.8f, 0.9f, 8, 6, DARK, 0f, 1f);
		for (int k = -1; k <= 1; k++) {
			cone(pose, tip[0] + k * 0.4f, tip[1] - 1.6f, tip[2], k * 0.3f, -1f, -0.1f, 1.4f, 0.4f, 0.35f, DARK);
		}

		// head: carried forward and up enough that the horns and ears clear the shoulders
		float hy = s.yaw(0.5f) * 0.6f;
		float hp = 0.22f + s.pitch(0.5f) * 0.3f + charge * 0.35f - pitch * 0.5f + Mth.sin(ph * 2f) * 0.04f * amt;
		boneYXZ(pose, 0f, 3.0f, 16.8f, hy, hp, 0f);
		head(pose, t);
		pose.popPose();
		pose.popPose();
	}

	/** The rhino head (pivot at the neck, +Z forward, level). Also used by the quick-morph ram. */
	public static void head(PoseStack pose, float t) {
		// skull and the long face, wider at the cheeks
		ellipsoid(pose, 0f, 0.8f, 3.4f, 5.8f, 6.0f, 6.6f, WRINKLE_V);
		ellipsoid(pose, 0f, -1.2f, 9.2f, 4.8f, 5.0f, 5.8f, WRINKLE_V);
		for (int s = -1; s <= 1; s += 2) {
			ellipsoid(pose, s * 3.4f, -2.4f, 7.0f, 2.4f, 3.4f, 4.2f, WRINKLE_V);
		}
		// the horn boss on the nose and the square muzzle with broad lips
		ellipsoid(pose, 0f, 1.6f, 12.4f, 3.4f, 2.4f, 3.0f, WRINKLE_LT);
		ellipsoid(pose, 0f, -3.0f, 13.2f, 4.0f, 3.4f, 3.0f, WRINKLE_LT);
		ellipsoid(pose, 0f, -5.3f, 12.6f, 3.9f, 1.4f, 2.8f, WRINKLE_LT);
		ellipsoid(pose, 0f, -4.6f, 13.1f, 3.5f, 0.45f, 2.6f, 10, 4, DARK, 0.4f, 0.6f);
		ellipsoid(pose, 0f, -6.4f, 11.8f, 3.0f, 1.0f, 2.2f, WRINKLE_V);
		for (int s = -1; s <= 1; s += 2) {
			// flared nostrils
			ellipsoid(pose, s * 1.9f, -1.8f, 15.6f, 0.9f, 0.7f, 0.6f, 8, 5, NOSE, 0f, 1f);
			ellipsoid(pose, s * 2.2f, -1.4f, 15.0f, 1.4f, 1.1f, 1.0f, 8, 5, WRINKLE_LT, 0f, 1f);
			// small eye under a heavy brow fold, a crease below
			eye(pose, s * 4.6f, 1.4f, 7.4f, 0.95f, s * 1.15f, 0f, EYE);
			ellipsoidR(pose, s * 4.4f, 2.4f, 7.5f, 1.5f, 0.75f, 1.9f, s * 0.4f, 0f, 0f, SKIN_DK);
			ellipsoidR(pose, s * 4.5f, 0.1f, 7.8f, 1.2f, 0.4f, 1.6f, s * 0.4f, 0f, 0f, SKIN_DK);
			// cheek folds
			for (int k = 0; k < 3; k++) {
				ellipsoidR(pose, s * (4.6f - k * 0.3f), -1.6f - k * 1.3f, 5.2f + k * 0.8f, 0.5f, 0.35f, 2.2f, s * 0.25f, 0.1f, 0f, SKIN_DK);
			}
			// upright tube ears on top of the head, flicking now and then
			float flick = Mth.sin(t * 0.21f + s) > 0.93f ? 0.45f : 0f;
			float[][] ear = { { s * 3.4f, 5.6f, 1.6f }, { s * 4.4f, 8.8f, 0.8f - flick }, { s * 5.0f, 11.6f, 0.2f - flick * 2f } };
			loft(pose, ear, new float[] { 1.5f, 1.7f, 0.5f }, new float[] { 1.3f, 1.6f, 0.4f }, 3, 10, WRINKLE, 0, v(0f, 0f, 1f));
			ellipsoid(pose, s * 4.5f, 9.2f, 1.6f - flick, 0.7f, 1.6f, 0.3f, 8, 6, INNER_EAR, 0f, 1f);
		}
		// the big front horn (curving up and back) and the second horn behind it
		horn(pose, new float[][] { { 0f, 2.4f, 12.8f }, { 0f, 5.4f, 14.4f }, { 0f, 8.6f, 14.8f }, { 0f, 11.2f, 13.6f }, { 0f, 12.6f, 12.2f } }, 2.9f, HORN);
		horn(pose, new float[][] { { 0f, 4.4f, 8.6f }, { 0f, 6.8f, 9.4f }, { 0f, 8.8f, 9.0f }, { 0f, 9.9f, 8.0f } }, 2.0f, HORN);
	}
}
