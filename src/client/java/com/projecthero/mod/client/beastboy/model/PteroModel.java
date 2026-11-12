package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's pterodactyl (a Pteranodon, about 4 blocks of wingspan): long pointed beak, swept-back crest, membrane wings on
 * the arm and the long fourth finger. On the ground it walks on its folded wing-hands; in the air it flaps (glides when sinking).
 * Moves: dive (wings swept back, talons forward at the end), screech / bite (beak open), grab (talons).
 */
public final class PteroModel {
	private PteroModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		boolean air = s.airborne || s.inWater || s.isAny("dive");
		float dive = s.env("dive", 0.2f, 0.15f);
		float screech = Math.max(s.env("screech", 0.15f, 0.2f), s.env("bite", 0.15f, 0.3f));
		float grab = s.env("grab", 0.25f, 0.25f);
		float amt = s.walkAmt;
		float ph = s.walkPos * 0.7f;

		// flap: full beats when climbing / level, a gentle glide when sinking
		boolean glide = s.vy < -0.08f;
		float flapSpeed = glide ? 0.12f : 0.42f;
		float flapAmp = glide ? 0.12f : 0.75f;
		float flap = air ? Mth.sin(t * flapSpeed) * flapAmp * (1f - dive) : 0f;
		float spread = air ? 1f - dive * 0.65f : 0f;

		float bodyPitch = air ? (0.15f + dive * 0.85f - grab * 0.3f) : 0.15f;
		float bodyY = air ? 9f + Mth.sin(t * flapSpeed + 1.4f) * (glide ? 0.3f : 1.2f) : 8.5f - Math.abs(Mth.sin(ph)) * 0.6f * amt;
		Matrix4f body = Rig.mat(0f, bodyY, 0f, 0f, bodyPitch, air ? 0f : Mth.sin(ph) * 0.05f * amt);

		// ---- hind legs
		for (int side = -1; side <= 1; side += 2) {
			float[] hip = Rig.tp(body, side * 2.2f, -0.5f, -4.5f);
			float[] foot;
			if (air) {
				float fwd = grab > 0f ? grab : (s.is("dive") && s.prog > 0.75f ? (s.prog - 0.75f) * 4f : 0f);
				foot = Rig.tp(body, side * 2.6f, -2f - fwd * 2f, -11f + fwd * 13f);
			} else {
				float[] st = Rig.step(ph + (side > 0 ? 0f : Mth.PI), 2.6f * amt, 1.8f * amt);
				foot = v(side * 2.6f, 0.6f + st[1], -4.5f + st[0]);
			}
			float[] knee = ik(hip, foot, 4.6f, 4.6f, v(0f, 0.3f, 1f));
			Rig.limb(pose, hip, knee, foot, 1.3f, 0.8f, 0.6f, COUNTER, null);
			for (int k = -1; k <= 1; k++) {
				float[] d = sub(foot, knee);
				cone(pose, foot[0] + k * 0.5f, foot[1], foot[2], k * 0.3f + d[0] * 0.1f, -0.4f, 1f, 1.6f, 0.3f, 0.3f, CLAW);
			}
		}

		// ---- wings (root space so the folded hands can stand on the ground)
		for (int side = -1; side <= 1; side += 2) {
			float[] sh = Rig.tp(body, side * 3.2f, 1.2f, 3.5f);
			float[] el, wr, fm, tip;
			float[] hipSide = Rig.tp(body, side * 2.2f, -0.6f, -5.5f);
			if (air) {
				// spread chain in body space, then rolled by the flap round the body's long axis
				float roll = flap;
				float sweep = dive * 0.9f;
				float[] e0 = v(side * 9f, 0.8f, 2.2f - sweep * 3f), w0 = v(side * 15f, 1.0f, 3.5f - sweep * 8f),
						m0 = v(side * 24f, 0.4f, 0f - sweep * 10f), t0 = v(side * (24f + 9f * spread), -0.4f, -6f - sweep * 9f);
				el = Rig.tp(body, rollX(e0, side, roll, 3.2f)[0], rollX(e0, side, roll, 3.2f)[1] + 1.2f, e0[2]);
				wr = Rig.tp(body, rollX(w0, side, roll * 1.15f, 3.2f)[0], rollX(w0, side, roll * 1.15f, 3.2f)[1] + 1.2f, w0[2]);
				fm = Rig.tp(body, rollX(m0, side, roll * 1.3f, 3.2f)[0], rollX(m0, side, roll * 1.3f, 3.2f)[1] + 1.2f, m0[2]);
				tip = Rig.tp(body, rollX(t0, side, roll * 1.45f, 3.2f)[0], rollX(t0, side, roll * 1.45f, 3.2f)[1] + 1.2f, t0[2]);
				if (dive > 0f) {
					el = lerp(el, Rig.tp(body, side * 6f, 1.6f, 0f), dive * 0.5f);
				}
			} else {
				// folded: the wrist is a front foot, the long finger folds back up along the body
				float[] st = Rig.step(ph + (side > 0 ? Mth.PI : 0f), 3.2f * amt, 2.4f * amt);
				wr = v(side * 6.5f, 1.2f + st[1], 6f + st[0]);
				el = ik(sh, wr, 5.4f, 6.4f, v(side * 0.8f, 0.6f, -0.4f));
				fm = add(wr, v(side * 1.2f, 6.5f, -5f));
				tip = add(fm, v(side * 0.2f, 2.8f, -9f));
			}
			// arm bones as a tube
			loft(pose, new float[][] { sh, el, wr, fm, tip }, new float[] { 1.5f, 1.1f, 0.9f, 0.55f, 0.15f }, new float[] { 1.5f, 1.1f, 0.9f, 0.55f, 0.15f },
					3, 8, COUNTER, 0, null);
			// little fingers at the wrist
			for (int k = 0; k < 3; k++) {
				float[] d = air ? v(side * 0.2f, 0.4f, 1f) : v(side * 0.2f, -0.8f, 1f);
				cone(pose, wr[0] - side * k * 0.3f, wr[1], wr[2] + k * 0.25f, d[0] + k * 0.15f * side, d[1], d[2], 1.4f, 0.25f, 0.25f, CLAW);
			}
			// membrane: leading edge along the arm and finger, trailing edge scalloped back to the hip
			int n = 9;
			float[][] lead = new float[n][], trail = new float[n][];
			float[][] le = { sh, el, wr, fm, tip };
			for (int i = 0; i < n; i++) {
				float u = i / (float) (n - 1);
				lead[i] = along(le, u);
				float[] straight = lerp(hipSide, tip, u);
				float[] toLead = sub(lead[i], straight);
				float scallop = Mth.sin(u * Mth.PI) * 0.22f;
				trail[i] = add(straight, mul(toLead, scallop));
			}
			ribbon(pose, lead, trail, MEMBRANE);
		}

		// ---- body, neck, head
		Rig.push(pose, body);
		loft(pose, new float[][] { { 0f, 0.2f, -8.5f }, { 0f, 0f, -5f }, { 0f, 0.6f, 0f }, { 0f, 1.2f, 4f }, { 0f, 2.6f, 6.5f } },
				new float[] { 0.6f, 2.6f, 3.4f, 3.2f, 1.9f }, new float[] { 0.6f, 2.8f, 3.6f, 3.4f, 2.0f }, 3, 12, COUNTER, 0, null);
		float neckLift = air ? 0f : 1.5f;
		float[] neckTop = v(0f, 4.8f + neckLift - dive * 1.5f, 9.8f + dive * 1.2f);
		loft(pose, new float[][] { { 0f, 2.6f, 6.5f }, { 0f, 3.8f + neckLift * 0.5f, 8.2f }, neckTop }, new float[] { 1.9f, 1.4f, 1.3f },
				new float[] { 2.0f, 1.5f, 1.4f }, 3, 10, COUNTER, 0, null);
		float hy = s.yaw(0.8f) * 0.8f;
		float hp = s.pitch(0.7f) * 0.5f - bodyPitch * 0.7f - screech * 0.35f + (air ? 0f : 0.1f) + Mth.sin(ph) * 0.05f * amt;
		boneYXZ(pose, neckTop[0], neckTop[1], neckTop[2], hy, hp, 0f);
		head(pose, screech);
		pose.popPose();
		pose.popPose();
	}

	private static float[] rollX(float[] p, float side, float roll, float pivotX) {
		// roll a point of the wing round the shoulder line (an axis along Z through x = side * pivotX)
		float dx = p[0] - side * pivotX;
		float c = Mth.cos(roll), sn = Mth.sin(roll);
		return new float[] { side * pivotX + dx * c, p[1] + Math.abs(dx) * sn, p[2] };
	}

	private static float[] along(float[][] chain, float u) {
		float total = 0f;
		float[] segs = new float[chain.length - 1];
		for (int i = 0; i < segs.length; i++) {
			segs[i] = len(sub(chain[i + 1], chain[i]));
			total += segs[i];
		}
		float d = u * total;
		for (int i = 0; i < segs.length; i++) {
			if (d <= segs[i] || i == segs.length - 1) {
				return lerp(chain[i], chain[i + 1], Mth.clamp(d / Math.max(segs[i], 1.0e-4f), 0f, 1f));
			}
			d -= segs[i];
		}
		return chain[chain.length - 1];
	}

	/** Head: pivot at the top of the neck, +Z forward. */
	public static void head(PoseStack pose, float open) {
		ellipsoid(pose, 0f, 0.8f, 1.2f, 1.7f, 1.9f, 2.6f, COUNTER_V);
		for (int s = -1; s <= 1; s += 2) {
			eye(pose, s * 1.35f, 1.4f, 2.0f, 0.65f, s * 1.0f, 0f, EYE);
		}
		// the crest: a flat blade swept back
		loft(pose, new float[][] { { 0f, 1.8f, 0.8f }, { 0f, 2.6f, -2.5f }, { 0f, 3.6f, -6f }, { 0f, 4.4f, -8.8f } }, new float[] { 0.7f, 0.5f, 0.35f, 0.1f },
				new float[] { 1.5f, 1.4f, 1.0f, 0.2f }, 3, 8, SKIN_DK, 0, v(0f, 1f, 0f));
		// upper beak
		loft(pose, new float[][] { { 0f, 0.8f, 2.5f }, { 0f, 0.6f, 6f }, { 0f, 0.1f, 10f }, { 0f, -0.6f, 13.5f } }, new float[] { 1.4f, 1.0f, 0.6f, 0.08f },
				new float[] { 1.2f, 0.8f, 0.5f, 0.08f }, 3, 10, COUNTER, 0, null);
		// lower beak (opens)
		bone(pose, 0f, 0f, 2.2f, open * 0.55f, 0f, 0f);
		loft(pose, new float[][] { { 0f, -0.2f, 0.4f }, { 0f, -0.4f, 4f }, { 0f, -0.8f, 7.8f }, { 0f, -1.1f, 10.6f } }, new float[] { 1.2f, 0.9f, 0.5f, 0.08f },
				new float[] { 0.8f, 0.6f, 0.4f, 0.08f }, 3, 10, COUNTER, 0, null);
		ellipsoid(pose, 0f, 0.1f, 4f, 0.8f, 0.3f, 3.5f, 8, 4, MOUTH, 0f, 0.5f);
		pose.popPose();
	}
}
