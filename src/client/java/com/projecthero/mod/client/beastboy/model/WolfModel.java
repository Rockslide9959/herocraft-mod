package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 fix round: Beast Boy's wolf (0.85 blocks at the shoulder line, about 1.2 long), replacing the tinted vanilla wolf. A
 * countershaded body (dark saddle, cream belly), a deep chest under a neck ruff, a long muzzle with a glossy nose, upright ears, slim
 * front legs, digitigrade hind legs, and a bushy tail that sways gently (damped, lifting with speed and trailing the body's turns).
 * Crouching sits him down ({@link BeastPose#sit}, eased in and out). Moves: bite (lunge and snap), howl (sits up, head back).
 */
public final class WolfModel {
	private WolfModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float bite = s.isAny("bite", "strike") ? s.prog : -1f;
		float howl = s.env("howl", 0.18f, 0.22f);
		float sit = BeastPose.smooth(Math.max(s.sit, howl * 0.9f));
		float amt = s.walkAmt * (1f - sit);
		float ph = s.walkPos * 0.66f;
		float run = Mth.clamp((s.walkAmt - 0.3f) / 0.55f, 0f, 1f) * (1f - sit);
		float breathe = Mth.sin(t * 0.16f);
		float lunge = bite >= 0f ? Mth.sin(bite * Mth.PI) : 0f;

		// ---- body: pivots at the hips, so sitting drops the rump and tips the chest up
		float bob = -Math.abs(Mth.sin(ph)) * 0.55f * amt + breathe * 0.1f;
		float pitch = Mth.lerp(sit, Mth.sin(ph * 2f) * 0.03f * amt + lunge * 0.1f + run * 0.03f, -0.66f);
		float hipY = Mth.lerp(sit, 9.0f + bob, 3.8f);
		float hipZ = Mth.lerp(sit, -6f + lunge * 1.6f, -4.4f);
		Matrix4f body = new Matrix4f().translation(0f, hipY, hipZ).rotateYXZ(0f, pitch, Mth.sin(ph) * 0.03f * amt).translate(0f, 0f, 6.2f);

		// ---- hind legs (digitigrade): hip, knee forward, hock back, foot
		for (int side = -1; side <= 1; side += 2) {
			float[] hip = Rig.tp(body, side * 2.2f, -0.5f, -6.3f);
			float[] st = Rig.step(ph + (side > 0 ? Mth.PI : 0f), 3.2f * amt + run * 0.8f, 1.8f * amt);
			float[] footS = v(side * 2.2f, 0.55f + st[1], hip[2] - 0.4f + st[0]);
			float[] hockS = add(footS, v(0f, 3.2f, -1.4f + st[1] * 0.3f));
			float[] footSit = v(side * 2.5f, 0.55f, hip[2] + 3.0f);
			float[] hockSit = v(side * 2.7f, 0.9f, hip[2] - 1.2f);
			float[] foot = lerp(footS, footSit, sit), hock = lerp(hockS, hockSit, sit);
			float[] knee = ik(hip, hock, 4.2f, 3.9f, v(side * 0.15f, 0.25f * sit, 1f));
			float[] th = lerp(hip, knee, 0.42f);
			ellipsoid(pose, th[0], th[1] + 0.3f, th[2], 1.75f, 2.5f, 2.4f, FUR_BACK_V);
			Rig.limb4(pose, hip, knee, hock, foot, 1.8f, 1.1f, 0.75f, 0.7f, FUR_BACK, v(side, 0.3f, 0f));
			paw(pose, foot, 0.95f);
		}

		// ---- front legs: shoulder blade, elbow back, wrist, paw
		for (int side = -1; side <= 1; side += 2) {
			float[] sh = Rig.tp(body, side * 2.0f, -1.4f, 4.9f);
			float[] st = Rig.step(ph + (side > 0 ? 0f : Mth.PI), 3.4f * amt + run * 0.9f, 1.7f * amt);
			float[] footS = v(side * 2.0f, 0.55f + st[1], sh[2] + 0.4f + st[0]);
			if (bite >= 0f) {
				footS[2] += lunge * 1.2f;
			}
			float[] footSit = v(side * 1.8f, 0.55f, sh[2] + 0.9f);
			float[] foot = lerp(footS, footSit, sit);
			float[] wrist = add(foot, v(0f, 1.3f, -0.35f + st[1] * 0.25f));
			float[] elbow = ik(sh, wrist, 3.9f, 3.7f, v(0f, 0f, -1f));
			float[] blade = Rig.tp(body, side * 1.7f, 0.6f, 4.4f);
			ellipsoid(pose, blade[0], blade[1], blade[2], 1.2f, 2.2f, 1.6f, FUR_BACK_V);
			Rig.limb(pose, sh, elbow, wrist, 1.45f, 0.95f, 0.72f, FUR_BACK, v(side, 0.2f, 0f));
			loft(pose, new float[][] { wrist, lerp(wrist, foot, 0.5f), add(foot, v(0f, 0.35f, 0.2f)) }, new float[] { 0.72f, 0.66f, 0.7f },
					new float[] { 0.72f, 0.66f, 0.7f }, 2, 10, FUR_LT, 0, null);
			paw(pose, foot, 1f);
		}

		// ---- torso
		Rig.push(pose, body);
		float br = 1f + breathe * 0.03f;
		float[][] spine = { { 0f, 0.7f, -9.2f }, { 0f, 0.9f, -6.8f }, { 0f, 0.55f, -2.4f }, { 0f, 0.3f, 2.2f }, { 0f, -0.35f, 5.4f }, { 0f, -0.5f, 8.0f } };
		float[] w = { 1.7f, 2.9f, 2.55f * br, 3.1f * br, 3.35f * br, 2.4f };
		float[] h = { 2.0f, 3.1f, 2.7f * br, 3.7f * br, 4.3f * br, 3.0f };
		loft(pose, spine, w, h, 3, 16, FUR_BACK, 0, null);
		// haunches and shoulders, a little fuller than the tube
		for (int side = -1; side <= 1; side += 2) {
			ellipsoid(pose, side * 1.3f, 1.0f, -6.0f, 1.9f, 2.6f, 2.8f, FUR_BACK_V);
		}
		// the ruff: neck fur and a fluffy chest
		ellipsoid(pose, 0f, 1.6f, 6.6f, 3.2f, 3.5f, 3.0f, FUR_BACK_V);
		ellipsoid(pose, 0f, -1.6f, 7.4f, 2.4f, 2.6f, 2.0f, FUR_LT);
		for (int k = -2; k <= 2; k++) {
			cone(pose, k * 0.9f, -2.6f, 7.6f - Math.abs(k) * 0.3f, k * 0.15f, -1f, 0.35f, 1.8f, 0.7f, 0.5f, FUR_LT);
		}
		for (int side = -1; side <= 1; side += 2) {
			for (int k = 0; k < 3; k++) {
				cone(pose, side * 2.6f, 2.2f - k * 1.4f, 6.4f - k * 0.2f, side * 1f, -0.25f, -0.5f, 1.5f, 0.75f, 0.55f, k == 2 ? FUR_LT : FUR_BACK_V);
			}
		}
		// a ridge of darker guard hair along the back
		for (int k = 0; k < 6; k++) {
			cone(pose, 0f, 3.4f + Mth.sin(k * 0.9f) * 0.15f, 5.5f - k * 2.3f, 0f, 0.6f, -1f, 1.4f, 0.8f, 0.5f, FUR);
		}

		// ---- neck and head (kept near level when he sits; raised for the howl)
		loft(pose, new float[][] { { 0f, 1.2f, 6.2f }, { 0f, 2.8f, 8.0f }, { 0f, 3.9f, 9.2f } }, new float[] { 2.5f, 2.2f, 2.0f }, new float[] { 2.8f, 2.4f, 2.1f },
				3, 12, FUR_BACK, 0, null);
		float hy = s.yaw(0.8f) * 0.85f;
		float hp = -pitch * 0.9f + s.pitch(0.6f) * 0.55f - howl * 0.95f + lunge * 0.15f + run * 0.12f - sit * 0.08f;
		boneYXZ(pose, 0f, 4.0f, 9.0f, hy, hp, 0f);
		float jaw = 0.06f + run * 0.16f + Math.max(0f, Mth.sin(t * 0.33f)) * 0.04f * run;
		if (bite >= 0f) {
			jaw = Math.max(jaw, bite < 0.55f ? BeastPose.smooth(bite / 0.55f) * 0.85f : 0.85f * (1f - BeastPose.smooth((bite - 0.55f) / 0.2f)));
		}
		jaw = Math.max(jaw, howl * 0.55f);
		head(pose, t, jaw, Math.max(howl, lunge * 0.6f));
		pose.popPose();
		pose.popPose();

		// ---- tail (world space): a gentle damped sway, lifted out behind at speed, curled on the ground when sitting
		float sway = Mth.sin(t * 0.085f) * 0.11f * (1f - run) * (1f - sit) + Mth.sin(ph) * 0.06f * amt + s.tailLag * (1f - sit);
		float tp0 = Mth.lerp(run, 0.72f, 0.3f) - lunge * 0.2f - pitch * (1f - sit);
		tp0 = Mth.lerp(sit, tp0, 1.15f);
		int n = 6;
		float[] yb = new float[n], pb = new float[n];
		for (int i = 0; i < n; i++) {
			float stand = Mth.sin(t * 0.085f - (i + 1) * 0.55f) * 0.025f * (1f - run);
			yb[i] = Mth.lerp(sit, stand, i < 5 ? 0.32f : 0.15f);
			pb[i] = Mth.lerp(sit, Mth.lerp(run, 0.07f, -0.03f), i < 3 ? -0.36f : 0f);
		}
		float[][] tail = walk(Rig.tp(body, 0f, 1.5f, -9.4f), Mth.PI + sway, tp0, 1.55f, yb, pb);
		// the tail lies on the ground rather than through it
		for (int i = 0; i < tail.length; i++) {
			tail[i][1] = Math.max(tail[i][1], 1.0f + i * 0.04f);
		}
		float[] tw = { 0.85f, 1.3f, 1.55f, 1.6f, 1.45f, 1.05f, 0.35f };
		loft(pose, tail, tw, tw, 3, 12, FUR_BACK, 0, null);
		float[] tip = lerp(tail[n - 1], tail[n], 0.35f);
		ellipsoid(pose, tip[0], tip[1], tip[2], 1.05f, 1.05f, 1.05f, 10, 7, FUR_LT, 0f, 1f);
	}

	/** A paw at {@code foot}: pad, toes and little claws. */
	static void paw(PoseStack pose, float[] f, float k) {
		ellipsoid(pose, f[0], f[1] - 0.05f, f[2] + 0.45f, 0.9f * k, 0.55f * k, 1.15f * k, 10, 7, FUR_LT, 0f, 1f);
		for (int i = -1; i <= 1; i++) {
			ellipsoid(pose, f[0] + i * 0.5f * k, f[1] - 0.1f, f[2] + 1.35f * k - Math.abs(i) * 0.2f, 0.36f * k, 0.38f * k, 0.42f * k, 8, 5, FUR_LT, 0f, 1f);
			cone(pose, f[0] + i * 0.5f * k, f[1] - 0.15f, f[2] + 1.7f * k - Math.abs(i) * 0.2f, 0f, -0.45f, 1f, 0.45f, 0.13f, 0.1f, CLAW);
		}
	}

	/** The wolf's head (pivot at the back of the skull, +Z forward, level). */
	static void head(PoseStack pose, float t, float jaw, float earsBack) {
		ellipsoid(pose, 0f, 0.6f, 1.3f, 2.55f, 2.35f, 2.6f, FUR_BACK_V);
		ellipsoid(pose, 0f, 1.5f, 2.5f, 1.95f, 1.15f, 1.6f, FUR_BACK_V);
		for (int side = -1; side <= 1; side += 2) {
			// cheeks with a tuft swept back
			ellipsoid(pose, side * 1.5f, -0.4f, 2.1f, 1.0f, 1.1f, 1.3f, FUR_LT);
			cone(pose, side * 2.0f, -0.6f, 1.3f, side * 0.8f, -0.35f, -0.7f, 1.1f, 0.6f, 0.45f, FUR_LT);
			// eyes under a brow
			eye(pose, side * 1.15f, 1.2f, 3.55f, 0.47f, side * 0.5f, 0.05f, EYE);
			ellipsoid(pose, side * 1.1f, 1.75f, 3.45f, 0.75f, 0.32f, 0.55f, 8, 5, FUR_BACK_V, 0f, 1f);
			// upright ears, laid back for the bite and the howl
			float back = earsBack * 0.7f;
			float flick = Mth.sin(t * 0.17f + side * 1.3f) > 0.95f ? 0.25f : 0f;
			float[] ed = { side * 0.32f, 1f, -0.15f - back - flick };
			cone(pose, side * 1.35f, 2.35f, 1.0f, ed[0], ed[1], ed[2], 2.7f, 0.95f, 0.45f, FUR);
			cone(pose, side * 1.38f, 2.4f, 1.28f, ed[0], ed[1], ed[2], 2.05f, 0.6f, 0.12f, INNER_EAR);
		}
		// the muzzle: dark on top, pale underneath, a glossy nose
		loft(pose, new float[][] { { 0f, 0.15f, 2.6f }, { 0f, -0.05f, 4.6f }, { 0f, -0.2f, 6.6f } }, new float[] { 1.55f, 1.15f, 0.8f },
				new float[] { 1.45f, 1.1f, 0.78f }, 3, 12, FUR_BACK, 0, null);
		ellipsoid(pose, 0f, 0.1f, 6.65f, 0.62f, 0.48f, 0.45f, 10, 7, NOSE, 0f, 1f);
		ellipsoid(pose, 0f, -0.75f, 4.4f, 1.05f, 0.32f, 2.1f, 10, 4, MOUTH, 0.35f, 1f);
		for (int side = -1; side <= 1; side += 2) {
			cone(pose, side * 0.62f, -0.7f, 6.0f, 0f, -1f, 0.05f, 0.65f, 0.18f, 0.18f, TOOTH);
			cone(pose, side * 0.8f, -0.7f, 5.0f, 0f, -1f, 0f, 0.35f, 0.14f, 0.14f, TOOTH);
		}
		// lower jaw
		bone(pose, 0f, -0.75f, 2.6f, jaw * 0.75f, 0f, 0f);
		loft(pose, new float[][] { { 0f, -0.2f, 0f }, { 0f, -0.3f, 2.0f }, { 0f, -0.25f, 3.7f } }, new float[] { 1.25f, 0.95f, 0.6f },
				new float[] { 0.6f, 0.5f, 0.38f }, 2, 10, FUR_LT, 0, null);
		ellipsoid(pose, 0f, 0.1f, 2.0f, 0.85f, 0.2f, 1.6f, 10, 4, MOUTH, 0f, 0.5f);
		for (int side = -1; side <= 1; side += 2) {
			cone(pose, side * 0.5f, 0.05f, 3.3f, 0f, 1f, 0.05f, 0.5f, 0.15f, 0.15f, TOOTH);
		}
		pose.popPose();
	}
}
