package com.projecthero.mod.client.thor;

import com.projecthero.mod.power.ThorTwinThunder;

/**
 * v0.16.1: the Twin Thunder Strike's keyframes ({@link ThorTwinThunder}), timed to the server's clock: both weapons
 * raised high in a V (anticipation), held there while the bolts strike them ({@link ThorTwinThunder#BOLT_TICK}) and the
 * lightning arcs between them, then slammed down together into the ground on {@link ThorTwinThunder#SLAM_TICK} with
 * the body bent hard from the hips, held through the shockwave and recovered.
 */
final class ThorTwinFrames {
	static final int END = ThorTwinThunder.END_TICK;
	private static final int BOLT = ThorTwinThunder.BOLT_TICK;
	private static final int SLAM = ThorTwinThunder.SLAM_TICK;

	private static float[] f(float tick, float... v) {
		float[] out = new float[13];
		out[0] = tick;
		System.arraycopy(v, 0, out, 1, Math.min(12, v.length));
		return out;
	}

	/** Third person, {@code ThorPose}'s format (hammer arm = Mjolnir's, free arm = Stormbreaker's). */
	static final float[][] THIRD = {
			f(0, -1.2f, 0f, 0.2f, -1.2f, 0f, -0.2f, 0f, 0f, 0f, 0f, 0f, 1),
			f(5, -2.95f, 0.05f, 0.42f, -2.95f, -0.05f, -0.42f, -0.18f, 0f, 0.05f, -0.05f, -0.4f, 1),
			f(BOLT, -3.1f, 0.05f, 0.45f, -3.1f, -0.05f, -0.45f, -0.24f, 0f, 0.05f, -0.05f, -0.45f, 1),
			f(SLAM - 2, -3.15f, 0.05f, 0.35f, -3.15f, -0.05f, -0.35f, -0.28f, 0f, 0.08f, -0.08f, -0.45f, 1),
			f(SLAM, -0.42f, -0.18f, -0.05f, -0.42f, 0.18f, 0.05f, 0.62f, 0f, -0.6f, 0.5f, 0.42f, 1),
			f(SLAM + 7, -0.4f, -0.16f, -0.05f, -0.4f, 0.16f, 0.05f, 0.58f, 0f, -0.55f, 0.46f, 0.4f, 1),
			f(END) };

	/** First person, both hands (mirrored): {tick, x, y, z, xDeg, yDeg, zDeg}. */
	static final float[][] FP = {
			{0, 0, 0, 0, 0, 0, 0},
			{5, 0.02f, 0.6f, 0.05f, 22, 0, -12},
			{SLAM - 2, 0.02f, 0.66f, 0.05f, 26, 0, -10},
			{SLAM, -0.15f, -0.25f, -0.3f, -90, 0, 10},
			{SLAM + 6, -0.15f, -0.22f, -0.28f, -86, 0, 10},
			{END, 0, 0, 0, 0, 0, 0} };

	private ThorTwinFrames() {
	}
}
