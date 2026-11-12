package com.projecthero.mod.client.beastboy.model;

import static com.projecthero.mod.client.beastboy.model.BeastMesh.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's shark (2.7 blocks long): a great-white shape, dark green back over a pale belly, tall dorsal fin, long pectoral
 * fins, crescent tail, gill slits, underslung toothy jaw. Swims with an S-wave down the body (faster when moving; it flops on land).
 * Moves: bite / strike (lunge and snap), charge.
 */
public final class SharkModel {
	private SharkModel() {
	}

	public static void draw(PoseStack pose, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		boolean wet = s.inWater;
		float bite = Math.max(s.env("bite", 0.2f, 0.3f), s.env("strike", 0.2f, 0.3f));
		float speed = wet ? 0.18f + amt * 0.25f : 0.45f;
		float amp = wet ? 1.3f + amt * 1.4f : 2.2f;
		float phase = t * speed + s.walkPos * 0.3f;
		float lunge = s.isAny("bite", "strike") ? Mth.sin(Mth.clamp(s.prog / 0.5f, 0f, 1f) * Mth.PI) * 3f : 0f;
		float roll = wet ? 0f : 1.3f;

		Matrix4f root = Rig.mat(0f, wet ? 6.5f + Mth.sin(t * 0.08f) * 0.4f : 5.5f, lunge, 0f, -bite * 0.12f + s.pitch(0.5f) * (wet ? 0.6f : 0f), roll);
		Rig.push(pose, root);

		// spine with the swim wave (bigger toward the tail)
		float[] zs = { 19f, 13f, 5f, -3f, -11f, -17f, -22f };
		float[] w = { 1.8f, 4.6f, 6.4f, 6.2f, 4.2f, 2.3f, 1.2f };
		float[] h = { 2.2f, 4.8f, 6.6f, 6.3f, 4.4f, 2.6f, 1.6f };
		float[][] sp = new float[zs.length][];
		for (int i = 0; i < zs.length; i++) {
			float k = i / (float) (zs.length - 1);
			float sway = Mth.sin(phase - k * 3.2f) * amp * (0.15f + k * k * 1.1f);
			sp[i] = v(sway, (i == 0 ? -0.6f : 0f), zs[i]);
		}
		loft(pose, sp, w, h, 3, 16, SHARK, 0, null);
		float headYaw = (float) Math.atan2(sp[0][0] - sp[1][0], sp[0][2] - sp[1][2]);
		float tailYaw = (float) Math.atan2(sp[6][0] - sp[5][0], sp[6][2] - sp[5][2]);

		// head details: snout, eyes, gills, jaw
		Rig.push(pose, Rig.mat(sp[1][0], sp[1][1], sp[1][2], headYaw, 0f, 0f));
		ellipsoid(pose, 0f, 0.6f, 6.5f, 3.4f, 3.0f, 4.2f, COUNTER_V);
		for (int sd = -1; sd <= 1; sd += 2) {
			eye(pose, sd * 3.3f, 1.6f, 4.6f, 0.7f, sd * 1.3f, 0f, BLACK);
			ellipsoid(pose, sd * 1.3f, -0.4f, 10.2f, 0.4f, 0.25f, 0.4f, 6, 4, DARK, 0f, 1f);
			for (int g = 0; g < 4; g++) {
				pose.pushPose();
				pose.translate(sd * (5.2f - g * 0.15f), 0f, -2.5f - g * 1.5f);
				pose.mulPose(com.mojang.math.Axis.YP.rotation(sd * 0.25f));
				box(pose, -0.18f, -2.6f + g * 0.1f, -0.25f, 0.18f, 1.6f - g * 0.1f, 0.25f, DARK);
				pose.popPose();
			}
		}
		// jaw: hinged at the back, opens down
		float jaw = 0.05f + bite * 0.85f;
		if (s.isAny("bite", "strike") && s.prog > 0.5f) {
			jaw = Math.max(0.05f, 0.9f - (s.prog - 0.5f) / 0.12f * 0.9f);
		}
		// upper teeth row
		for (int sd = -1; sd <= 1; sd += 2) {
			for (int i = 0; i < 6; i++) {
				float z = 3.8f + i * 1.1f;
				float x = sd * (3.1f - i * 0.35f);
				cone(pose, x, -2.2f + i * 0.12f, z, 0f, -1f, 0.15f, 1.3f, 0.35f, 0.25f, TOOTH);
			}
		}
		ellipsoid(pose, 0f, -1.9f, 5.5f, 3.0f, 0.6f, 3.6f, 10, 4, MOUTH, 0.5f, 1f);
		bone(pose, 0f, -2f, 1.5f, jaw * 0.75f, 0f, 0f);
		ellipsoid(pose, 0f, -1.4f, 4.5f, 3.4f, 1.4f, 4.4f, COUNTER_V);
		ellipsoid(pose, 0f, -0.3f, 4.6f, 2.8f, 0.6f, 3.6f, 10, 4, MOUTH, 0f, 0.5f);
		for (int sd = -1; sd <= 1; sd += 2) {
			for (int i = 0; i < 5; i++) {
				float z = 3.2f + i * 1.15f;
				cone(pose, sd * (2.8f - i * 0.38f), -0.4f, z, 0f, 1f, 0.1f, 1.1f, 0.3f, 0.22f, TOOTH);
			}
		}
		pose.popPose();
		pose.popPose();

		// dorsal fin on the third ring
		float[] d0 = sp[2], d1 = sp[3];
		float[] db = lerp(d0, d1, 0.3f);
		float dy = (float) Math.atan2(d0[0] - d1[0], d0[2] - d1[2]);
		Rig.push(pose, Rig.mat(db[0], db[1], db[2], dy, 0f, 0f));
		loft(pose, new float[][] { { 0f, 5.6f, 0f }, { 0f, 8.5f, -1.6f }, { 0f, 11.2f, -3.6f }, { 0f, 12.6f, -5.6f } }, new float[] { 0.7f, 0.55f, 0.35f, 0.1f },
				new float[] { 4.2f, 3f, 1.8f, 0.3f }, 3, 8, FIN, 0, v(0f, 0f, 1f));
		// pectoral fins
		float pf = Mth.sin(t * 0.12f) * 0.1f;
		for (int sd = -1; sd <= 1; sd += 2) {
			loft(pose, new float[][] { { sd * 4.6f, -3.2f, 6.5f }, { sd * 8f, -5.2f + pf * sd, 4.5f }, { sd * 11.4f, -6.8f + pf * sd * 2f, 1.2f },
					{ sd * 13.2f, -7.6f + pf * sd * 3f, -1.2f } }, new float[] { 3.6f, 2.8f, 1.6f, 0.3f }, new float[] { 0.7f, 0.5f, 0.35f, 0.1f }, 3, 8,
					FIN, 0, v(0f, 1f, 0f));
		}
		pose.popPose();
		// small second dorsal + pelvic fins near the tail
		float[] pb = lerp(sp[4], sp[5], 0.4f);
		Rig.push(pose, Rig.mat(pb[0], pb[1], pb[2], tailYaw * 0.6f, 0f, 0f));
		loft(pose, new float[][] { { 0f, 2.6f, 0f }, { 0f, 4f, -1.2f } }, new float[] { 0.4f, 0.1f }, new float[] { 1.4f, 0.2f }, 2, 6, FIN, 0, v(0f, 0f, 1f));
		for (int sd = -1; sd <= 1; sd += 2) {
			loft(pose, new float[][] { { sd * 2.4f, -2.6f, 1.5f }, { sd * 4.4f, -4f, -0.5f } }, new float[] { 1.6f, 0.2f }, new float[] { 0.4f, 0.1f }, 2, 6,
					FIN, 0, v(0f, 1f, 0f));
		}
		pose.popPose();
		// crescent tail fin
		Rig.push(pose, Rig.mat(sp[6][0], sp[6][1], sp[6][2], tailYaw, 0f, 0f));
		loft(pose, new float[][] { { 0f, 0f, 0.8f }, { 0f, 3.5f, -1.8f }, { 0f, 7.5f, -4.4f }, { 0f, 10.5f, -6.8f } }, new float[] { 0.7f, 0.5f, 0.35f, 0.1f },
				new float[] { 2.6f, 2.0f, 1.3f, 0.2f }, 3, 8, FIN, 0, v(0f, 0f, 1f));
		loft(pose, new float[][] { { 0f, 0f, 0.8f }, { 0f, -2.6f, -1.6f }, { 0f, -5.4f, -3.6f }, { 0f, -7.4f, -5.2f } }, new float[] { 0.7f, 0.5f, 0.35f, 0.1f },
				new float[] { 2.4f, 1.8f, 1.1f, 0.2f }, 3, 8, FIN, 0, v(0f, 0f, 1f));
		pose.popPose();
		pose.popPose();
	}
}
