package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.Blocky.bone;
import static com.projecthero.mod.client.beastboy.model.Blocky.box;
import static com.projecthero.mod.client.beastboy.model.FlyUv.*;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's fly, HQ round (moved out of SmallModels): a blocky house fly on {@code fly.png} ({@link FlyUv}), built big and
 * scaled down: a striped metallic thorax with bristles, a banded abdomen, a head with a pale face plate, two huge faceted compound eyes,
 * antennae and a sponge-tipped proboscis; see-through veined wings, halteres; six legs of three thin segments each.
 *
 * <p>Flying: the wings buzz as a blur of three see-through strokes, the body hovers with a nervous jitter and darts, legs dangle. Landed:
 * the wings fold back in a V over the abdomen, the legs stand splayed, the front legs rub together now and then, the head twitches.
 * It has no moves (it can't attack).
 */
public final class FlyModel {
	private static final float K = 0.2f;

	private FlyModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		BeastMesh.texture(TEX);
		MotionState st = MotionState.of(s, 6);
		boolean airT = s.airborne || s.inWater;
		st.air = st.ease(st.air, airT ? 1f : 0f, airT ? 0.4f : 0.2f);
		pose.pushPose();
		Blocky.scale(pose, K);
		float t = s.age;
		float air = st.air, gnd = 1f - air;
		float amt = s.walkAmt;

		// hover jitter and darting
		float jx = AnimKit.wobble(t * 1.3f, 0.7f) * 1.6f * air, jy = AnimKit.wobble(t * 1.1f, 2.9f) * 1.4f * air, jz = AnimKit.wobble(t * 0.9f, 5.3f) * 1.2f * air;
		float bodyY = Mth.lerp(air, 7.2f, 16f) + jy;
		float bodyPitch = Mth.lerp(air, 0f, -0.12f + amt * 0.25f) + AnimKit.wobble(t, 9f) * 0.05f * air;
		float walk = s.walkPos * 2.2f;
		bone(pose, jx, bodyY + gnd * Math.abs(Mth.sin(walk)) * 0.3f * amt, jz, AnimKit.wobble(t * 0.7f, 3f) * 0.12f * air, bodyPitch,
				AnimKit.wobble(t * 1.2f, 7f) * 0.08f * air);
		box(pose, THORAX, -3f, -3f, -3f);
		// bristles on the thorax
		for (int i = 0; i < 6; i++) {
			float bx = (i % 3 - 1) * 2f, bz = i < 3 ? 1.6f : -1.4f;
			bone(pose, bx, 2.8f, bz, 0f, -0.5f, (i % 3 - 1) * 0.5f);
			box(pose, BRISTLE, -0.2f, 0f, -0.2f, 0.4f, 1.6f, 0.4f, false);
			pose.popPose();
		}
		// abdomen (breathing pulse)
		float pulse = 1f + Mth.sin(t * 0.3f) * 0.04f;
		bone(pose, 0f, -0.6f, -2.4f, 0f, 0.18f + gnd * 0.05f, 0f);
		pose.scale(pulse, pulse, 1f);
		box(pose, ABDOMEN, -3f, -2.5f, -8f);
		box(pose, ABD_TIP, -2f, -2f, -10.5f);
		pose.popPose();

		// head: twitchy discrete turns
		float tgt = s.yaw(1.0f) * 0.7f + gnd * (Mth.sin(t * 0.13f) > 0.7f ? 0.35f : 0f) * Mth.sign(Mth.sin(t * 0.021f));
		if (st.fresh || Math.abs(tgt - st.f[0]) > 0.18f) {
			st.f[0] = tgt;
		}
		st.headYaw = st.easeAngle(st.headYaw, st.f[0], 0.8f);
		bone(pose, 0f, 0.5f, 2.8f, st.headYaw, s.pitch(0.6f) * 0.4f, 0f);
		box(pose, HEAD, -3f, -2.5f, 0f);
		box(pose, EYE, 1.6f, -2f, 0.3f);
		box(pose, EYE, -4.6f, -2f, 0.3f, 3f, 4f, 4f, true);
		for (int sd = -1; sd <= 1; sd += 2) {
			bone(pose, sd * 0.8f, 1.6f, 3.8f, 0f, -0.6f, sd * 0.3f);
			box(pose, ANTENNA, -0.4f, 0f, -0.4f, 0.8f, 1.6f, 0.8f, false);
			pose.popPose();
		}
		float dab = gnd * Math.max(0f, Mth.sin(t * 0.17f)) * 0.5f;
		bone(pose, 0f, -2.2f, 2.6f, 0f, 0.35f - dab, 0f);
		box(pose, PROBOSCIS, -0.5f, -3f, -0.5f);
		box(pose, PAD, -1f, -3.8f, -1f);
		pose.popPose();
		pose.popPose();

		// halteres
		for (int sd = -1; sd <= 1; sd += 2) {
			bone(pose, sd * 2.2f, 1.6f, -2.8f, 0f, 0f, sd * (0.6f + Mth.sin(t * 7f) * 0.5f * air));
			box(pose, LEGSEG, -0.2f, 0f, -0.2f, 0.4f, 1.2f, 0.4f, false);
			box(pose, HALTERE, -0.45f, 1.1f, -0.45f, 0.9f, 0.9f, 0.9f, false);
			pose.popPose();
		}

		// legs: femur out and up, tibia down, tarsus to the ground (dangling in the air)
		for (int sd = -1; sd <= 1; sd += 2) {
			for (int k = 0; k < 3; k++) {
				float z = 1.6f - k * 1.6f;
				float step = gnd * Mth.sin(walk + k * 2.1f + (sd > 0 ? 0f : Mth.PI)) * 0.25f * amt;
				boolean front = k == 0;
				float rub = front ? gnd * (1f - amt) * Math.max(0f, Mth.sin(t * 0.05f)) : 0f; // grooming
				float rubWave = Mth.sin(t * 1.4f) * 0.25f * rub;
				float yaw = sd * (0.5f - k * 0.5f) + step - rub * sd * 0.5f;
				float femR = Mth.lerp(air, 1.15f, 0.55f) - rub * 0.6f;
				bone(pose, sd * 2.4f, -2.4f, z, yaw, rub * -0.9f, sd * femR);
				box(pose, LEGSEG, -0.35f, -4f, -0.35f, 0.7f, 4f, 0.7f, false);
				bone(pose, 0f, -3.8f, 0f, 0f, rubWave, -sd * Mth.lerp(air, 1.85f, 0.9f) + sd * rub * 0.6f);
				box(pose, LEGSEG, -0.3f, -4.2f, -0.3f, 0.6f, 4.2f, 0.6f, false);
				bone(pose, 0f, -4f, 0f, 0f, 0f, sd * Mth.lerp(air, 0.55f, 0.3f));
				box(pose, LEGSEG, -0.25f, -3f, -0.25f, 0.5f, 3f, 0.5f, false);
				box(pose, BRISTLE, -0.45f, -3.3f, -0.45f, 0.9f, 0.5f, 0.9f, false);
				pose.popPose();
				pose.popPose();
				pose.popPose();
			}
		}

		// wings: a three-stroke blur when flying, folded back in a V when landed
		for (int sd = -1; sd <= 1; sd += 2) {
			pose.pushPose();
			pose.scale(sd, 1f, 1f);
			if (air > 0.05f) {
				for (int c = 0; c < 3; c++) {
					float ph = t * 4.2f + c * 2.09f;
					float stroke = Mth.sin(ph);
					float yaw = Mth.lerp(air, 1.25f, 0.45f + stroke * 0.7f);
					float roll = Mth.lerp(air, 0.12f, 0.25f + Mth.cos(ph) * 0.35f);
					bone(pose, 1.4f, 2.9f, 0.6f, yaw, 0f, roll);
					Blocky.plane(pose, WING, 0f, 0.02f * c, -2.5f, 11f, 5f, false);
					pose.popPose();
				}
			} else {
				bone(pose, 1.2f, sd > 0 ? 3.05f : 3.1f, 0.6f, 1.22f, 0f, 0.1f);
				Blocky.plane(pose, WING, 0f, 0f, -2.5f, 11f, 5f, false);
				pose.popPose();
			}
			pose.popPose();
		}

		pose.popPose(); // body
		pose.popPose(); // scale
	}
}
