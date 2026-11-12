package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's elephant (2.6 blocks tall): a big domed head with flapping ears, a long ringed trunk, tusks, column legs and a
 * tufted tail. Moves: stomp (rears and slams the front feet), spray (trunk up and forward), throw / grab (trunk sweep / reach).
 */
public final class ElephantModel {
	private ElephantModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		float ph = s.walkPos * 0.42f;
		float stomp = s.is("stomp") ? s.prog : -1f;

		float rear = 0f;
		if (stomp >= 0f) {
			rear = stomp < 0.5f ? BeastPose.smooth(stomp / 0.5f) : (stomp < 0.62f ? 1f - BeastPose.smooth((stomp - 0.5f) / 0.12f) : 0f);
		}
		float bob = -Math.abs(Mth.sin(ph)) * 1.2f * amt + Mth.sin(t * 0.08f) * 0.3f;
		float pitch = -rear * 0.42f + Mth.sin(ph * 2f) * 0.015f * amt;
		// rear up around the hind hips
		Matrix4f body = Rig.mat(0f, 26f + bob, -11f, Mth.sin(ph) * 0.03f * amt, pitch, Mth.sin(ph) * 0.03f * amt);
		body.translate(0f, 0f, 11f);

		float[][] hips = { { 8.4f, 0f, -11f }, { -8.4f, 0f, -11f }, { 8.8f, 0f, 9f }, { -8.8f, 0f, 9f } };
		float[] phs = { 0f, Mth.PI, Mth.PI * 0.5f, Mth.PI * 1.5f };
		for (int i = 0; i < 4; i++) {
			boolean front = hips[i][2] > 0;
			float[] st = Rig.step(ph + phs[i], 6f * amt, 4f * amt);
			float[] hip = Rig.tp(body, hips[i][0], hips[i][1], hips[i][2]);
			float[] foot = v(hips[i][0], 2.4f + st[1], hips[i][2] + st[0]);
			if (front && rear > 0f) {
				foot = v(hips[i][0], hip[1] - 22f, hip[2] + 4f);
			}
			float[] knee = ik(hip, foot, 12.4f, 12f, v(0f, 0f, front ? 1f : -1f));
			Rig.limb(pose, hip, knee, foot, 6.2f, 4.8f, 4.6f, WRINKLE, v(0f, 0f, 1f));
			taper(pose, foot[0], foot[1] - 2.4f, foot[2], 5.0f, 5.0f, 3f, 4.6f, 4.6f, 14, WRINKLE_V, true);
			for (int k = -1; k <= 1; k++) {
				ellipsoid(pose, foot[0] + k * 2.2f, foot[1] - 1.4f, foot[2] + 4.4f - Math.abs(k) * 0.8f, 1.2f, 1f, 0.8f, 8, 5, HORN, 0f, 1f);
			}
		}

		Rig.push(pose, body);
		float br = 1f + Mth.sin(t * 0.08f) * 0.02f;
		float[][] spine = { { 0f, 0f, -19f }, { 0f, 2f, -12f }, { 0f, 3f, -1f }, { 0f, 3.2f, 8f }, { 0f, 2f, 14f }, { 0f, 1f, 17f } };
		float[] w = { 6f, 12.5f, 14f * br, 13.6f * br, 11f, 8f };
		float[] h = { 6f, 12.5f, 13.8f * br, 13.6f * br, 11f, 9f };
		loft(pose, spine, w, h, 3, 18, WRINKLE, 5, null);
		ellipsoid(pose, 0f, 12f, 4f, 9f, 5f, 9f, WRINKLE_V);
		// tail with a tuft
		float tw = Mth.sin(t * 0.18f) * 0.5f;
		float[][] tail = walk(v(0f, 4f, -19.5f), Mth.PI + tw * 0.2f, 1.2f, 3f, new float[] { tw * 0.15f, tw * 0.2f, tw * 0.2f }, new float[] { 0.08f, 0.05f, 0f });
		loft(pose, tail, new float[] { 1.0f, 0.8f, 0.6f, 0.5f }, new float[] { 1.0f, 0.8f, 0.6f, 0.5f }, 2, 8, SKIN_DK, 0, null);
		ellipsoid(pose, tail[3][0], tail[3][1] - 0.8f, tail[3][2], 0.9f, 1.8f, 0.9f, 8, 6, DARK, 0f, 1f);

		float hy = s.yaw(0.5f) * 0.6f;
		float hp = s.pitch(0.5f) * 0.3f - pitch * 0.4f - rear * 0.2f;
		boneYXZ(pose, 0f, 3f, 16f, hy, hp, 0f);
		float trunkUp = Math.max(rear, s.env("spray", 0.2f, 0.25f));
		float grab = s.isAny("grab", "throw") ? s.env(s.move, 0.3f, 0.3f) : 0f;
		float swing = s.is("throw") ? Mth.sin(s.prog * Mth.TWO_PI) : 0f;
		head(pose, t, amt, trunkUp, grab, swing, rear);
		pose.popPose();
		pose.popPose();
	}

	/** The elephant head with ears, tusks and trunk (pivot at the neck, +Z forward). */
	public static void head(PoseStack pose, float t, float amt, float trunkUp, float grab, float swing, float rear) {
		ellipsoid(pose, 0f, 3f, 4f, 8.2f, 9f, 7.6f, WRINKLE_V);
		ellipsoid(pose, 0f, 7.5f, 4f, 6.6f, 5f, 6f, WRINKLE_V);
		ellipsoid(pose, 0f, -2f, 8.5f, 5.6f, 6f, 4.4f, WRINKLE_V);
		for (int s = -1; s <= 1; s += 2) {
			eye(pose, s * 6.2f, 3.4f, 8.2f, 1.05f, s * 0.95f, 0.05f, EYE);
			ellipsoid(pose, s * 6.0f, 4.5f, 8.4f, 1.5f, 0.6f, 1.4f, 8, 5, WRINKLE_V, 0f, 1f);
			// big ears flapping
			float flap = Mth.sin(t * 0.12f + s * 0.4f) * 0.18f + amt * Mth.sin(t * 0.3f) * 0.1f + rear * 0.5f;
			ear(pose, s, flap, 1f);
			// tusks
			horn(pose, new float[][] { { s * 3.4f, -4.5f, 9.6f }, { s * 3.9f, -8f, 12f }, { s * 3.6f, -9.4f, 15.6f }, { s * 2.8f, -8.6f, 18.4f } }, 1.3f, HORN);
		}
		// mouth under the trunk
		ellipsoid(pose, 0f, -6.8f, 8.6f, 2.8f, 1.6f, 2.6f, MOUTH);
		ellipsoid(pose, 0f, -7.8f, 8.2f, 3f, 1.4f, 2.8f, WRINKLE_V);
		trunk(pose, t, amt, trunkUp, grab, swing);
	}

	/**
	 * One big ear hinged at the side of the head (s = +1 left), swung out to the side and back; flap opens it wider. The inner face
	 * (pale) looks forward.
	 */
	public static void ear(PoseStack pose, int s, float flap, float k) {
		boneYXZ(pose, s * 6.2f * k, 4f * k, 3.5f * k, -s * (1.05f - flap), 0f, s * 0.12f);
		pose.scale(k, k, k);
		ellipsoid(pose, 0f, -2f, -7.8f, 0.9f, 11f, 8.4f, 14, 9, WRINKLE_V, 0f, 1f);
		ellipsoid(pose, s * 0.55f, -1.9f, -7.6f, 0.45f, 9.6f, 7.2f, 14, 9, INNER_EAR, 0f, 1f);
		pose.popPose();
	}

	/** The trunk from its root on the face. trunkUp raises and curls it up (trumpet / spray), grab reaches it forward. */
	public static void trunk(PoseStack pose, float t, float amt, float trunkUp, float grab, float swing) {
		int n = 8;
		float[] yb = new float[n], pb = new float[n];
		for (int i = 0; i < n; i++) {
			float sway = Mth.sin(t * 0.1f - i * 0.5f) * 0.05f + Mth.sin(t * 0.33f) * 0.03f * amt + swing * 0.12f;
			yb[i] = sway;
			// hanging: bends from forward to down, tip curls forward a little
			float hang = i < 2 ? 0.25f : (i > 5 ? -0.22f : 0.06f);
			float up = i < 3 ? -0.5f : (i > 5 ? -0.5f : -0.3f);
			float reach = i < 2 ? -0.3f : (i > 5 ? 0.05f : -0.05f);
			pb[i] = hang + trunkUp * (up - hang) + grab * (reach - hang) * (1f - trunkUp);
		}
		float[][] pts = walk(v(0f, -2.5f, 11.5f), 0f, 0.6f + -trunkUp * 0.6f, 3.2f, yb, pb);
		float[] w = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			w[i] = Mth.lerp(i / (float) n, 3.6f, 1.4f);
		}
		loft(pose, pts, w, w, 3, 12, WRINKLE, 2, v(0f, 0f, 1f));
		float[] tip = pts[n];
		ellipsoid(pose, tip[0], tip[1], tip[2], 1.6f, 1.6f, 1.6f, 10, 6, WRINKLE_LT, 0f, 1f);
	}
}
