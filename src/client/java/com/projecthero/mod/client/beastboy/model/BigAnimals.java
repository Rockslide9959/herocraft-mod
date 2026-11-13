package com.projecthero.mod.client.beastboy.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.beastboy.BeastForm;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: entry point for the blocky big animals (rhino, elephant, gorilla, the Beast), called first by
 * {@code BeastFormArt.renderPose}. Same contract: pose stack at the feet, +Z forward, blocks.
 */
public final class BigAnimals {
	private BigAnimals() {
	}

	/** @return false for forms drawn elsewhere */
	public static boolean render(BeastForm form, BeastPose s, PoseStack pose, MultiBufferSource buffers, int light, int tint) {
		if (form != BeastForm.RHINO && form != BeastForm.ELEPHANT && form != BeastForm.GORILLA && form != BeastForm.BEAST) {
			return false;
		}
		float[] m = s.morph();
		pose.pushPose();
		if (m[0] != 1f) {
			float cy = form.height * 0.35f;
			pose.translate(0f, cy * (1f - m[0]) * 0.3f, 0f);
			pose.scale(m[0], m[0], m[0]);
		}
		switch (form) {
			case RHINO -> RhinoModel.render(s, pose, buffers, light, tint, m[1]);
			case ELEPHANT -> ElephantModel.render(s, pose, buffers, light, tint, m[1]);
			case GORILLA -> GorillaModel.render(s, pose, buffers, light, tint, m[1]);
			default -> BeastModel.render(s, pose, buffers, light, tint, m[1]);
		}
		pose.popPose();
		return true;
	}

	/**
	 * A jointed quadruped leg posed from a gait sample: {@code fwd} -0.5..0.5 (+ = foot forward), {@code lift} 0..1. The upper bone swings
	 * by {@code amp}, the lower folds by {@code knee} while lifted, the foot stays level on the ground and tips its toes down in the
	 * swing; the hip rises {@code liftPx} so the foot clears the ground.
	 */
	/**
	 * Two-bone IK in the side plane: {upper, lower} world angles (vanilla xRot sense: 0 = hanging straight down, + = tip toward the back)
	 * of a limb reaching from its root to a target {@code dz} forward and {@code dy} up (pixels), bones {@code a} and {@code b}. The middle
	 * joint points back ({@code elbowBack}, arms and digitigrade hocks) or forward (knees).
	 */
	static float[] ik(float dz, float dy, float a, float b, boolean elbowBack) {
		float d = Mth.clamp(Mth.sqrt(dz * dz + dy * dy), Math.abs(a - b) + 0.01f, a + b - 0.001f);
		float t = (float) Math.atan2(-dz, -dy);
		float al = (float) Math.acos(Mth.clamp((a * a + d * d - b * b) / (2f * a * d), -1f, 1f));
		float be = (float) Math.acos(Mth.clamp((a * a + b * b - d * d) / (2f * a * b), -1f, 1f));
		float u = elbowBack ? t + al : t - al;
		float l = elbowBack ? u - (Mth.PI - be) : u + (Mth.PI - be);
		return new float[] { u, l };
	}

	/** A point (y up, z forward) of an upright body frame, pitched forward by {@code p} (+ = nose down): {y, z}. */
	static float[] pitchPt(float p, float y, float z) {
		float c = Mth.cos(p), s = Mth.sin(p);
		return new float[] { y * c - z * s, y * s + z * c };
	}

	/** Blends a part's rotation toward (x, y, z) by {@code w}. */
	static void blend(ModelPart m, float w, float x, float y, float z) {
		if (w <= 0f) {
			return;
		}
		m.xRot = Mth.lerp(w, m.xRot, x);
		m.yRot = Mth.lerp(w, m.yRot, y);
		m.zRot = Mth.lerp(w, m.zRot, z);
	}

	static void leg(ModelPart up, ModelPart shin, ModelPart foot, float fwd, float lift, float amp, float knee, float liftPx) {
		up.xRot += -fwd * amp * 2f - lift * knee * 0.35f;
		shin.xRot += lift * knee;
		foot.xRot += -(up.xRot + shin.xRot) * (1f - lift * 0.7f) + lift * 0.25f;
		up.y -= lift * liftPx;
	}
}
