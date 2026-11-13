package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's quick-morph partials, drawn on the human player for a split second. Space: feet origin, pixels, +Y up, +Z the
 * player's front, +X the player's left (the caller converts from the player model root). {@code e} = grow-in / hold / shrink-out scale.
 */
public final class QuickMorphs {
	private QuickMorphs() {
	}

	public static float envelope(float p) {
		return BeastPose.envelope(p, 0.18f, 0.2f);
	}

	/** Gorilla Smash: huge furry arms out of the shoulders, raised overhead then slammed down in front. */
	public static void gorillaArms(PoseStack pose, float p, float t) {
		float e = envelope(p);
		if (e <= 0.01f) {
			return;
		}
		for (int side = -1; side <= 1; side += 2) {
			float[] sh = v(side * 6f, 21.5f, 0f);
			float[] up = v(side * 5f, 40f, 2f), down = v(side * 4.5f, 1.5f, 15f), rest = v(side * 11f, 6f, 4f);
			float[] hand;
			if (p < 0.45f) {
				hand = lerp(rest, up, BeastPose.smooth(p / 0.45f));
			} else if (p < 0.6f) {
				hand = lerp(up, down, BeastPose.smooth((p - 0.45f) / 0.15f));
			} else {
				hand = down;
			}
			hand = add(sh, mul(sub(hand, sh), Math.max(0.35f, e)));
			float[] elbow = ik(sh, hand, 10.5f * e, 10f * e, v(side * 0.6f, 0f, -1f));
			ellipsoid(pose, sh[0] + side * 0.5f, sh[1] + 0.5f, sh[2], 4.6f * e, 4.4f * e, 4.6f * e, FUR);
			Rig.limb(pose, sh, elbow, hand, 3.8f * e, 3.4f * e, 2.6f * e, FUR, null);
			float[] fm = lerp(elbow, hand, 0.35f);
			ellipsoid(pose, fm[0], fm[1], fm[2], 3.4f * e, 3.4f * e, 3.4f * e, 10, 7, FUR, 0f, 1f);
			ellipsoid(pose, hand[0], hand[1], hand[2], 2.7f * e, 2.1f * e, 2.8f * e, FACE);
			for (int k = 0; k < 4; k++) {
				ellipsoid(pose, hand[0] + (k - 1.5f) * 1.1f * e, hand[1] - 1.2f * e, hand[2] + 1.6f * e, 0.65f * e, 0.7f * e, 0.75f * e, 8, 5, FACE, 0f, 1f);
			}
		}
	}

	/** Ram: a rhino head with the horn over the player's head, lowered to charge. */
	public static void rhinoHead(PoseStack pose, float p, float t) {
		float e = envelope(p);
		if (e <= 0.01f) {
			return;
		}
		pose.pushPose();
		pose.translate(0f, 29f, -1.5f);
		pose.scale(e, e, e);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(0.3f + Mth.sin(p * Mth.PI) * 0.15f));
		// a short neck of hide behind it
		ellipsoid(pose, 0f, -1f, 0f, 6f, 6.5f, 5f, WRINKLE_V);
		RhinoModel.head(pose, t);
		pose.popPose();
	}

	/** T-Rex Roar: a T-Rex head over the player's head, jaws wide. */
	public static void trexHead(PoseStack pose, float p, float t) {
		float e = envelope(p);
		if (e <= 0.01f) {
			return;
		}
		pose.pushPose();
		pose.translate(0f, 27.5f, -3.5f);
		pose.scale(e * 0.85f, e * 0.85f, e * 0.85f);
		pose.mulPose(com.mojang.math.Axis.YP.rotation(Mth.sin(t * 2.2f) * 0.05f));
		pose.mulPose(com.mojang.math.Axis.XP.rotation(-0.35f));
		float jaw = BeastPose.smooth(Mth.clamp((p - 0.1f) / 0.2f, 0f, 1f)) * (p > 0.85f ? Math.max(0f, 1f - (p - 0.85f) / 0.15f) : 1f);
		TRexModel.quickHead(pose, jaw, t); // HQ round: blocky head on its own atlas
		pose.popPose();
	}

	/** Snake Strike: the whole snake, bigger than life, lunging forward. */
	public static void snake(PoseStack pose, float p, float t) {
		float e = envelope(p);
		if (e <= 0.01f) {
			return;
		}
		BeastPose s = new BeastPose();
		s.age = t;
		s.move = "strike";
		s.prog = Mth.clamp((p - 0.15f) / 0.7f, 0f, 1f);
		pose.pushPose();
		pose.scale(2.4f * e, 2.4f * e, 2.4f * e);
		SnakeModel.draw(pose, s); // HQ round: own class
		pose.popPose();
	}

	/** Elephant Stomp: elephant legs (one stomps) and a trunk with ears on the face. */
	public static void elephant(PoseStack pose, float p, float t) {
		float e = envelope(p);
		if (e <= 0.01f) {
			return;
		}
		float lift = p < 0.5f ? BeastPose.smooth(Mth.clamp((p - 0.1f) / 0.4f, 0f, 1f)) * 9f : Math.max(0f, 9f * (1f - (p - 0.5f) / 0.07f));
		for (int side = -1; side <= 1; side += 2) {
			float[] hip = v(side * 2.4f, 12.5f, 0f);
			float l = side < 0 ? lift : 0f;
			float[] foot = v(side * 3.2f, 2.4f * e + l, l * 0.4f);
			float[] knee = ik(hip, foot, 6f, 6f, v(0f, 0f, 1f));
			Rig.limb(pose, hip, knee, foot, 4.2f * e, 3.6f * e, 3.6f * e, WRINKLE, v(0f, 0f, 1f));
			taper(pose, foot[0], foot[1] - 2.4f * e, foot[2], 4.2f * e, 4.2f * e, 2.6f * e, 3.8f * e, 3.8f * e, 14, WRINKLE_V, true);
			for (int k = -1; k <= 1; k++) {
				ellipsoid(pose, foot[0] + k * 1.8f * e, foot[1] - 1.5f * e, foot[2] + 3.6f * e, 1f * e, 0.8f * e, 0.7f * e, 8, 5, HORN, 0f, 1f);
			}
		}
		pose.pushPose();
		pose.translate(0f, 27.5f, 3.6f);
		pose.scale(0.55f * e, 0.55f * e, 0.55f * e);
		pose.translate(0f, 2.5f, -11.5f);
		ElephantModel.trunk(pose, t, 0f, p < 0.5f ? BeastPose.smooth(p / 0.5f) : 1f - BeastPose.smooth((p - 0.5f) / 0.5f), 0f, 0f);
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, 26.5f, -1.5f);
		for (int side = -1; side <= 1; side += 2) {
			ElephantModel.ear(pose, side, Mth.sin(t * 0.3f) * 0.2f, 0.62f * e);
		}
		pose.popPose();
	}

	/** Octopus Grab: four tentacles out of the back that reach round to the front and curl. */
	public static void tentacles(PoseStack pose, float p, float t) {
		float e = envelope(p);
		if (e <= 0.01f) {
			return;
		}
		float grab = BeastPose.smooth(Mth.clamp((p - 0.2f) / 0.35f, 0f, 1f));
		for (int i = 0; i < 4; i++) {
			int side = i % 2 == 0 ? 1 : -1;
			boolean upper = i < 2;
			float[] base = v(side * 2.4f, upper ? 21f : 15.5f, -2.5f);
			float yaw = side * Mth.lerp(grab, 2.3f, 1.2f);
			float pitch = upper ? -0.35f + grab * 0.2f : 0.15f;
			ellipsoid(pose, base[0], base[1], base[2] - 0.4f, 1.9f * e, 1.9f * e, 1.4f * e, 10, 6, SKIN, 0f, 1f);
			OctopusModel.tentacle(pose, base, yaw, pitch, e, 0.2f + grab * 0.8f, t, i * 3);
		}
	}
}
