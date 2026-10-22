package com.projecthero.mod.titanshifter;

/**
 * v0.16.7 (user, Titan fixes): the Emergency Titan's own transformation timetable -- a rushed, unstable version of the
 * per-texel build. The normal build ({@code titan_texels/<model>_times.png}) lays the skeleton from the spine outward,
 * then the muscle, then the skin, each in its own window. The emergency build reuses each texel's place in that order
 * (its <i>rank</i> inside its layer) but:
 * <ul>
 *   <li>is faster ({@link TitanShifterConfig.Emergency#transformTicks}, 2 s against 3 s) and front-loaded;</li>
 *   <li>the skeleton flickers in and out in whole regions until the flesh covers it ({@link #SKELETON_STEADY});</li>
 *   <li>the flesh spreads in uneven blotches (a low-frequency noise, not the tidy spine-outward order) with gaps that fill
 *       late;</li>
 *   <li>the skin only partly forms: about a quarter of it ({@link #isPatch} -- noise blobs with ragged edges) never forms,
 *       leaving raw flesh showing, and those patches vent steam for as long as the Emergency Titan lives.</li>
 * </ul>
 * Pure functions of the texel's layer, rank and rest position, shared by the client renderer and the gametests. Positions
 * are the model's rest-pose pixels (geo space).
 */
public final class TitanEmergencyBuild {
	/** The normal build's layer windows (baked by the generator: skeleton, muscle, skin). */
	private static final float[][] B_WIN = { { 0.02f, 0.36f }, { 0.30f, 0.66f }, { 0.60f, 0.95f } };
	/** Skin noise below this never forms (about 27% of the skin). */
	public static final float PATCH_THRESHOLD = 0.385f;
	/** The skeleton flickers until this far through the build (by then the flesh hides it). */
	public static final float SKELETON_STEADY = 0.62f;
	/** Flicker steps over the build (about 1.4 a tick over the 2 s build). */
	public static final int FLICKER_STEPS = 56;
	/** A texel that never forms. */
	public static final float NEVER = 2.0f;

	private TitanEmergencyBuild() {
	}

	/** A texel's place (0..1) inside its layer's window of the normal build. */
	public static float rank(int layer, float build) {
		float[] w = B_WIN[Math.max(0, Math.min(2, layer))];
		return clamp((build - w[0]) / (w[1] - w[0]));
	}

	/** Noise inputs of a texel at rest position {@code (x, y, z)} (model px): {blob, patch, region}. */
	public static float[] noise(float x, float y, float z) {
		return new float[] { noise3(x * 0.16f, y * 0.16f, z * 0.16f, 1), noise3(x * 0.22f, y * 0.22f, z * 0.22f, 2),
				noise3(x * 0.11f, y * 0.11f, z * 0.11f, 3) };
	}

	/** True if this skin texel is in a patch that never forms ({@code jitter} 0..1 roughens the edge). */
	public static boolean isPatch(float patch, float jitter) {
		return patch + (jitter - 0.5f) * 0.05f < PATCH_THRESHOLD;
	}

	/** When the texel first appears in the emergency build (0..1 of it), or {@link #NEVER}. */
	public static float appear(int layer, float rank, float blob, float patch, float jitter) {
		switch (layer) {
			case 0:
				// the bones burst out fast, still spine-first
				return 0.0f + rank * 0.24f + jitter * 0.04f;
			case 1:
				// the flesh comes in blotches, not in order; the last gaps close at ~0.9
				return 0.20f + 0.62f * clamp(0.35f * rank + 0.65f * stretch(blob)) + jitter * 0.07f;
			default:
				if (isPatch(patch, jitter)) {
					return NEVER;
				}
				return 0.50f + 0.40f * clamp(0.5f * rank + 0.5f * stretch(blob)) + jitter * 0.05f;
		}
	}

	/** Visible at build progress {@code p}: appeared, and (for the skeleton) not flickered out this step. */
	public static boolean visible(int layer, float appear, float region, float jitter, float p) {
		if (p < appear) {
			return false;
		}
		if (layer == 0 && p < SKELETON_STEADY) {
			int step = (int) (p * FLICKER_STEPS);
			// whole regions blink together (the skeleton stuttering into being), fewer as it steadies
			float off = 0.12f + 0.43f * (1.0f - p / SKELETON_STEADY);
			if (hash01(step * 92821 + (int) (region * 9.0f) * 131 + 7) < off) {
				return false;
			}
			// and a sparkle of single texels
			return hash01(step * 31 + (int) (jitter * 4096.0f)) >= 0.08f;
		}
		return true;
	}

	/** The fraction of the skin texels (given their patch noise and jitter) that ever forms. */
	public static float skinCoverage(float[] patch, float[] jitter) {
		int n = 0;
		for (int i = 0; i < patch.length; i++) {
			if (!isPatch(patch[i], jitter[i])) {
				n++;
			}
		}
		return patch.length == 0 ? 1.0f : n / (float) patch.length;
	}

	/**
	 * The Emergency Titan's jerky clip time: {@code elapsed} ticks of the build warped so the body stalls, then lurches to
	 * catch up, in uneven beats of 3-7 ticks (deterministic in {@code seed}). Never runs backwards, matches {@code elapsed}
	 * at every beat boundary, and is scaled by {@link #CLIP_LEAD} so the clip finishes just inside the build.
	 * GeckoLib multiplies the whole elapsed time by the speed, so the speed handler returns {@code warp(e) / e}.
	 */
	public static double jerkyWarp(double elapsed, int seed) {
		if (elapsed <= 0.0) {
			return 0.0;
		}
		double start = 0.0;
		for (int beat = 0; beat < 400; beat++) {
			double len = 3.0 + Math.floor(hash01(seed * 7919 + beat * 104729) * 5.0);
			if (elapsed < start + len) {
				double f = (elapsed - start) / len;
				// most of the beat frozen (a stall), then a sudden lurch through it
				double hold = 0.45 + 0.25 * hash01(seed * 31 + beat * 613);
				double g = f < hold ? 0.12 * f / hold : 0.12 + 0.88 * Math.pow((f - hold) / (1.0 - hold), 0.55);
				return (start + g * len) * CLIP_LEAD;
			}
			start += len;
		}
		return elapsed * CLIP_LEAD;
	}

	/** The jerky clip runs this much ahead of the build, so it is done before the Titan steps out of it. */
	public static final double CLIP_LEAD = 1.08;

	// ---------------- noise

	public static float hash01(int v) {
		int x = v * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA6B;
		x ^= x >>> 13;
		return (x >>> 8) / 16777216.0f;
	}

	private static float lattice(int i, int j, int k, int s) {
		return hash01(i * 73856093 ^ j * 19349663 ^ k * 83492791 ^ s * 0x9E3779B1);
	}

	private static float smooth(float f) {
		return f * f * (3.0f - 2.0f * f);
	}

	/** Smooth value noise, 0..1 (mostly 0.25..0.75). */
	public static float noise3(float x, float y, float z, int seed) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
		float u = smooth(x - xi), v = smooth(y - yi), w = smooth(z - zi);
		float r = 0.0f;
		for (int a = 0; a < 2; a++) {
			for (int b = 0; b < 2; b++) {
				for (int c = 0; c < 2; c++) {
					r += (a == 1 ? u : 1 - u) * (b == 1 ? v : 1 - v) * (c == 1 ? w : 1 - w) * lattice(xi + a, yi + b, zi + c, seed);
				}
			}
		}
		return r;
	}

	/** Spreads the value noise's middle-heavy spread out over 0..1. */
	private static float stretch(float n) {
		return clamp((n - 0.25f) / 0.5f);
	}

	private static float clamp(float v) {
		return v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
	}
}
