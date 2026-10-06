package com.projecthero.mod.ironman.suit;

/**
 * v0.14.28: the Iron Man suit-up / suit-down body pose as a <b>pure function</b> of the synced {@link IronManSuitFx}
 * piece clocks -- which piece is building (or un-building) right now and how far along it is. No randomness, no tremor,
 * no client state, so every viewer sees the same frame and the gametests can check it on the server.
 *
 * <ul>
 *   <li><b>Boots</b>: looking down at the feet, arms a little out, the right foot lifted and then planted.</li>
 *   <li><b>Leggings</b>: a wide braced stance, head following the plates up the legs, arms out to the sides.</li>
 *   <li><b>Chestplate</b>: arms spread wide, chest out, head up.</li>
 *   <li><b>Helmet</b>: head bowed with the right hand raised to the face, then the hand drops and the head snaps up.</li>
 * </ul>
 * Each piece moves from its start key to its end key over its {@link IronManSuitFx#BUILD_TICKS} window and cross-fades
 * from the previous piece's end key over {@link #CROSS_FADE} ticks. A suit-down ({@link IronManSuitFx#STYLE_UNBUILD})
 * plays the same keys backwards, helmet -> boots. The pose eases in over {@link #EASE_IN} ticks and is gone
 * {@link #EASE_OUT} ticks (0.25 s) after the last piece finishes -- it never lingers past the build.
 */
public final class IronManSuitPoses {
	public static final float EASE_IN = 6f;
	public static final float EASE_OUT = 5f;
	public static final float CROSS_FADE = 10f;

	/** Indices into a key: right arm x/y/z, left arm x/y/z, right leg x/z, left leg x/z, head x/y. */
	public static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, RLX = 6, RLZ = 7, LLX = 8, LLZ = 9,
			HX = 10, HY = 11, SIZE = 12;

	/** One frame: blend weight 0..1 and the target angles. */
	public record Sample(float weight, float[] key) {
	}

	private static float[] k(float rax, float ray, float raz, float lax, float lay, float laz, float rlx, float rlz,
			float llx, float llz, float hx, float hy) {
		return new float[] { rax, ray, raz, lax, lay, laz, rlx, rlz, llx, llz, hx, hy };
	}

	/** [piece bit 0 HEAD .. 3 FEET][0 start key, 1 end key]. Arm / leg z: right +z and left -z both swing out. */
	private static final float[][][] KEYS = {
			// helmet: head bowed, right hand up at the face -> hand down at the side, head up
			{ k(-1.85f, -0.45f, 0.05f, 0f, 0f, -0.12f, 0f, 0.08f, 0f, -0.08f, 0.50f, 0f),
					k(0f, 0f, 0.12f, 0f, 0f, -0.12f, 0f, 0.08f, 0f, -0.08f, -0.12f, 0f) },
			// chestplate: arms spread wide, chest out, head up
			{ k(0.05f, 0f, 1.10f, 0.05f, 0f, -1.10f, 0f, 0.14f, 0f, -0.14f, -0.15f, 0f),
					k(0.25f, 0f, 1.45f, 0.25f, 0f, -1.45f, 0f, 0.16f, 0f, -0.16f, -0.35f, 0f) },
			// leggings: braced wide stance, arms out to the sides, head following the plates up the legs
			{ k(-0.15f, 0f, 0.70f, -0.15f, 0f, -0.70f, -0.12f, 0.24f, -0.12f, -0.24f, 0.55f, 0f),
					k(-0.10f, 0f, 0.95f, -0.10f, 0f, -0.95f, -0.08f, 0.22f, -0.08f, -0.22f, 0.20f, 0f) },
			// boots: looking down at the feet, right foot lifted -> planted
			{ k(0.05f, 0f, 0.30f, 0.05f, 0f, -0.30f, -0.45f, 0.06f, 0f, -0.06f, 0.65f, 0f),
					k(0f, 0f, 0.45f, 0f, 0f, -0.45f, 0f, 0.12f, 0f, -0.12f, 0.55f, 0f) },
	};

	private IronManSuitPoses() {
	}

	/** Piece {@code bit}'s key at its own progress {@code p} (0..1). Pure, smooth, monotonic per angle. */
	public static float[] key(int bit, float p) {
		float[][] ks = KEYS[Math.max(0, Math.min(3, bit))];
		float t = clamp(p);
		float[] out = new float[SIZE];
		for (int i = 0; i < SIZE; i++) {
			float e;
			if (bit == 0 && (i == HX || i == HY)) {
				e = smooth((t - 0.70f) / 0.15f); // the head stays bowed, then snaps up
			} else if (bit == 0 && i <= RAZ) {
				e = smooth((t - 0.45f) / 0.35f); // the hand leaves the face before the head comes up
			} else if (bit == 3 && (i == RLX || i == RLZ)) {
				e = smooth((t - 0.55f) / 0.30f); // the foot is planted once the boot is mostly on
			} else {
				e = smooth(t);
			}
			out[i] = ks[0][i] + (ks[1][i] - ks[0][i]) * e;
		}
		return out;
	}

	/** Does {@code fx} drive a per-piece pose at all? (The Mark V case keeps its own case-in-hand pose.) */
	public static boolean drives(IronManSuitFx fx) {
		// v0.14.29: the Mark 5 suitcase build has its own pose too (IronManMk5Suitcase#pose)
		// v0.15.4: ...and so has the Mark 7 bracelet suit-up (IronManBraceletSuitUp#pose)
		return fx.style() != IronManSuitFx.STYLE_CASE && fx.style() != IronManSuitFx.STYLE_MK5
				&& fx.style() != IronManSuitFx.STYLE_BRACELET;
	}

	/** Does piece {@code bit}'s clock count for the pose (a build-on, or a reverse build coming off)? */
	private static boolean counts(IronManSuitFx fx, int bit) {
		return fx.start(bit) > 0L && (fx.assembling(bit) || fx.unbuilding());
	}

	private static int window(IronManSuitFx fx, int bit) {
		return fx.assembling(bit) ? fx.lockTicks() : fx.releaseTicks();
	}

	/** The key of piece {@code bit} {@code age} ticks into its window: forwards on, backwards off. */
	private static float[] pieceKey(IronManSuitFx fx, int bit, float age) {
		float p = clamp(age / window(fx, bit));
		return key(bit, fx.assembling(bit) ? p : 1f - p);
	}

	/**
	 * This frame's pose, or null when no piece is building / un-building and the last one finished more than
	 * {@link #EASE_OUT} ticks ago. A pure function of {@code fx} and the time.
	 */
	public static Sample sample(IronManSuitFx fx, long now, float partial) {
		if (fx == null || !drives(fx)) {
			return null;
		}
		int cur = -1;
		float curAge = 0f;
		for (int bit = 0; bit < 4; bit++) {
			if (!counts(fx, bit)) {
				continue;
			}
			float a = now - fx.start(bit) + partial;
			if (a >= 0f && a < window(fx, bit) && (cur < 0 || fx.start(bit) > fx.start(cur))) {
				cur = bit;
				curAge = a;
			}
		}
		if (cur >= 0) {
			boolean dir = fx.assembling(cur);
			// the piece just before it in the same sequence (ended at, or a few ticks before, this one's start)
			int prev = -1;
			for (int bit = 0; bit < 4; bit++) {
				if (bit == cur || !counts(fx, bit) || fx.assembling(bit) != dir || fx.start(bit) >= fx.start(cur)) {
					continue;
				}
				long end = fx.start(bit) + window(fx, bit);
				if (end <= fx.start(cur) + 1 && end >= fx.start(cur) - CROSS_FADE
						&& (prev < 0 || fx.start(bit) > fx.start(prev))) {
					prev = bit;
				}
			}
			float[] key = pieceKey(fx, cur, curAge);
			if (prev >= 0) {
				if (curAge < CROSS_FADE) {
					key = lerp(pieceKey(fx, prev, window(fx, prev)), key, smooth(curAge / CROSS_FADE));
				}
				return new Sample(1f, key);
			}
			return new Sample(smooth(curAge / EASE_IN), key);
		}
		// nothing running: ease out of the last piece's final key, quickly
		int last = -1;
		float since = 0f;
		for (int bit = 0; bit < 4; bit++) {
			if (!counts(fx, bit)) {
				continue;
			}
			float s = now - (fx.start(bit) + window(fx, bit)) + partial;
			if (s >= 0f && (last < 0 || s < since)) {
				last = bit;
				since = s;
			}
		}
		if (last < 0 || since >= EASE_OUT) {
			return null;
		}
		return new Sample(1f - smooth(since / EASE_OUT), pieceKey(fx, last, window(fx, last)));
	}

	/** The blend weight this frame (0 = no pose). */
	public static float weight(IronManSuitFx fx, long now, float partial) {
		Sample s = sample(fx, now, partial);
		return s == null ? 0f : s.weight();
	}

	/** Is any piece of the current sequence still building / un-building (or easing out)? */
	public static boolean active(IronManSuitFx fx, long now) {
		return sample(fx, now, 0f) != null;
	}

	static float[] lerp(float[] a, float[] b, float t) {
		float[] out = new float[SIZE];
		for (int i = 0; i < SIZE; i++) {
			out[i] = a[i] + (b[i] - a[i]) * t;
		}
		return out;
	}

	static float smooth(float x) {
		x = clamp(x);
		return x * x * (3f - 2f * x);
	}

	static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}
