package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's octopus (1 block wide): a big spotted mantle behind wide eyes, a siphon, eight curling tentacles with sucker
 * rows underneath. Crawls on its arms on land, trails them and pulses in water. Moves: grab (front arms reach and curl), ink (mantle
 * squeezes and a dark cloud bursts out behind), strike.
 */
public final class OctopusModel {
	private OctopusModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		boolean wet = s.inWater;
		float grab = Math.max(s.env("grab", 0.3f, 0.3f), s.env("strike", 0.2f, 0.3f));
		float ink = s.env("ink", 0.1f, 0.4f);
		float pulse = wet ? (Mth.sin(t * 0.2f) * 0.5f + 0.5f) : 0f;
		float squeeze = Math.max(ink * 0.25f, pulse * 0.08f);

		float bodyY = wet ? 6f + Mth.sin(t * 0.2f + 1f) * 0.8f : 3.6f + Mth.sin(t * 0.12f) * 0.25f - Math.abs(Mth.sin(s.walkPos * 0.6f)) * 0.4f * amt;
		pose.pushPose();
		pose.translate(0f, bodyY, 0f);

		// tentacles (8, round the base, the two front pairs do the grabbing)
		for (int i = 0; i < 8; i++) {
			float ang = (i + 0.5f) / 8f * Mth.TWO_PI; // 0 = forward (+Z), round toward +X
			float sx = Mth.sin(ang), sz = Mth.cos(ang);
			boolean front = sz > 0.3f;
			float[] base = v(sx * 2.6f, 0.5f, 0.5f + sz * 2.6f);
			int n = 9;
			float[] yb = new float[n], pb = new float[n];
			float wave = Mth.sin(t * 0.14f + i * 1.7f);
			float crawl = Mth.sin(s.walkPos * 0.6f + i * 0.9f) * amt;
			float startPitch;
			if (wet) {
				startPitch = 1.0f - pulse * 0.5f;
				for (int k = 0; k < n; k++) {
					yb[k] = wave * 0.05f;
					pb[k] = 0.05f + pulse * 0.02f;
				}
			} else {
				startPitch = 0.75f;
				for (int k = 0; k < n; k++) {
					yb[k] = wave * 0.06f + crawl * 0.05f;
					// down to the ground, along it, the tip curls up
					pb[k] = k < 2 ? 0.05f : (k < 5 ? -0.28f + crawl * 0.06f : -0.32f - k * 0.02f);
				}
			}
			float heading = ang;
			if (front && grab > 0f) {
				heading = Mth.lerp(grab, ang, ang * 0.35f);
				startPitch = Mth.lerp(grab, startPitch, -0.15f);
				for (int k = 0; k < n; k++) {
					float curl = s.prog > 0.5f ? (s.prog - 0.5f) * 2f : 0f;
					pb[k] = Mth.lerp(grab, pb[k], k > 4 ? 0.35f * curl + 0.03f : 0.02f);
				}
			}
			float[][] pts = walk(base, heading, startPitch, 2.1f, yb, pb);
			float[] w = new float[n + 1];
			for (int k = 0; k <= n; k++) {
				w[k] = Mth.lerp(k / (float) n, 1.7f, 0.25f);
			}
			loft(pose, pts, w, w, 2, 10, TENTACLE, 2, v(0f, 1f, 0f));
		}

		// head and mantle
		float sq = 1f - squeeze;
		ellipsoid(pose, 0f, 2.6f, 1f, 4.6f, 3.8f, 4.6f, SPOT_V);
		pose.pushPose();
		pose.translate(0f, 4.8f, -1.2f);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(-0.55f + (wet ? 0.5f : 0f) + Mth.sin(t * 0.1f) * 0.04f));
		ellipsoid(pose, 0f, 2.8f, -3.2f, 4.9f * sq, 5.2f * sq, 6.6f * (1f + squeeze * 0.4f), SPOT_V);
		pose.popPose();
		for (int sd = -1; sd <= 1; sd += 2) {
			ellipsoid(pose, sd * 3.0f, 5.6f, 2.4f, 1.9f, 1.7f, 1.9f, SKIN);
			eye(pose, sd * 3.3f, 6.0f, 3.2f, 1.25f, sd * 0.55f, 0.1f, EYE_SLIT);
		}
		// siphon on the left side
		loft(pose, new float[][] { { 3.8f, 2.4f, -0.6f }, { 5.2f, 2.0f, -1.6f }, { 5.8f, 1.6f, -2.6f } }, new float[] { 0.9f, 0.8f, 0.7f },
				new float[] { 0.9f, 0.8f, 0.7f }, 2, 8, SKIN_DK, 0, null);
		// ink cloud
		if (ink > 0f && s.is("ink")) {
			float p = s.prog;
			for (int k = 0; k < 6; k++) {
				float g = Mth.clamp(p * 3f - k * 0.25f, 0f, 1f);
				if (g <= 0f) {
					continue;
				}
				float r = (2f + k * 0.7f) * g * (1f - Math.max(0f, p - 0.75f) * 3.5f);
				if (r > 0.1f) {
					ellipsoid(pose, Mth.sin(k * 2.1f) * 3f, 3f + Mth.cos(k * 1.3f) * 2f, -8f - k * 2.2f * g, r, r * 0.85f, r, 10, 6, BLACK, 0f, 1f);
				}
			}
		}
		pose.popPose();
	}

	/** A single tentacle from {@code base} (for the quick-morph grab): heading yaw, curl 0..1 at the tip, reach 0..1. */
	public static void tentacle(PoseStack pose, float[] base, float yaw, float pitch, float reach, float curl, float t, int seed) {
		int n = 9;
		float[] yb = new float[n], pb = new float[n];
		for (int k = 0; k < n; k++) {
			yb[k] = Mth.sin(t * 0.2f + seed + k * 0.4f) * 0.06f;
			pb[k] = k > 4 ? 0.38f * curl : 0.02f;
		}
		float[][] pts = walk(base, yaw, pitch, 2.3f * reach, yb, pb);
		float[] w = new float[n + 1];
		for (int k = 0; k <= n; k++) {
			w[k] = Mth.lerp(k / (float) n, 1.8f, 0.25f) * Math.max(0.3f, reach);
		}
		loft(pose, pts, w, w, 2, 10, TENTACLE, 2, v(0f, 1f, 0f));
	}
}
