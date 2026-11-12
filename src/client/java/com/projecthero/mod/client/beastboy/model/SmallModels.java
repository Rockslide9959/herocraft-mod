package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's small forms: the mouse (big round ears, long tail, whiskers), the fly (compound eyes, buzzing see-through wings)
 * and the snake (coiled, head raised; slithers in an S when moving). Snake moves: strike / bite. Mouse: bite. Fly: none (it can't attack).
 */
public final class SmallModels {
	private SmallModels() {
	}

	// ------------------------------------------------------------------------------------------------------------------ mouse

	public static void mouse(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		float ph = s.walkPos * 1.2f;
		float bob = -Math.abs(Mth.sin(ph)) * 0.25f * amt;
		float sniff = Mth.sin(t * 0.9f) * 0.08f * (1f - amt);
		pose.pushPose();
		pose.translate(0f, bob, 0f);
		// body: a pear, bigger at the back
		ellipsoid(pose, 0f, 1.9f, -0.6f, 1.75f, 1.65f, 2.4f, COUNTER_V);
		ellipsoid(pose, 0f, 2.1f, 1.0f, 1.35f, 1.35f, 1.7f, COUNTER_V);
		// legs: little paws scurrying
		for (int sd = -1; sd <= 1; sd += 2) {
			for (int fb = 0; fb < 2; fb++) {
				float[] st = Rig.step(ph + (fb == 0 ? 0f : Mth.PI) + (sd > 0 ? 0f : Mth.PI), 0.6f * amt, 0.4f * amt);
				float z = (fb == 0 ? 1.5f : -1.4f) + st[0];
				ellipsoid(pose, sd * 1.05f, 0.45f + st[1], z, 0.42f, 0.5f, 0.7f, 8, 5, fb == 0 ? INNER_EAR : COUNTER_V, 0f, 1f);
			}
		}
		// head
		bone(pose, 0f, 2.6f, 2.3f, s.pitch(0.5f) * 0.4f + sniff, s.yaw(0.7f) * 0.8f, 0f);
		float bite = s.env("bite", 0.2f, 0.3f);
		ellipsoid(pose, 0f, 0.2f, 0.6f, 1.25f, 1.15f, 1.4f, COUNTER_V);
		loft(pose, new float[][] { { 0f, 0.1f, 1.2f }, { 0f, -0.05f, 2.2f }, { 0f, -0.2f, 3.0f } }, new float[] { 0.9f, 0.6f, 0.25f },
				new float[] { 0.8f, 0.55f, 0.25f }, 2, 10, COUNTER, 0, null);
		ellipsoid(pose, 0f, -0.1f, 3.05f, 0.28f, 0.24f, 0.22f, 8, 5, DARK, 0f, 1f);
		for (int sd = -1; sd <= 1; sd += 2) {
			eye(pose, sd * 0.78f, 0.55f, 1.55f, 0.36f, sd * 0.8f, 0f, EYE);
			// round ears
			boneYXZ(pose, sd * 0.85f, 1.15f, 0.25f, sd * 0.35f, -0.2f, sd * -0.35f);
			ellipsoid(pose, 0f, 0.85f, 0f, 0.95f, 0.95f, 0.18f, 12, 8, COUNTER_V, 0f, 1f);
			ellipsoid(pose, 0f, 0.85f, 0.09f, 0.7f, 0.7f, 0.12f, 12, 8, INNER_EAR, 0f, 1f);
			pose.popPose();
			// whiskers
			for (int k = 0; k < 3; k++) {
				cone(pose, sd * 0.45f, -0.05f + (k - 1) * 0.12f, 2.45f, sd * 1f, (k - 1) * 0.15f, -0.25f + Mth.sin(t * 0.7f + k) * 0.05f, 1.7f, 0.035f, 0.035f, DARK);
			}
		}
		// buck teeth
		bone(pose, 0f, -0.35f, 2.6f, bite * 0.6f, 0f, 0f);
		box(pose, -0.17f, -0.5f, 0f, -0.02f, 0f, 0.12f, TOOTH);
		box(pose, 0.02f, -0.5f, 0f, 0.17f, 0f, 0.12f, TOOTH);
		pose.popPose();
		pose.popPose();
		// tail
		int n = 7;
		float[] yb = new float[n], pb = new float[n];
		for (int i = 0; i < n; i++) {
			yb[i] = Mth.sin(t * 0.2f - i * 0.6f) * 0.12f;
			pb[i] = i == 0 ? 0.2f : -0.07f;
		}
		float[][] tail = walk(v(0f, 1.3f, -2.8f), Mth.PI, 0.25f, 1.05f, yb, pb);
		float[] tw = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			tw[i] = Mth.lerp(i / (float) n, 0.32f, 0.08f);
		}
		loft(pose, tail, tw, tw, 2, 8, INNER_EAR, 0, null);
		pose.popPose();
	}

	// -------------------------------------------------------------------------------------------------------------------- fly

	public static void fly(PoseStack pose, BeastPose s) {
		float t = s.age;
		boolean air = s.airborne || s.inWater;
		float hover = air ? Mth.sin(t * 0.3f) * 0.15f : 0f;
		pose.pushPose();
		pose.translate(0f, (air ? 0.9f : 0.55f) + hover, 0f);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(air ? 0.15f : 0f));
		// abdomen, thorax, head
		ellipsoid(pose, 0f, 0.55f, -0.95f, 0.62f, 0.5f, 0.95f, STRIPED_V);
		ellipsoid(pose, 0f, 0.7f, 0.1f, 0.62f, 0.6f, 0.65f, SKIN_DK);
		bone(pose, 0f, 0.75f, 0.75f, s.pitch(0.5f) * 0.4f, s.yaw(0.6f) * 0.6f, 0f);
		ellipsoid(pose, 0f, 0f, 0.25f, 0.52f, 0.5f, 0.42f, SKIN);
		for (int sd = -1; sd <= 1; sd += 2) {
			ellipsoidR(pose, sd * 0.4f, 0.08f, 0.35f, 0.38f, 0.48f, 0.4f, sd * 0.4f, 0f, 0f, COMPOUND);
		}
		// proboscis
		cone(pose, 0f, -0.3f, 0.5f, 0f, -1f, 0.4f, 0.35f, 0.1f, 0.1f, DARK);
		pose.popPose();
		// legs: three pairs, bent
		for (int sd = -1; sd <= 1; sd += 2) {
			for (int k = 0; k < 3; k++) {
				float z = 0.35f - k * 0.35f;
				float twitch = Mth.sin(t * 0.6f + k + sd) * 0.05f;
				float[] a = v(sd * 0.3f, 0.45f, z);
				float[] b = v(sd * 0.75f, 0.75f + twitch, z + (1 - k) * 0.25f);
				float[] c = v(sd * 0.95f, air ? 0.1f : -0.5f, z + (1 - k) * 0.45f);
				Rig.limb(pose, a, b, c, 0.07f, 0.06f, 0.04f, DARK, null);
			}
		}
		// wings: buzzing when flying, folded back when landed
		for (int sd = -1; sd <= 1; sd += 2) {
			float buzz = air ? Mth.sin(t * 9.1f) * 0.7f : 0f;
			boneYXZ(pose, sd * 0.25f, 1.15f, 0.15f, sd * (air ? 0.25f : 0.05f), 0f, sd * (-0.25f + buzz));
			float[][] lead = { { 0f, 0f, 0f }, { sd * 0.8f, 0.05f, 0.05f }, { sd * 1.6f, 0.1f, -0.45f }, { sd * 1.9f, 0.1f, -1.2f } };
			float[][] trail = { { 0f, 0f, -0.15f }, { sd * 0.5f, 0f, -0.8f }, { sd * 1.0f, 0.05f, -1.4f }, { sd * 1.9f, 0.1f, -1.2f } };
			if (!air) {
				for (float[] q : lead) {
					float x = q[0];
					q[0] = x * 0.25f;
					q[2] = q[2] - Math.abs(x) * 0.8f;
				}
				for (float[] q : trail) {
					float x = q[0];
					q[0] = x * 0.3f;
					q[2] = q[2] - Math.abs(x) * 0.8f;
				}
			}
			ribbon(pose, lead, trail, FLYWING);
			pose.popPose();
		}
		pose.popPose();
	}

	// ------------------------------------------------------------------------------------------------------------------ snake

	/** The snake. Also drawn by the Snake Strike quick morph (with a forced strike progress). */
	public static void snake(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		float strike = Math.max(s.env("strike", 0.15f, 0.35f), s.env("bite", 0.15f, 0.35f));
		float lunge = s.isAny("strike", "bite") ? Mth.sin(Mth.clamp(s.prog / 0.55f, 0f, 1f) * Mth.PI) : 0f;
		float slither = Mth.clamp(amt * 1.6f, 0f, 1f);
		float ph = s.walkPos * 0.9f + t * 0.05f;

		// body points: coiled at rest, an S-wave trailing behind when moving
		int coil = 11;
		float[][] pts = new float[coil + 4][];
		for (int i = 0; i < coil; i++) {
			float k = i / (float) (coil - 1); // 0 = tail tip, 1 = where the neck rises
			// coil: a spiral getting wider toward the neck
			float ang = (1f - k) * 9.5f + 0.4f;
			float rad = 1.2f + k * 2.6f;
			float[] c = v(Mth.sin(ang) * rad, 0.95f, -1.6f + Mth.cos(ang) * rad);
			// slither: trailing straight back with a travelling wave
			float zb = -2f - (1f - k) * 13f;
			float[] sl = v(Mth.sin(ph - (1f - k) * 4.5f) * 1.6f * (0.4f + (1f - k) * 0.8f), 0.95f, zb);
			pts[i] = lerp(c, sl, slither);
		}
		float rise = 1f - slither * 0.45f;
		float sway = Mth.sin(t * 0.07f) * 0.6f * (1f - strike);
		pts[coil] = v(sway * 0.3f, 1.4f, 0.4f + lunge * 2f);
		pts[coil + 1] = v(sway * 0.6f, 1.4f + 2.2f * rise - lunge * 0.8f, 1.8f + lunge * 4.5f);
		pts[coil + 2] = v(sway * 0.8f, 1.4f + 4.0f * rise - lunge * 1.6f, 2.8f + lunge * 7.5f);
		pts[coil + 3] = v(sway, 1.4f + 4.6f * rise - lunge * 2f, 4.0f + lunge * 10f);
		float[] w = new float[pts.length];
		for (int i = 0; i < pts.length; i++) {
			float k = i / (float) (pts.length - 1);
			w[i] = k < 0.15f ? Mth.lerp(k / 0.15f, 0.1f, 0.7f) : (k > 0.85f ? Mth.lerp((k - 0.85f) / 0.15f, 1.0f, 0.75f) : Mth.lerp((k - 0.15f) / 0.7f, 0.7f, 1.05f));
		}
		loft(pose, pts, w, w, 3, 10, SCALES, 2, v(0f, 1f, 0f));
		// head
		float[] nb = pts[coil + 2], nt = pts[coil + 3];
		float[] d = sub(nt, nb);
		float hy = (float) Math.atan2(d[0], d[2]) + s.yaw(0.6f) * 0.5f;
		float hp = 0.12f + lunge * 0.15f + s.pitch(0.5f) * 0.3f;
		boneYXZ(pose, nt[0], nt[1], nt[2], hy, hp, 0f);
		float jaw = strike * (s.prog < 0.6f ? 1f : 0.3f);
		head(pose, t, jaw);
		pose.popPose();
	}

	public static void head(PoseStack pose, float t, float jaw) {
		ellipsoid(pose, 0f, 0.2f, 1.1f, 1.15f, 0.75f, 1.75f, SCALES_V);
		ellipsoid(pose, 0f, 0.35f, 0.4f, 1.3f, 0.8f, 1.2f, SCALES_V);
		for (int sd = -1; sd <= 1; sd += 2) {
			eye(pose, sd * 0.85f, 0.55f, 1.4f, 0.36f, sd * 1.0f, 0f, EYE_SLIT);
			ellipsoid(pose, sd * 0.35f, 0.4f, 2.7f, 0.12f, 0.1f, 0.1f, 6, 4, DARK, 0f, 1f);
		}
		bone(pose, 0f, -0.2f, 0f, jaw * 0.9f, 0f, 0f);
		ellipsoid(pose, 0f, -0.25f, 1.2f, 1.0f, 0.4f, 1.6f, COUNTER_V);
		ellipsoid(pose, 0f, 0.05f, 1.2f, 0.85f, 0.2f, 1.4f, 8, 4, MOUTH, 0f, 0.5f);
		pose.popPose();
		ellipsoid(pose, 0f, -0.05f, 1.2f, 0.85f, 0.2f, 1.4f, 8, 4, MOUTH, 0.5f, 1f);
		// fangs show when it opens
		for (int sd = -1; sd <= 1; sd += 2) {
			cone(pose, sd * 0.45f, -0.05f, 2.2f, 0f, -1f, 0.25f, 0.4f + jaw * 0.3f, 0.09f, 0.09f, TOOTH);
		}
		// forked tongue flicking
		float flick = Mth.sin(t * 0.35f);
		if (flick > 0.2f || jaw > 0.3f) {
			float l = jaw > 0.3f ? 1.4f : (flick - 0.2f) * 1.8f;
			loft(pose, new float[][] { { 0f, -0.1f, 2.4f }, { 0f, -0.15f, 2.4f + l } }, new float[] { 0.08f, 0.06f }, new float[] { 0.05f, 0.04f }, 1, 6, MOUTH, 0,
					null);
			for (int sd = -1; sd <= 1; sd += 2) {
				cone(pose, 0f, -0.15f, 2.4f + l, sd * 0.4f, 0f, 1f, 0.45f, 0.05f, 0.04f, MOUTH);
			}
		}
	}
}
