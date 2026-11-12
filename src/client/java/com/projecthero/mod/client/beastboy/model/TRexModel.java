package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's T-Rex (about 3.4 blocks tall, 4.5 long): the show's big-headed cartoon tyrannosaur, striped back, pale belly,
 * tiny two-clawed arms, thick tail held out for balance. Moves: bite, roar, swipe/sweep (tail), stomp, charge.
 */
public final class TRexModel {
	private TRexModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		float freq = 0.42f;
		float ph = s.walkPos * freq;
		float breathe = Mth.sin(t * 0.09f) * 0.025f;

		float bite = s.env("bite", 0.25f, 0.25f);
		float roar = s.env("roar", 0.2f, 0.25f);
		float sweep = s.isAny("swipe", "sweep", "tail") ? Mth.sin(s.prog * Mth.PI) : 0f;
		float sweepDir = s.isAny("swipe", "sweep", "tail") ? Mth.sin(s.prog * Mth.TWO_PI) : 0f;
		float charge = s.env("charge", 0.15f, 0.2f);
		float stomp = s.is("stomp") ? s.prog : -1f;

		float bob = -Math.abs(Mth.sin(ph)) * 1.6f * amt + Mth.sin(t * 0.09f) * 0.3f;
		float pitch = 0.04f + 0.06f * amt + bite * 0.14f - roar * 0.2f + charge * 0.16f;
		float twist = Mth.sin(ph) * 0.06f * amt + sweepDir * 0.55f;
		Matrix4f body = Rig.mat(0f, 31f + bob - roar * 1f, 0f, twist, pitch, -Mth.sin(ph) * 0.03f * amt);

		// ---- legs (root space, IK to the ground)
		for (int side = -1; side <= 1; side += 2) {
			float lph = ph + (side > 0 ? 0f : Mth.PI);
			float[] st = Rig.step(lph, 9f * amt + 2.5f * charge, 5f * amt);
			float lift = st[1];
			float fz = 1f + st[0];
			if (stomp >= 0f && side < 0) {
				lift = stomp < 0.6f ? Mth.sin(stomp / 0.6f * Mth.HALF_PI) * 10f : Math.max(0f, 10f * (1f - (stomp - 0.6f) / 0.08f));
				fz = 4f;
			}
			float[] hip = Rig.tp(body, side * 8f, -1f, -2f);
			float[] foot = v(side * 9f, lift, fz);
			float[] ankle = v(side * 9f, lift + 7f, fz - 3.5f);
			float[] knee = ik(hip, ankle, 13.5f, 13.5f, v(0f, 0f, 1f));
			// thigh muscle
			float[] mid = lerp(hip, knee, 0.45f);
			float[] dir = sub(knee, hip);
			pose.pushPose();
			pose.translate(mid[0], mid[1], mid[2]);
			pose.mulPose(new org.joml.Quaternionf().rotationTo(0f, -1f, 0f, dir[0], dir[1], dir[2]));
			ellipsoid(pose, 0f, 0f, 0f, 5.2f, 9f, 6.8f, COUNTER_V);
			pose.popPose();
			Rig.limb4(pose, hip, knee, ankle, foot, 5.5f, 3.6f, 2.4f, 2.0f, COUNTER, v(0f, 0f, 1f));
			// foot: three toes with claws
			for (int k = -1; k <= 1; k++) {
				float yaw = k * 0.38f;
				float[] tb = v(foot[0] + k * 1.4f, foot[1] + 0.9f, foot[2]);
				float[] tt = v(tb[0] + Mth.sin(yaw) * 4.5f, foot[1] + 0.8f, tb[2] + Mth.cos(yaw) * 4.5f);
				loft(pose, new float[][] { tb, tt }, new float[] { 1.3f, 0.9f }, new float[] { 1.0f, 0.75f }, 1, 8, SKIN, 0, null);
				cone(pose, tt[0], tt[1], tt[2], Mth.sin(yaw), -0.4f, Mth.cos(yaw), 2.2f, 0.75f, 0.6f, CLAW);
			}
			ellipsoid(pose, foot[0], foot[1] + 1.2f, foot[2] - 0.5f, 2.3f, 1.4f, 2.8f, SKIN);
		}

		// ---- body, tail, neck (body space)
		Rig.push(pose, body);
		float tailSway = Mth.sin(t * 0.07f) * 0.05f + Mth.sin(ph) * 0.05f * amt;
		float[] yb = new float[5], pb = new float[5];
		for (int i = 0; i < 5; i++) {
			yb[i] = tailSway * (0.6f + i * 0.3f) - sweepDir * 0.16f * (i + 1) * 0.6f;
			pb[i] = -0.03f + (i == 0 ? 0.08f : 0f) - sweep * 0.02f;
		}
		float[][] tail = walk(v(0f, 1f, -5f), Mth.PI, 0.05f, 11f, yb, pb);
		float[][] spine = new float[tail.length - 1 + 5][];
		for (int i = 0; i < tail.length - 1; i++) {
			spine[i] = tail[tail.length - 1 - i];
		}
		int o = tail.length - 1;
		spine[o] = v(0f, 1f, -5f);
		spine[o + 1] = v(0f, 3.5f, 5f);
		spine[o + 2] = v(0f, 8.5f, 13f);
		spine[o + 3] = v(0f, 14f, 17.5f);
		spine[o + 4] = v(0f, 18f, 19.5f);
		float k = 1f + breathe;
		float[] w = { 0.5f, 2.6f, 4.8f, 7.2f, 9.6f, 11.5f * k, 12f * k, 9.5f, 6.8f, 6.2f };
		float[] h = { 0.5f, 3.0f, 5.6f, 8.2f, 11f, 13f * k, 13f * k, 10f, 7.4f, 6.6f };
		loft(pose, spine, w, h, 3, 16, STRIPED, 3, null);
		// back ridge bumps
		for (int i = o - 3; i < o + 3; i++) {
			for (int k2 = 0; k2 < 2; k2++) {
				float f = k2 * 0.5f;
				float[] q = lerp(spine[i], spine[i + 1], f);
				float hh = Mth.lerp(f, h[i], h[i + 1]);
				ellipsoid(pose, q[0], q[1] + hh * 0.93f, q[2], 0.9f, 1.0f, 1.6f, 8, 5, SKIN_DK, 0f, 1f);
			}
		}

		// tiny arms
		for (int side = -1; side <= 1; side += 2) {
			float wave = Mth.sin(t * 0.12f + side) * 0.15f + roar * 0.5f;
			float[] sh = v(side * 7.2f, 6.5f, 13.5f);
			float[] el = v(side * 8.4f, 3.5f - wave * 2f, 15.5f);
			float[] wr = v(side * 7.2f, 2.6f - wave, 19f);
			Rig.limb(pose, sh, el, wr, 1.6f, 1.2f, 0.9f, COUNTER, null);
			for (int c = 0; c < 2; c++) {
				cone(pose, wr[0] + (c == 0 ? -0.5f : 0.5f) * side, wr[1], wr[2], 0f, -0.6f, 1f, 2f, 0.4f, 0.4f, CLAW);
			}
		}

		// head
		float jaw = 0.06f + Mth.sin(t * 0.05f) * 0.03f;
		if (s.is("bite")) {
			float p = s.prog;
			jaw = p < 0.45f ? BeastPose.smooth(p / 0.45f) * 0.85f : Math.max(0.05f, 0.85f - (p - 0.45f) / 0.1f * 0.85f);
		}
		jaw = Math.max(jaw, roar * 1.0f);
		float hy = s.yaw(0.6f) * 0.8f - twist * 0.6f + roar * Mth.sin(t * 2.4f) * 0.05f;
		float hp = s.pitch(0.6f) * 0.5f - pitch * 0.6f + bite * 0.35f - roar * 0.6f;
		float lunge = s.is("bite") ? Mth.sin(Mth.clamp(s.prog / 0.6f, 0f, 1f) * Mth.PI) * 4f : 0f;
		boneYXZ(pose, 0f, 18f, 19.5f + lunge, hy, hp, 0f);
		head(pose, jaw);
		pose.popPose();
		pose.popPose();
	}

	/** The head (pivot at the top of the neck, +Z forward), jaw 0..1 open. Also used by the quick-morph roar. */
	public static void head(PoseStack pose, float jaw) {
		// cranium and the long snout (upper jaw)
		ellipsoid(pose, 0f, 4f, 4f, 7f, 6.6f, 8f, COUNTER_V);
		ellipsoid(pose, 0f, 3f, 12.5f, 5.6f, 4.6f, 9f, COUNTER_V);
		// brow ridges and cheek bulges
		for (int s = -1; s <= 1; s += 2) {
			ellipsoidR(pose, s * 4.4f, 8.2f, 8.8f, 2.2f, 1.3f, 3.4f, s * 0.2f, 0f, 0f, SKIN_DK);
			ellipsoid(pose, s * 5.6f, 2.2f, 5.5f, 2.6f, 3.4f, 4f, COUNTER_V);
			eye(pose, s * 5.2f, 6.4f, 8.6f, 1.55f, s * 0.95f, 0.05f, EYE);
			ellipsoid(pose, s * 1.7f, 6.3f, 20.6f, 0.75f, 0.5f, 0.7f, 8, 5, DARK, 0f, 1f);
		}
		// stripes over the crown
		for (int i = 0; i < 3; i++) {
			ellipsoid(pose, 0f, 9.8f - i * 0.4f, 1.5f + i * 3.6f, 3.8f - i * 0.5f, 1.2f, 1.1f, 10, 5, SKIN_DK, 0f, 0.6f);
		}
		// upper teeth along the jaw line
		for (int s = -1; s <= 1; s += 2) {
			for (int i = 0; i < 7; i++) {
				float z = 6f + i * 2.1f;
				float dz = (z - 12.5f) / 9f;
				float x = 4.6f * Mth.sqrt(Math.max(0.05f, 1f - dz * dz));
				cone(pose, s * x, -0.3f, z, 0f, -1f, 0.1f, i == 2 || i == 3 ? 2.6f : 2f, 0.5f, 0.55f, TOOTH);
			}
		}
		// the lower jaw, mouth and lower teeth
		bone(pose, 0f, 0.5f, 2f, jaw * 0.9f, 0f, 0f);
		ellipsoid(pose, 0f, -1.8f, 9.5f, 5.1f, 2.6f, 9.6f, COUNTER_V);
		ellipsoid(pose, 0f, 0.1f, 10f, 4.3f, 1.0f, 8.6f, 12, 6, MOUTH, 0f, 0.5f);
		ellipsoid(pose, 0f, 0.35f, 8f, 2.4f, 0.8f, 5f, 10, 6, MOUTH, 0f, 0.6f);
		for (int s = -1; s <= 1; s += 2) {
			for (int i = 0; i < 6; i++) {
				float z = 6.5f + i * 2.1f;
				float dz = (z - 9.5f) / 9.6f;
				float x = 4.3f * Mth.sqrt(Math.max(0.05f, 1f - dz * dz));
				cone(pose, s * x, 0.4f, z, 0f, 1f, 0.05f, 1.7f, 0.45f, 0.5f, TOOTH);
			}
		}
		pose.popPose();
		// the mouth's roof (shows when open)
		ellipsoid(pose, 0f, 0.2f, 10.5f, 4.2f, 0.8f, 8.4f, 12, 6, MOUTH, 0.5f, 1f);
	}
}
