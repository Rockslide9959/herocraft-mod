package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's gorilla (2 blocks tall knuckle-walking): huge shoulders and forearms, short legs, dark green fur with a lighter
 * chest, face, hands and feet. Moves: smash, grab / throw, punch / strike, roar / chest_beat.
 */
public final class GorillaModel {
	private GorillaModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		float ph = s.walkPos * 0.55f;
		float smash = s.is("smash") ? s.prog : -1f;
		float beat = s.isAny("roar", "chest_beat", "beat") ? s.env(s.move, 0.15f, 0.2f) : 0f;
		float grabK = s.isAny("grab", "throw") ? s.env(s.move, 0.3f, 0.3f) : 0f;
		float punch = s.isAny("strike", "punch") ? Mth.sin(s.prog * Mth.PI) : 0f;

		// stance: knuckle-walk (pitched forward) -> upright for smash / chest beat
		float rear = beat;
		float slamPitch = 0f;
		if (smash >= 0f) {
			if (smash < 0.45f) {
				rear = BeastPose.smooth(smash / 0.45f);
			} else if (smash < 0.6f) {
				rear = 1f - BeastPose.smooth((smash - 0.45f) / 0.15f) * 1.3f;
				slamPitch = 0f;
			} else {
				rear = -0.3f * (1f - BeastPose.smooth((smash - 0.6f) / 0.4f));
			}
		}
		float bob = -Math.abs(Mth.sin(ph)) * 1.0f * amt + Mth.sin(t * 0.1f) * 0.25f;
		float pitch = Mth.lerp(rear, 0.78f, 0.08f) + slamPitch + grabK * 0.1f;
		float hipY = 13f + bob + rear * 1.5f;
		Matrix4f body = Rig.mat(0f, hipY, -3f, Mth.sin(ph) * 0.08f * amt + punch * 0.25f, pitch, Mth.sin(ph) * 0.05f * amt);

		// ---- legs
		for (int side = -1; side <= 1; side += 2) {
			float[] st = Rig.step(ph + (side > 0 ? Mth.PI : 0f), 4.5f * amt, 3f * amt);
			float[] hip = Rig.tp(body, side * 4.2f, 0f, 0f);
			float[] foot = v(side * 5f, 1.2f + st[1], -2f + st[0] + rear * 1.5f);
			float[] knee = ik(hip, foot, 7.5f, 7f, v(side * 0.4f, 0f, 1f));
			Rig.limb(pose, hip, knee, foot, 3.6f, 3.0f, 2.4f, FUR, null);
			ellipsoid(pose, foot[0], foot[1] - 0.2f, foot[2] + 1.2f, 2.2f, 1.2f, 3.6f, FACE);
			for (int k = 0; k < 3; k++) {
				ellipsoid(pose, foot[0] + (k - 1) * 1.2f * side, foot[1] - 0.3f, foot[2] + 4.4f, 0.6f, 0.6f, 0.8f, 8, 5, FACE, 0f, 1f);
			}
		}

		// ---- arms (IK; hands knuckle on the ground ahead unless a move moves them)
		for (int side = -1; side <= 1; side += 2) {
			float[] st = Rig.step(ph + (side > 0 ? 0f : Mth.PI), 6f * amt, 4f * amt);
			float[] sh = Rig.tp(body, side * 9.5f, 13.5f, 0.5f);
			float[] hand = v(side * 10.5f, 1.8f + st[1], 9f + st[0]);
			if (smash >= 0f) {
				float[] up = v(side * 5f, 40f, 2f), down = v(side * 5.5f, 1.8f, 15f);
				if (smash < 0.45f) {
					hand = lerp(hand, up, BeastPose.smooth(smash / 0.45f));
				} else if (smash < 0.6f) {
					hand = lerp(up, down, BeastPose.smooth((smash - 0.45f) / 0.15f));
				} else {
					hand = lerp(down, v(side * 10.5f, 1.8f, 9f), BeastPose.smooth((smash - 0.6f) / 0.4f));
				}
			}
			if (beat > 0f) {
				float hit = Mth.sin(t * 1.4f + (side > 0 ? 0f : Mth.PI));
				float[] chest = v(side * 3.5f, 23.5f, 9.5f + Math.max(0f, hit) * 3.5f);
				hand = lerp(hand, chest, beat);
			}
			if (grabK > 0f && side < 0) {
				float reach = s.is("throw") ? (s.prog < 0.5f ? 0f : 1f) : 1f;
				float[] tgt = s.is("throw") && s.prog < 0.55f ? v(-5f, 36f, -2f) : v(-4f, 18f, 24f * reach);
				hand = lerp(hand, tgt, grabK);
			}
			if (punch > 0f && side < 0) {
				hand = lerp(hand, v(-4f, 20f, 26f), punch);
			}
			float[] elbow = ik(sh, hand, 10.5f, 10f, v(side * 0.6f, 0.1f, -1f));
			Rig.limb(pose, sh, elbow, hand, 3.8f, 3.4f, 2.6f, FUR, null);
			// forearm bulk
			float[] fm = lerp(elbow, hand, 0.35f);
			ellipsoid(pose, fm[0], fm[1], fm[2], 3.4f, 3.4f, 3.4f, 10, 7, FUR, 0f, 1f);
			// fist with knuckles
			ellipsoid(pose, hand[0], hand[1], hand[2], 2.6f, 2.0f, 2.7f, FACE);
			for (int k = 0; k < 4; k++) {
				ellipsoid(pose, hand[0] + (k - 1.5f) * 1.1f, hand[1] - 1.2f, hand[2] + 1.6f, 0.65f, 0.7f, 0.75f, 8, 5, FACE, 0f, 1f);
			}
		}

		// ---- torso (body space, upright coordinates: +Y up the spine)
		Rig.push(pose, body);
		float br = 1f + Mth.sin(t * 0.1f) * 0.03f + beat * 0.04f;
		ellipsoid(pose, 0f, 1.5f, -0.5f, 6.2f, 5.5f, 5.4f, FUR);
		ellipsoid(pose, 0f, 7f, 0.5f, 6.8f * br, 6.5f, 5.8f * br, FUR);
		ellipsoid(pose, 0f, 7f, 3.4f, 4.6f, 5f, 3.2f, FUR_LT);
		ellipsoid(pose, 0f, 13f, 0f, 9.4f * br, 6.8f, 6.8f * br, FUR);
		for (int side = -1; side <= 1; side += 2) {
			ellipsoid(pose, side * 3.4f, 12.4f, 4.6f, 3.8f, 3.4f, 2.4f, FUR_LT);
			ellipsoid(pose, side * 8.6f, 13.8f, 0.3f, 4.2f, 4.2f, 4.4f, FUR);
		}
		// the shoulder hump
		ellipsoid(pose, 0f, 17f, -1.5f, 7f, 4f, 5f, FUR);

		// head (kept level, looking ahead)
		float hy = s.yaw(0.7f) * 0.8f;
		float hp = -pitch + 0.15f + s.pitch(0.5f) * 0.5f;
		boneYXZ(pose, 0f, 17.5f, 3.5f, hy, hp, 0f);
		float jaw = Math.max(beat * (0.4f + 0.3f * Mth.sin(t * 0.9f)), smash > 0.4f && smash < 0.7f ? 0.7f : 0f);
		head(pose, jaw);
		pose.popPose();
		pose.popPose();
	}

	/** The gorilla head (pivot at the neck, +Z forward). Also used by nothing else yet but kept public for previews. */
	public static void head(PoseStack pose, float jaw) {
		ellipsoid(pose, 0f, 2.6f, 0f, 4.4f, 4.5f, 4.3f, FUR);
		ellipsoid(pose, 0f, 6f, -1.2f, 1.7f, 2.4f, 3.4f, FUR);
		// brow ridge, face, muzzle
		ellipsoid(pose, 0f, 4f, 3.4f, 4.1f, 1.4f, 1.9f, SKIN_DK);
		ellipsoid(pose, 0f, 2f, 3.2f, 3.4f, 2.6f, 2.0f, FACE);
		ellipsoid(pose, 0f, 0.6f, 4.3f, 3.2f, 2.3f, 2.4f, FACE);
		for (int s = -1; s <= 1; s += 2) {
			eye(pose, s * 1.55f, 2.9f, 4.6f, 0.85f, s * 0.25f, 0.1f, EYE);
			ellipsoid(pose, s * 0.95f, 1.5f, 6.4f, 0.75f, 0.55f, 0.45f, 8, 5, DARK, 0f, 1f);
			ellipsoid(pose, s * 4.3f, 2.6f, 0.2f, 0.7f, 1.2f, 1.1f, 8, 6, FACE, 0f, 1f);
		}
		// nose bridge
		ellipsoid(pose, 0f, 2.3f, 5.6f, 1.6f, 1.2f, 1.0f, 10, 6, FACE, 0f, 1f);
		// lower jaw with canines
		bone(pose, 0f, -0.4f, 2f, jaw * 0.7f, 0f, 0f);
		ellipsoid(pose, 0f, -1.2f, 2.4f, 3f, 1.5f, 2.6f, FACE);
		ellipsoid(pose, 0f, -0.3f, 2.6f, 2.4f, 0.6f, 2.0f, 10, 6, MOUTH, 0f, 0.5f);
		for (int s = -1; s <= 1; s += 2) {
			cone(pose, s * 1.6f, -0.2f, 4.2f, 0f, 1f, 0.1f, 1.3f, 0.35f, 0.35f, TOOTH);
		}
		pose.popPose();
		for (int s = -1; s <= 1; s += 2) {
			cone(pose, s * 1.6f, -0.2f, 4.6f, 0f, -1f, 0.1f, 1.3f, 0.35f, 0.35f, TOOTH);
		}
		ellipsoid(pose, 0f, -0.3f, 3.2f, 2.4f, 0.5f, 2.0f, 10, 6, MOUTH, 0.5f, 1f);
	}
}
