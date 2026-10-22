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

	/** The pose's length in ticks. */
	public static float length(float[][] frames) {
		return frames[frames.length - 1][0];
	}

	/** Pose values at {@code tick} (fractional), or null outside the pose. */
	public static float[] sample(float[][] frames, float tick) {
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
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return null;
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
		m.hat.copyFrom(m.head);
	}
}
