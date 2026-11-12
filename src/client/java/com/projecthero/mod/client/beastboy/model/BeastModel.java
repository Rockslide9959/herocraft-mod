package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 the Beast ("The Beast Within"): a hulking, hunched green wolf-ape about 2.6 blocks tall. Huge shoulders under a shaggy dark
 * mane, long arms hanging to the knees with big claws, digitigrade wolf legs, a long-muzzled snarling head with pointed ears and fangs.
 * Moves: swipe / strike (one-arm slash), roar, pounce (crouch then leap stretched out), smash (both fists overhead, down).
 */
public final class BeastModel {
	private BeastModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		float ph = s.walkPos * 0.55f;
		float roar = s.env("roar", 0.15f, 0.25f);
		float swipe = s.isAny("swipe", "strike") ? s.prog : -1f;
		float pounce = s.is("pounce") ? s.prog : -1f;
		float smash = s.is("smash") ? s.prog : -1f;

		// crouch / stretch for the pounce
		float crouch = 0f, stretch = 0f;
		if (pounce >= 0f) {
			crouch = pounce < 0.3f ? BeastPose.smooth(pounce / 0.3f) : Math.max(0f, 1f - (pounce - 0.3f) / 0.1f);
			stretch = pounce < 0.3f ? 0f : (pounce < 0.8f ? BeastPose.smooth((pounce - 0.3f) / 0.15f) : 1f - BeastPose.smooth((pounce - 0.8f) / 0.2f));
		}
		float rear = roar;
		if (smash >= 0f) {
			rear = smash < 0.45f ? BeastPose.smooth(smash / 0.45f) : -0.4f * (1f - BeastPose.smooth((smash - 0.45f) / 0.55f));
		}
		float breathe = Mth.sin(t * 0.13f);
		float bob = -Math.abs(Mth.sin(ph)) * 1.4f * amt + breathe * 0.35f - crouch * 5f + stretch * 4f;
		float pitch = 0.62f + 0.12f * amt - rear * 0.55f + crouch * 0.3f + stretch * 0.6f + (swipe >= 0 ? Mth.sin(swipe * Mth.PI) * 0.15f : 0f);
		float twist = Mth.sin(ph) * 0.1f * amt + (swipe >= 0f ? Mth.lerp(BeastPose.smooth(swipe), 0.45f, -0.5f) * Mth.sin(swipe * Mth.PI) : 0f);
		Matrix4f body = Rig.mat(0f, 19f + bob, -1f, twist, pitch, Mth.sin(ph) * 0.05f * amt);

		// ---- digitigrade legs
		for (int side = -1; side <= 1; side += 2) {
			float[] st = Rig.step(ph + (side > 0 ? 0f : Mth.PI), 6f * amt, 4f * amt);
			float[] hip = Rig.tp(body, side * 4.6f, -0.5f, -0.5f);
			float[] foot = v(side * 5.2f, 0.4f + st[1], st[0] + crouch * 1.5f);
			if (stretch > 0f) {
				foot = lerp(foot, v(side * 4.5f, 6f, -14f), stretch);
			}
			float[] ankle = add(foot, v(0f, 6.2f - crouch * 1.5f, -2.8f + crouch * 0.5f));
			if (stretch > 0f) {
				ankle = lerp(ankle, add(foot, v(0f, 3f, 5f)), stretch);
			}
			float[] knee = ik(hip, ankle, 9.8f, 9f, v(0f, 0.1f, 1f));
			float[] th = lerp(hip, knee, 0.4f);
			ellipsoid(pose, th[0], th[1], th[2], 3.8f, 4.4f, 4.2f, FUR);
			Rig.limb4(pose, hip, knee, ankle, foot, 4.2f, 2.8f, 1.8f, 1.6f, FUR, v(0f, 0f, 1f));
			// paw with claws
			ellipsoid(pose, foot[0], foot[1] + 0.6f, foot[2] + 1.2f, 2f, 1.1f, 2.8f, FUR_LT);
			for (int k = -1; k <= 1; k++) {
				cone(pose, foot[0] + k * 1.0f, foot[1] + 0.5f, foot[2] + 3.6f, k * 0.2f, -0.5f, 1f, 1.6f, 0.4f, 0.4f, CLAW);
			}
		}

		// ---- long arms
		for (int side = -1; side <= 1; side += 2) {
			float[] sh = Rig.tp(body, side * 9f, 15.5f, 0.5f);
			float swing = Mth.sin(ph + (side > 0 ? Mth.PI : 0f)) * 5f * amt;
			float[] hand = v(side * 10.5f, 5.5f + breathe * 0.3f - crouch * 4f, 8f + swing);
			if (crouch > 0f) {
				hand = lerp(hand, v(side * 8f, 1.4f, 10f), crouch);
			}
			if (stretch > 0f) {
				hand = lerp(hand, add(sh, v(side * -2f, 2f, 20f)), stretch);
			}
			if (roar > 0f) {
				hand = lerp(hand, add(sh, v(side * 15f, 6f, 6f)), roar);
			}
			if (smash >= 0f) {
				float[] up = add(sh, v(side * -4f, 19f, -1f)), down = v(side * 5f, 1.5f, 15f);
				hand = smash < 0.45f ? lerp(hand, up, BeastPose.smooth(smash / 0.45f))
						: (smash < 0.58f ? lerp(up, down, BeastPose.smooth((smash - 0.45f) / 0.13f)) : lerp(down, hand, BeastPose.smooth((smash - 0.58f) / 0.42f)));
			}
			if (swipe >= 0f && side < 0) {
				// the right arm slashes from out-back, across the front
				float k = BeastPose.smooth(swipe);
				float a = Mth.lerp(k, -2.2f, 0.9f);
				float env = Mth.sin(swipe * Mth.PI);
				float[] arc = add(sh, v(Mth.sin(a) * 16f, -2f - env * 2f, Mth.cos(a) * 16f));
				hand = lerp(hand, arc, Math.min(1f, env * 1.6f));
			}
			float[] elbow = ik(sh, hand, 11f, 10.5f, v(side * 0.5f, -0.2f, -1f));
			Rig.limb(pose, sh, elbow, hand, 4f, 3.4f, 2.6f, FUR, null);
			float[] fm = lerp(elbow, hand, 0.3f);
			ellipsoid(pose, fm[0], fm[1], fm[2], 3.2f, 3.2f, 3.2f, 10, 7, FUR, 0f, 1f);
			// hand and claws (curled toward the front)
			float[] fd = norm(sub(hand, elbow), v(0f, -1f, 0f));
			ellipsoid(pose, hand[0], hand[1], hand[2], 2.4f, 2.4f, 2.4f, FUR_LT);
			for (int k = 0; k < 4; k++) {
				float off = (k - 1.5f) * 1.1f;
				float[] base = add(hand, v(off * (fd[2] > 0.5f ? 1f : 0.7f), -0.6f, 0.6f + Math.abs(off) * -0.2f));
				float[] d1 = add(fd, v(0f, 0f, 0.8f));
				cone(pose, base[0] + fd[0] * 1.6f, base[1] + fd[1] * 1.6f, base[2] + fd[2] * 1.6f, d1[0], d1[1], d1[2], 4.0f, 0.55f, 0.45f, CLAW);
			}
		}

		// ---- torso
		Rig.push(pose, body);
		float br = 1f + breathe * 0.035f + roar * 0.05f;
		ellipsoid(pose, 0f, 1.2f, -0.5f, 5.6f, 4.8f, 4.6f, FUR);
		ellipsoid(pose, 0f, 7f, 0.8f, 6.4f * br, 6.2f, 5.2f * br, FUR);
		ellipsoid(pose, 0f, 7.5f, 3.6f, 4.2f, 5f, 2.8f, FUR_LT);
		ellipsoid(pose, 0f, 13.5f, 0.8f, 9f * br, 7f, 6.6f * br, FUR);
		ellipsoid(pose, 0f, 13f, 4.6f, 6f, 5.4f, 3.2f, FUR_LT);
		for (int side = -1; side <= 1; side += 2) {
			ellipsoid(pose, side * 7.6f, 16f, 0f, 4.6f, 4.4f, 4.8f, FUR);
			ellipsoid(pose, side * 2.6f, 10f, 5.4f, 2.4f, 1.8f, 1.4f, FUR_LT);
		}
		// the mane: a shaggy collar and tufts down the back and over the shoulders
		ellipsoid(pose, 0f, 18.2f, -0.5f, 8.4f, 5f, 6.6f, MANE);
		for (int i = 0; i < 9; i++) {
			float y = 8f + i * 1.5f;
			float zz = -4.8f - Mth.sin(i * 0.4f) * 1.4f;
			cone(pose, 0f, y, zz + 1f, 0f, 0.4f, -1f, 3.2f + (i % 3) * 0.8f, 1.6f, 1.2f, MANE);
			if (i > 3) {
				for (int side = -1; side <= 1; side += 2) {
					cone(pose, side * (3.5f + (i - 3) * 0.5f), y + 1f, zz + 2f, side * 0.6f, 0.5f, -0.8f, 2.6f + (i % 2), 1.3f, 1.0f, MANE);
				}
			}
		}
		for (int k = 0; k < 10; k++) {
			float a = (k / 9f - 0.5f) * 3.6f;
			cone(pose, Mth.sin(a) * 7.6f, 19.5f + Mth.cos(k * 1.7f) * 0.8f, Mth.cos(a) * 4.5f - 1.2f, Mth.sin(a), 0.7f, Mth.cos(a) * 0.4f - 0.5f, 3.8f, 1.6f, 1.2f, MANE);
		}

		// head (level, snarling)
		float hy = s.yaw(0.7f) * 0.8f - twist * 0.6f;
		float hp = -pitch + 0.25f + s.pitch(0.5f) * 0.5f - roar * 0.5f + crouch * 0.2f - stretch * 0.1f;
		boneYXZ(pose, 0f, 20f, 5f, hy, hp, 0f);
		float jaw = Math.max(roar * (0.85f + Mth.sin(t * 1.3f) * 0.08f), 0.12f + Math.max(0f, Mth.sin(t * 0.05f)) * 0.06f);
		if (swipe >= 0f || smash >= 0f || stretch > 0f) {
			jaw = Math.max(jaw, 0.5f);
		}
		head(pose, t, jaw, roar);
		pose.popPose();
		pose.popPose();
	}

	public static void head(PoseStack pose, float t, float jaw, float roar) {
		ellipsoid(pose, 0f, 1.8f, 0f, 4.2f, 4.1f, 4.4f, FUR);
		// heavy brow and the long muzzle
		ellipsoid(pose, 0f, 3.2f, 2.8f, 3.9f, 1.3f, 2.1f, SKIN_DK);
		ellipsoid(pose, 0f, 0.6f, 5.2f, 2.8f, 2.3f, 4.2f, FUR_LT);
		ellipsoid(pose, 0f, 1.6f, 5.6f, 1.8f, 1.2f, 3.6f, FUR_LT);
		ellipsoid(pose, 0f, 1.6f, 9.0f, 0.95f, 0.7f, 0.6f, DARK);
		// snarl wrinkles on the muzzle
		for (int i = 0; i < 3; i++) {
			ellipsoid(pose, 0f, 2.6f - i * 0.1f, 4.4f + i * 1.3f, 1.6f - i * 0.2f, 0.4f, 0.35f, 8, 4, SKIN_DK, 0f, 0.6f);
		}
		for (int s = -1; s <= 1; s += 2) {
			eye(pose, s * 2.0f, 2.4f, 3.5f, 0.85f, s * 0.35f, 0.1f, EYE_FIERCE);
			// pointed ears, laid back when roaring
			float back = roar * 0.5f;
			cone(pose, s * 2.8f, 4.6f, -0.8f, s * 0.45f, 1f, -0.25f - back, 4.8f, 1.4f, 0.8f, FUR);
			cone(pose, s * 2.95f, 4.7f, -0.55f, s * 0.45f, 1f, -0.25f - back, 3.6f, 0.8f, 0.3f, INNER_EAR);
			// cheek ruff
			cone(pose, s * 3.4f, 0.4f, 0.6f, s * 1f, -0.3f, -0.8f, 3.2f, 1.2f, 1.6f, MANE);
			cone(pose, s * 3.2f, -1.2f, 1.2f, s * 0.8f, -0.8f, -0.6f, 2.8f, 1.1f, 1.3f, MANE);
		}
		// upper fangs
		for (int s = -1; s <= 1; s += 2) {
			cone(pose, s * 1.7f, -0.9f, 7.4f, 0f, -1f, 0.1f, 2.0f, 0.42f, 0.42f, TOOTH);
			cone(pose, s * 1.9f, -0.8f, 5.6f, 0f, -1f, 0f, 1.0f, 0.3f, 0.3f, TOOTH);
			cone(pose, s * 2.0f, -0.8f, 4.3f, 0f, -1f, 0f, 0.8f, 0.28f, 0.28f, TOOTH);
		}
		ellipsoid(pose, 0f, -0.9f, 5.4f, 2.2f, 0.6f, 3.4f, 10, 4, MOUTH, 0.5f, 1f);
		bone(pose, 0f, -1f, 1.6f, jaw * 0.75f, 0f, 0f);
		ellipsoid(pose, 0f, -1.3f, 4f, 2.3f, 1.2f, 3.8f, FUR_LT);
		ellipsoid(pose, 0f, -0.2f, 4.2f, 2.0f, 0.55f, 3.4f, 10, 4, MOUTH, 0f, 0.5f);
		for (int s = -1; s <= 1; s += 2) {
			cone(pose, s * 1.6f, -0.2f, 6.6f, 0f, 1f, -0.1f, 1.6f, 0.38f, 0.38f, TOOTH);
			cone(pose, s * 1.8f, -0.2f, 4.6f, 0f, 1f, 0f, 0.8f, 0.28f, 0.28f, TOOTH);
		}
		pose.popPose();
	}
}
