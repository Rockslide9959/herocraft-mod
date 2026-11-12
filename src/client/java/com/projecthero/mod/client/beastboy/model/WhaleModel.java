package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's whale (a humpback, about 7 blocks long): grooved pale throat, knobbly head, long white-edged flippers, small
 * dorsal hump, broad flukes. Swims with an up-and-down fluke wave. Moves: slam (tail rises high and slams down), charge.
 */
public final class WhaleModel {
	private WhaleModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		boolean wet = s.inWater;
		float speed = wet ? 0.07f + amt * 0.08f : 0.05f;
		float amp = wet ? 1.2f + amt * 1.6f : 0.6f;
		float phase = t * speed + s.walkPos * 0.12f;

		// slam: tail rises (body tips nose-down) then whips down
		float tailLift = 0f;
		if (s.is("slam")) {
			float p = s.prog;
			tailLift = p < 0.5f ? BeastPose.smooth(p / 0.5f) : (p < 0.62f ? 1f - BeastPose.smooth((p - 0.5f) / 0.12f) * 1.5f : -0.5f * (1f - BeastPose.smooth((p - 0.62f) / 0.38f)));
		}
		float bodyPitch = tailLift * 0.18f + s.pitch(0.4f) * (wet ? 0.3f : 0f);

		pose.pushPose();
		pose.translate(0f, 17f + (wet ? Mth.sin(phase + 0.8f) * 0.8f : 0f), 0f);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(bodyPitch));
		pose.mulPose(com.mojang.math.Axis.YP.rotation(s.yaw(0.5f) * 0.15f));

		float[] zs = { 52f, 44f, 30f, 12f, -8f, -26f, -42f, -54f };
		float[] w = { 4.6f, 10f, 15f, 17.6f, 17f, 12f, 6f, 2.8f };
		float[] h = { 5.5f, 11f, 15f, 17f, 16.5f, 12f, 6.2f, 3f };
		float[] ys = { -4f, -3f, -1f, 0f, 0.5f, 1f, 1f, 0.6f };
		float[][] sp = new float[zs.length][];
		for (int i = 0; i < zs.length; i++) {
			float k = i / (float) (zs.length - 1);
			float wave = Mth.sin(phase - k * 2.6f) * amp * (k * k * 1.6f);
			float lift = tailLift * k * k * k * 26f;
			sp[i] = v(0f, ys[i] + wave + lift, zs[i] - Math.abs(lift) * k * 0.25f);
		}
		loft(pose, sp, w, h, 3, 20, GROOVE, 0, null);
		// head knobs (tubercles) and the blowhole
		for (int i = 0; i < 9; i++) {
			float z = 30f + i * 2.6f;
			for (int sd = -1; sd <= 1; sd += 2) {
				float x = sd * (2f + (i % 3) * 1.8f);
				float top = topAt(sp, h, z);
				ellipsoid(pose, x, top - 0.6f + (i % 2) * 0.2f, z, 0.9f, 0.9f, 0.9f, 8, 5, SKIN_DK, 0f, 1f);
			}
		}
		ellipsoid(pose, 0f, topAt(sp, h, 26f) + 0.1f, 26f, 1.2f, 0.5f, 0.7f, 8, 5, DARK, 0f, 1f);
		// mouth line and eyes
		float[][] lipL = new float[6][], lipR = new float[6][];
		for (int i = 0; i < 6; i++) {
			float z = 52f - i * 4.6f;
			float wz = widthAt(sp, w, z);
			float y = yAt(sp, z) - 4f - i * 0.5f;
			lipL[i] = v(wz * 0.84f + 0.35f, y, z);
			lipR[i] = v(-wz * 0.84f - 0.35f, y, z);
		}
		float[] lw = { 0.4f, 0.45f, 0.45f, 0.45f, 0.4f, 0.3f };
		loft(pose, lipL, lw, lw, 2, 6, DARK, 0, null);
		loft(pose, lipR, lw, lw, 2, 6, DARK, 0, null);
		for (int sd = -1; sd <= 1; sd += 2) {
			float z = 27f;
			eye(pose, sd * (widthAt(sp, w, z) * 0.9f), yAt(sp, z) - 4.8f, z, 1.1f, sd * 1.3f, 0f, EYE);
		}
		// dorsal hump
		float dz = -24f;
		float dt = topAt(sp, h, dz);
		loft(pose, new float[][] { { 0f, dt - 1f, dz + 3f }, { 0f, dt + 1.6f, dz }, { 0f, dt + 2.6f, dz - 2.6f } }, new float[] { 1.2f, 0.8f, 0.2f },
				new float[] { 4f, 2.4f, 0.4f }, 3, 8, SKIN_DK, 0, v(0f, 0f, 1f));
		// long flippers, slow beat
		float fl = Mth.sin(phase * 0.8f) * (wet ? 0.18f : 0.05f) + (s.is("slam") ? -0.2f : 0f);
		for (int sd = -1; sd <= 1; sd += 2) {
			float[][] fp = new float[5][];
			for (int i = 0; i < 5; i++) {
				float k = i / 4f;
				float dx = 13f + k * 26f;
				fp[i] = v(sd * dx * Mth.cos(fl), -7f - k * 6f + sd * 0f + dx * Mth.sin(fl) * 0.6f - (i * i) * 0.2f, 22f - k * 16f);
			}
			loft(pose, fp, new float[] { 6f, 5.2f, 4.2f, 3f, 0.8f }, new float[] { 1.6f, 1.3f, 1.0f, 0.7f, 0.3f }, 3, 10, FIN, 0, v(0f, 1f, 0f));
			// knobs along the leading edge
			for (int i = 1; i < 4; i++) {
				float[] q = fp[i];
				ellipsoid(pose, q[0], q[1], q[2] + (5.2f - i * 1.0f) * 0.85f, 0.9f, 0.7f, 0.9f, 8, 5, BELLY, 0f, 1f);
			}
		}
		// flukes
		float[] tail = sp[sp.length - 1];
		float[] prev = sp[sp.length - 2];
		float fp = (float) Math.atan2(prev[1] - tail[1], prev[2] - tail[2]) * 0.8f;
		pose.pushPose();
		pose.translate(tail[0], tail[1], tail[2]);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(-fp));
		for (int sd = -1; sd <= 1; sd += 2) {
			loft(pose, new float[][] { { 0f, 0f, 1f }, { sd * 6f, 0f, -2.6f }, { sd * 12f, 0f, -5.2f }, { sd * 17.5f, 0f, -9f } }, new float[] { 4.4f, 4.2f, 3.4f, 0.6f },
					new float[] { 1.4f, 1.0f, 0.7f, 0.2f }, 3, 8, FIN, 0, v(0f, 1f, 0f));
		}
		pose.popPose();
		pose.popPose();
	}

	private static int seg(float[][] sp, float z) {
		for (int i = 0; i < sp.length - 1; i++) {
			if (z <= sp[i][2] && z >= sp[i + 1][2]) {
				return i;
			}
		}
		return z > sp[0][2] ? 0 : sp.length - 2;
	}

	private static float frac(float[][] sp, int i, float z) {
		return Mth.clamp((sp[i][2] - z) / Math.max(0.01f, sp[i][2] - sp[i + 1][2]), 0f, 1f);
	}

	private static float yAt(float[][] sp, float z) {
		int i = seg(sp, z);
		return Mth.lerp(frac(sp, i, z), sp[i][1], sp[i + 1][1]);
	}

	private static float widthAt(float[][] sp, float[] w, float z) {
		int i = seg(sp, z);
		return Mth.lerp(frac(sp, i, z), w[i], w[i + 1]);
	}

	private static float topAt(float[][] sp, float[] h, float z) {
		int i = seg(sp, z);
		return yAt(sp, z) + Mth.lerp(frac(sp, i, z), h[i], h[i + 1]);
	}
}
