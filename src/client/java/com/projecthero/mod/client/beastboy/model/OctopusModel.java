package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's octopus, HQ round: a blocky octopus in his greens -- a spotted two-segment mantle behind the head, raised eye bumps
 * with gold eyes and bar pupils (they blink), a siphon on the left, and eight arms, each a chain of nine tapering boxes with sucker rows
 * underneath. In water it hovers with a jet pulse (mantle squeezes, the arms flare then snap together), arms rippling with waves that
 * run down them; swimming it tips mantle-first and streams its arms behind, and they trail the body's turns with a lag; on land it
 * sprawls and crawls on its arms with the mantle sagging back.
 * Moves: grab (front arms shoot out, coil, pull in), ink (squeeze, jet forward, a cloud bursts from the siphon), strike (one arm whips).
 */
public final class OctopusModel {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/beastboy/art/octopus.png");
	private static final int SEGS = 9;
	private static ModelPart root;
	private static Map<String, ModelPart> P;
	private static final ModelPart[][] ARMS = new ModelPart[8][SEGS];

	private OctopusModel() {
	}

	private static ModelPart p(String n) {
		return P.get(n);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, int tint, float flash, BeastPose s, SwimRig.State st) {
		if (root == null) {
			root = SeaShapes.octopus();
			P = SeaShapes.octopusParts(root);
			for (int k = 0; k < 8; k++) {
				for (int j = 0; j < SEGS; j++) {
					ARMS[k][j] = P.get("arm" + k + "_" + j);
				}
			}
		}
		SeaArt.reset(root);
		ModelPart ink = p("ink");
		animate(s, st);
		ink.visible = false;
		pose.pushPose();
		SeaArt.toModel(pose);
		SeaArt.draw(root, TEX, pose, buffers, light, tint, flash);
		if (s.is("ink")) {
			inkCloud(pose, buffers, light, tint, s.prog, st.time, ink);
		}
		pose.popPose();
	}

	/** Signed base angle of arm k (0 = straight ahead, + toward the left). */
	private static float ang(int k) {
		float a = (k + 0.5f) / 8f * Mth.TWO_PI;
		return a > Mth.PI ? a - Mth.TWO_PI : a;
	}

	static void animate(BeastPose s, SwimRig.State st) {
		float t = st.time;
		float wet = st.wetness, dry = 1f - wet;
		float spd = st.speed01();
		ModelPart head = p("head"), mantle = p("mantle"), mantle2 = p("mantle2");

		// jet pulse: 0 relaxed (arms flared, mantle full) .. 1 squeezed (arms snapped together)
		float rate = 2.4f + spd * 5f;
		float pp = t * rate;
		float pulse = Mth.sin(pp) * 0.5f + 0.5f;
		pulse = pulse * pulse * (3f - 2f * pulse);

		float grabP = s.is("grab") ? s.prog : -1f;
		float inkP = s.is("ink") ? s.prog : -1f;
		float strikeP = s.is("strike") ? s.prog : -1f;
		float squeeze = inkP >= 0f ? SeaArt.smooth(SeaArt.span(inkP, 0f, 0.15f)) * (1f - SeaArt.smooth(SeaArt.span(inkP, 0.35f, 0.7f))) : 0f;
		float jet = Math.max(pulse * wet * (0.35f + spd * 0.65f), squeeze);

		// body: hover and bob with the pulse; tip mantle-first when swimming; sit low on land
		float tilt = wet * (1.15f * spd + st.pitch * 0.3f * spd);
		head.xRot = tilt;
		head.yRot = -s.yaw(0.6f) * 0.3f;
		head.zRot = Mth.clamp(st.turn * 0.15f, -0.4f, 0.4f) * wet;
		head.y -= wet * (3.0f + Mth.sin(t * 1.3f) * 0.8f + jet * 1.4f) + dry * 1.6f;
		head.z -= squeeze * 4f;
		if (dry > 0.01f) {
			head.y -= dry * Math.abs(Mth.sin(s.walkPos * 0.7f)) * 0.5f * s.walkAmt;
		}

		// mantle: breathes, squeezes with the jet, sags back on land, its tip lags the pulse
		float breathe = Mth.sin(t * 1.8f) * 0.04f;
		float msq = 1f - jet * 0.14f - squeeze * 0.14f + breathe;
		mantle.xScale = msq;
		mantle.yScale = 1f - jet * 0.06f + breathe;
		mantle.zScale = 1f + jet * 0.08f;
		mantle.xRot = -dry * 0.35f + wet * (Mth.sin(t * 1.1f) * 0.05f - spd * 0.15f);
		mantle2.xRot = -dry * 0.25f + wet * (Mth.sin(t * 1.1f - 0.8f) * 0.08f - jet * 0.08f);
		mantle2.xScale = 1f - jet * 0.1f;

		ModelPart siphon = p("siphon");
		siphon.yRot = 0.25f * Mth.sin(t * 0.9f) - squeeze * 0.4f;
		siphon.xScale = siphon.yScale = 1f + jet * 0.25f + squeeze * 0.3f;

		float blink = SwimRig.blink(t, 4);
		for (String sd : new String[] { "L", "R" }) {
			ModelPart lid = p("lid" + sd);
			lid.yScale = Math.max(0.01f, blink);
			lid.visible = blink > 0.02f;
			p("eyeBump" + sd).yRot = -s.yaw(0.6f) * 0.25f;
		}

		// ---- arms
		float crawl = s.walkPos * 0.7f;
		float camt = dry * s.walkAmt;
		for (int k = 0; k < 8; k++) {
			float a = ang(k);
			float seed = k * 1.37f;
			ModelPart[] arm = ARMS[k];
			// water: droop and curl, flare with the pulse; swimming streams them back
			float spread = Mth.lerp(jet, 0.88f, 1.42f);
			spread = Mth.lerp(spd * wet, spread, 1.42f);
			// land: down to the ground, along it, tips curling up
			float rootX = Mth.lerp(dry, spread, 0.55f);
			float rootY = -a;
			float cphase = crawl + k * 0.79f;
			if (camt > 0.01f) {
				rootY += Mth.sin(cphase) * 0.32f * camt;
				rootX -= Math.max(0f, Mth.cos(cphase)) * 0.35f * camt;
			}
			// the arm trails the body's turns with a lag
			arm[0].xRot = rootX + Mth.sin(t * 1.7f + seed) * 0.05f;
			arm[0].yRot = rootY + Mth.sin(t * 1.3f + seed) * 0.06f * wet;
			for (int j = 1; j < SEGS; j++) {
				float f = j / (float) (SEGS - 1);
				float lagTurn = st.lagTurn(j * 0.055f);
				float wave = Mth.sin(t * 3.1f - j * 0.62f + seed) * (0.09f + 0.07f * f) * (1f - spd * 0.5f);
				float sway = Mth.sin(t * 2.2f - j * 0.5f + seed * 1.3f) * 0.08f * (1f - spd * 0.4f);
				float waterCurl = Mth.lerp(jet, -0.17f - 0.09f * f, -0.04f) * (1f - spd);
				float landCurl;
				if (j == 1) {
					landCurl = 0.3f;
				} else if (j == 2) {
					landCurl = -0.42f;
				} else {
					landCurl = j >= 6 ? -0.28f : -0.04f;
				}
				float x = Mth.lerp(dry, waterCurl + wave, landCurl + wave * 0.3f);
				float y = sway * Mth.lerp(dry, 1f, 0.4f) + Mth.clamp(lagTurn * 0.12f, -0.18f, 0.18f) * wet;
				if (camt > 0.01f && j >= 6) {
					x -= Math.max(0f, Mth.cos(cphase - 0.6f)) * 0.15f * camt;
				}
				arm[j].xRot = x;
				arm[j].yRot = y;
			}
			// moves
			boolean front = Math.abs(a) < 1.3f;
			if (grabP >= 0f && front) {
				float wgt = Math.abs(a) < 0.6f ? 1f : 0.75f;
				float reach = SeaArt.smooth(SeaArt.span(grabP, 0f, 0.28f)) * (1f - SeaArt.smooth(SeaArt.span(grabP, 0.72f, 1f))) * wgt;
				float coil = SeaArt.smooth(SeaArt.span(grabP, 0.28f, 0.52f)) * (1f - SeaArt.smooth(SeaArt.span(grabP, 0.82f, 1f)));
				float pull = SeaArt.smooth(SeaArt.span(grabP, 0.52f, 0.75f)) * (1f - SeaArt.smooth(SeaArt.span(grabP, 0.85f, 1f)));
				arm[0].yRot = Mth.lerp(reach, arm[0].yRot, -a * 0.4f);
				arm[0].xRot = Mth.lerp(reach, arm[0].xRot, -0.2f + pull * 0.5f);
				for (int j = 1; j < SEGS; j++) {
					float target = j >= 5 ? 0.62f * coil : 0.02f + pull * 0.1f;
					arm[j].xRot = Mth.lerp(reach, arm[j].xRot, target);
					arm[j].yRot = Mth.lerp(reach, arm[j].yRot, 0f);
				}
			}
			if (strikeP >= 0f && k == 0) {
				float whip = Mth.sin(SeaArt.span(strikeP, 0f, 0.7f) * Mth.PI);
				arm[0].xRot = Mth.lerp(whip, arm[0].xRot, -0.5f);
				arm[0].yRot = Mth.lerp(whip, arm[0].yRot, -a * 0.2f);
				for (int j = 1; j < SEGS; j++) {
					arm[j].xRot = Mth.lerp(whip, arm[j].xRot, Mth.sin(strikeP * 9f - j * 0.7f) * 0.25f);
				}
			}
			if (squeeze > 0f) {
				arm[0].xRot = Mth.lerp(squeeze, arm[0].xRot, 1.45f);
				for (int j = 1; j < SEGS; j++) {
					arm[j].xRot = Mth.lerp(squeeze, arm[j].xRot, 0.02f);
				}
			}
		}
	}

	/** The ink cloud: puffs shot out of the siphon, drifting back and swelling, then thinning out. Model space. */
	private static void inkCloud(PoseStack pose, MultiBufferSource buffers, int light, int tint, float p, float t, ModelPart ink) {
		for (int i = 0; i < 16; i++) {
			float born = 0.04f + i * 0.026f;
			float age = p - born;
			if (age <= 0f) {
				continue;
			}
			float r1 = Mth.sin(i * 2.3f), r2 = Mth.cos(i * 1.7f);
			float dist = 6f + age * 34f;
			float x = 5f + r1 * age * 10f;
			float y = 18.5f - age * 6f + r2 * age * 8f;
			float z = dist;
			float sc = 0.25f + age * 1.6f + (i % 3) * 0.12f;
			float alpha = (1f - SeaArt.span(p, 0.55f, 1f)) * Math.min(1f, age * 8f) * 0.5f;
			SeaArt.puff(ink, TEX, pose, buffers, light, x, y, z, sc, i * 1.1f + t * 0.5f, alpha, tint);
		}
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
