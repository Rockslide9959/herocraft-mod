package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: the fix-round rhino head and elephant trunk / ear on the shared {@link BeastMesh} atlas, kept for the
 * human-form quick morphs ({@link QuickMorphs}) now that the full rhino / elephant bodies moved to {@link BigMesh} and their own textures.
 */
final class LegacyBigParts {
	private LegacyBigParts() {
	}

	/** The rhino head (pivot at the neck, +Z forward, level). Also used by the quick-morph ram. */
	static void rhinoHead(PoseStack pose, float t) {
		// skull and the long face, wider at the cheeks
		ellipsoid(pose, 0f, 0.8f, 3.4f, 5.8f, 6.0f, 6.6f, WRINKLE_V);
		ellipsoid(pose, 0f, -1.2f, 9.2f, 4.8f, 5.0f, 5.8f, WRINKLE_V);
		for (int s = -1; s <= 1; s += 2) {
			ellipsoid(pose, s * 3.4f, -2.4f, 7.0f, 2.4f, 3.4f, 4.2f, WRINKLE_V);
		}
		// the horn boss on the nose and the square muzzle with broad lips
		ellipsoid(pose, 0f, 1.6f, 12.4f, 3.4f, 2.4f, 3.0f, WRINKLE_LT);
		ellipsoid(pose, 0f, -3.0f, 13.2f, 4.0f, 3.4f, 3.0f, WRINKLE_LT);
		ellipsoid(pose, 0f, -5.3f, 12.6f, 3.9f, 1.4f, 2.8f, WRINKLE_LT);
		ellipsoid(pose, 0f, -4.6f, 13.1f, 3.5f, 0.45f, 2.6f, 10, 4, DARK, 0.4f, 0.6f);
		ellipsoid(pose, 0f, -6.4f, 11.8f, 3.0f, 1.0f, 2.2f, WRINKLE_V);
		for (int s = -1; s <= 1; s += 2) {
			// flared nostrils
			ellipsoid(pose, s * 1.9f, -1.8f, 15.6f, 0.9f, 0.7f, 0.6f, 8, 5, NOSE, 0f, 1f);
			ellipsoid(pose, s * 2.2f, -1.4f, 15.0f, 1.4f, 1.1f, 1.0f, 8, 5, WRINKLE_LT, 0f, 1f);
			// small eye under a heavy brow fold, a crease below
			eye(pose, s * 4.6f, 1.4f, 7.4f, 0.95f, s * 1.15f, 0f, EYE);
			ellipsoidR(pose, s * 4.4f, 2.4f, 7.5f, 1.5f, 0.75f, 1.9f, s * 0.4f, 0f, 0f, SKIN_DK);
			ellipsoidR(pose, s * 4.5f, 0.1f, 7.8f, 1.2f, 0.4f, 1.6f, s * 0.4f, 0f, 0f, SKIN_DK);
			// cheek folds
			for (int k = 0; k < 3; k++) {
				ellipsoidR(pose, s * (4.6f - k * 0.3f), -1.6f - k * 1.3f, 5.2f + k * 0.8f, 0.5f, 0.35f, 2.2f, s * 0.25f, 0.1f, 0f, SKIN_DK);
			}
			// upright tube ears on top of the head, flicking now and then
			float flick = Mth.sin(t * 0.21f + s) > 0.93f ? 0.45f : 0f;
			float[][] ear = { { s * 3.4f, 5.6f, 1.6f }, { s * 4.4f, 8.8f, 0.8f - flick }, { s * 5.0f, 11.6f, 0.2f - flick * 2f } };
			loft(pose, ear, new float[] { 1.5f, 1.7f, 0.5f }, new float[] { 1.3f, 1.6f, 0.4f }, 3, 10, WRINKLE, 0, v(0f, 0f, 1f));
			ellipsoid(pose, s * 4.5f, 9.2f, 1.6f - flick, 0.7f, 1.6f, 0.3f, 8, 6, INNER_EAR, 0f, 1f);
		}
		// the big front horn (curving up and back) and the second horn behind it
		horn(pose, new float[][] { { 0f, 2.4f, 12.8f }, { 0f, 5.4f, 14.4f }, { 0f, 8.6f, 14.8f }, { 0f, 11.2f, 13.6f }, { 0f, 12.6f, 12.2f } }, 2.9f, HORN);
		horn(pose, new float[][] { { 0f, 4.4f, 8.6f }, { 0f, 6.8f, 9.4f }, { 0f, 8.8f, 9.0f }, { 0f, 9.9f, 8.0f } }, 2.0f, HORN);
	}

	/**
	 * One big ear hinged at the side of the head (s = +1 left), swung out to the side and back; flap opens it wider. The inner face
	 * (pale) looks forward.
	 */
	static void elephantEar(PoseStack pose, int s, float flap, float k) {
		boneYXZ(pose, s * 6.2f * k, 4f * k, 3.5f * k, -s * (1.05f - flap), 0f, s * 0.12f);
		pose.scale(k, k, k);
		ellipsoid(pose, 0f, -2f, -7.8f, 0.9f, 11f, 8.4f, 14, 9, WRINKLE_V, 0f, 1f);
		ellipsoid(pose, s * 0.55f, -1.9f, -7.6f, 0.45f, 9.6f, 7.2f, 14, 9, INNER_EAR, 0f, 1f);
		pose.popPose();
	}

	/** The trunk from its root on the face. trunkUp raises and curls it up (trumpet / spray), grab reaches it forward. */
	static void elephantTrunk(PoseStack pose, float t, float amt, float trunkUp, float grab, float swing) {
		int n = 8;
		float[] yb = new float[n], pb = new float[n];
		for (int i = 0; i < n; i++) {
			float sway = Mth.sin(t * 0.1f - i * 0.5f) * 0.05f + Mth.sin(t * 0.33f) * 0.03f * amt + swing * 0.12f;
			yb[i] = sway;
			// hanging: bends from forward to down, tip curls forward a little
			float hang = i < 2 ? 0.25f : (i > 5 ? -0.22f : 0.06f);
			float up = i < 3 ? -0.5f : (i > 5 ? -0.5f : -0.3f);
			float reach = i < 2 ? -0.3f : (i > 5 ? 0.05f : -0.05f);
			pb[i] = hang + trunkUp * (up - hang) + grab * (reach - hang) * (1f - trunkUp);
		}
		float[][] pts = walk(v(0f, -2.5f, 11.5f), 0f, 0.6f + -trunkUp * 0.6f, 3.2f, yb, pb);
		float[] w = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			w[i] = Mth.lerp(i / (float) n, 3.6f, 1.4f);
		}
		loft(pose, pts, w, w, 3, 12, WRINKLE, 2, v(0f, 0f, 1f));
		float[] tip = pts[n];
		ellipsoid(pose, tip[0], tip[1], tip[2], 1.6f, 1.6f, 1.6f, 10, 6, WRINKLE_LT, 0f, 1f);
	}
}
