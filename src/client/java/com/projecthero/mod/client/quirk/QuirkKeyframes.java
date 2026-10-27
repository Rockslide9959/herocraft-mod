package com.projecthero.mod.client.quirk;

import com.projecthero.mod.client.HipBend;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

/**
 * v0.16.7 (Quirk revamp): keyframed limb poses for the Quirk kits, in the style of {@code AllMightPose} but with the legs'
 * full rotation (kicks, spins and splits need yaw and roll on the legs too). A pose is a list of frames
 * <pre>
 *   { tick,  rArmX, rArmY, rArmZ,  lArmX, lArmY, lArmZ,  bodyX, bodyY,  rLegX, rLegY, rLegZ,  lLegX, lLegY, lLegZ,  headX }
 * </pre>
 * in radians (arm / leg X negative = swung forward / up; right arm Z positive = raised out to his side, the left arm
 * mirrors with negative Z; bodyX positive = leaning forward, bent at the hips with {@link HipBend}; headX is ADDED to the
 * look pitch). Frames are smoothstepped between and the whole pose fades in over the first {@link #FADE_IN} ticks and
 * out over the last {@link #FADE_OUT}, so it blends with the walk cycle instead of snapping.
 */
public final class QuirkKeyframes {
	public static final int COLS = 16;
	public static final float FADE_IN = 2f;
	public static final float FADE_OUT = 3f;

	private QuirkKeyframes() {
	}

	/** One frame: {@code f(tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, head x)}. */
	public static float[] f(float tick, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly,
			float rlz, float llx, float lly, float llz, float hx) {
		return new float[] { tick, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx };
	}

	/** The rest frame at {@code tick} (every part at 0: lerping to it hands the limbs back to vanilla). */
	public static float[] rest(float tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	/**
	 * v0.17.1 framework: a 16-column frame plus the optional 17th {@link #CROUCH} column (0..1 lowers the body onto bent legs,
	 * the vanilla sneak offsets weighted -- Overhaul's and Eraser Head's ground moves). A table mixes only one width.
	 */
	public static float[] fc(float tick, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly,
			float rlz, float llx, float lly, float llz, float hx, float crouch) {
		return new float[] { tick, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx, crouch };
	}

	/** The 17-column rest frame. */
	public static float[] restC(float tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	/** The optional crouch column. */
	public static final int CROUCH = 16;

	/** A crouch of weight {@code w}: legs bent, body lowered (the model's sneak offsets). */
	public static void crouch(HumanoidModel<?> model, float w) {
		if (w <= 0.001f || model.crouching) {
			return;
		}
		model.body.y += 3.2f * w;
		model.head.y += 4.2f * w;
		model.rightArm.y += 3.2f * w;
		model.leftArm.y += 3.2f * w;
		model.rightLeg.z += 4.0f * w;
		model.leftLeg.z += 4.0f * w;
		model.rightLeg.y += 0.2f * w;
		model.leftLeg.y += 0.2f * w;
	}

	/** The column mask bit of {@code col} (for {@link #applyMasked}). */
	public static int bit(int col) {
		return 1 << col;
	}

	/**
	 * {@link #apply}, but only the columns whose bit is set in {@code mask} (bit n = column n) -- a move that only moves the
	 * arms leaves the walk cycle's legs alone (Dark Shadow's commands).
	 */
	public static void applyMasked(HumanoidModel<?> m, float[] p, float w, int mask) {
		if (w <= 0f) {
			return;
		}
		net.minecraft.client.model.geom.ModelPart[] parts = { null, m.rightArm, m.rightArm, m.rightArm, m.leftArm, m.leftArm, m.leftArm };
		for (int c = 1; c <= 6; c++) {
			if ((mask & bit(c)) == 0) {
				continue;
			}
			var part = parts[c];
			switch ((c - 1) % 3) {
				case 0 -> part.xRot = Mth.lerp(w, part.xRot, p[c]);
				case 1 -> part.yRot = Mth.lerp(w, part.yRot, p[c]);
				default -> part.zRot = Mth.lerp(w, part.zRot, p[c]);
			}
		}
		if ((mask & bit(BODY_X)) != 0 && !m.crouching) {
			HipBend.pitch(m, w, p[BODY_X]);
		}
		if ((mask & bit(BODY_Y)) != 0) {
			m.body.yRot = Mth.lerp(w, m.body.yRot, p[BODY_Y]);
		}
		net.minecraft.client.model.geom.ModelPart[] legs = { m.rightLeg, m.rightLeg, m.rightLeg, m.leftLeg, m.leftLeg, m.leftLeg };
		for (int c = R_LEG_X; c <= L_LEG_Z; c++) {
			if ((mask & bit(c)) == 0) {
				continue;
			}
			var part = legs[c - R_LEG_X];
			switch ((c - R_LEG_X) % 3) {
				case 0 -> part.xRot = Mth.lerp(w, part.xRot, p[c]);
				case 1 -> part.yRot = Mth.lerp(w, part.yRot, p[c]);
				default -> part.zRot = Mth.lerp(w, part.zRot, p[c]);
			}
		}
		if ((mask & bit(HEAD_X)) != 0) {
			m.head.xRot = m.head.xRot + p[HEAD_X] * w;
		}
		m.hat.copyFrom(m.head);
	}

	/** Lerps only the legs' yaw and roll toward the frame (the columns the retired 12-column tables lacked). */
	public static void legTwist(HumanoidModel<?> m, float[] p, float w) {
		m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, p[R_LEG_Y]);
		m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, p[R_LEG_Z]);
		m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, p[L_LEG_Y]);
		m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, p[L_LEG_Z]);
	}

	/**
	 * v0.17.1 framework: in an arm's X or Y column, "at the target / along the look" -- replaced by the aim angle passed to
	 * {@link #sample(float[][], float, float, float)} before interpolating (Dark Shadow's commands, aimed palms).
	 */
	public static final float AIM = 99f;

	/** Column indices. */
	public static final int R_ARM_X = 1, R_ARM_Y = 2, R_ARM_Z = 3, L_ARM_X = 4, L_ARM_Y = 5, L_ARM_Z = 6, BODY_X = 7, BODY_Y = 8, R_LEG_X = 9,
			R_LEG_Y = 10, R_LEG_Z = 11, L_LEG_X = 12, L_LEG_Y = 13, L_LEG_Z = 14, HEAD_X = 15;

	/** The pose's length in ticks. */
	public static float length(float[][] frames) {
		return frames[frames.length - 1][0];
	}

	/** Pose values at {@code tick} (fractional), or null outside the pose (16 or 17 columns, as the table is). */
	public static float[] sample(float[][] frames, float tick) {
		return sampleN(frames, tick, frames.length == 0 ? COLS : frames[0].length);
	}

	/** {@link #sample} with {@link #AIM} entries resolved to {@code aimX} / {@code aimY} (arm columns only). */
	public static float[] sample(float[][] frames, float tick, float aimX, float aimY) {
		if (tick < 0f || tick > length(frames)) {
			return null;
		}
		for (int i = 1; i < frames.length; i++) {
			if (tick <= frames[i][0]) {
				float[] a = frames[i - 1];
				float[] b = frames[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = t * t * (3f - 2f * t);
				float[] out = new float[COLS];
				out[0] = tick;
				for (int k = 1; k < COLS; k++) {
					out[k] = Mth.lerp(t, aim(a[k], k, aimX, aimY), aim(b[k], k, aimX, aimY));
				}
				return out;
			}
		}
		return null;
	}

	private static float aim(float v, int col, float aimX, float aimY) {
		if (v != AIM) {
			return v;
		}
		return col == R_ARM_X || col == L_ARM_X ? aimX : col == R_ARM_Y || col == L_ARM_Y ? aimY : 0f;
	}

	/** Any-width keyframe sampling (smoothstep between frames; null outside). Used by the first-person clips too. */
	public static float[] sampleN(float[][] frames, float tick, int cols) {
		if (frames.length == 0 || tick < 0f || tick > frames[frames.length - 1][0]) {
			return null;
		}
		if (tick <= frames[0][0]) {
			return frames[0].clone();
		}
		for (int i = 1; i < frames.length; i++) {
			if (tick <= frames[i][0]) {
				float[] a = frames[i - 1];
				float[] b = frames[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = t * t * (3f - 2f * t);
				float[] out = new float[cols];
				out[0] = tick;
				for (int k = 1; k < cols; k++) {
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return null;
	}

	/**
	 * v0.17.1 framework: the migration helper -- one frame of the retired 12-column format {@code {tick, rArm xyz, lArm xyz,
	 * body xy, rLegX, lLegX, headX}} as a 16-column frame (leg yaw and roll 0). Kept for kit code that still builds frames in
	 * the old order; every shipped pose table is already 16-column.
	 */
	public static float[] from12(float[] o) {
		return f(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], o[8], o[9], 0, 0, o[10], 0, 0, o[11]);
	}

	/** The fade weight at {@code tick} of a pose {@code len} long (0..1). */
	public static float weight(float tick, float len) {
		return Mth.clamp(Math.min(tick / FADE_IN, (len - tick) / FADE_OUT), 0f, 1f);
	}

	/** Plays {@code frames} at {@code tick} on the model; true if it was in range. {@code strength} scales the fade weight. */
	public static boolean play(HumanoidModel<?> m, float[][] frames, float tick, float strength) {
		float[] p = sample(frames, tick);
		if (p == null) {
			return false;
		}
		apply(m, p, weight(tick, length(frames)) * strength);
		return true;
	}

	/** Lerps the model toward one sampled frame by {@code w}. */
	public static void apply(HumanoidModel<?> m, float[] p, float w) {
		if (w <= 0f) {
			return;
		}
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1]);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2]);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4]);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5]);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		if (!m.crouching) {
			HipBend.pitch(m, w, p[7]);
		}
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, p[9]);
		m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, p[10]);
		m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, p[11]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, p[12]);
		m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, p[13]);
		m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, p[14]);
		m.head.xRot = m.head.xRot + p[15] * w;
		if (p.length > CROUCH) {
			crouch(m, w * p[CROUCH]);
		}
		m.hat.copyFrom(m.head);
	}
}
