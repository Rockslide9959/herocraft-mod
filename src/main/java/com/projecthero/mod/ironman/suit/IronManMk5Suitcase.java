package com.projecthero.mod.ironman.suit;

/**
 * v0.14.29: the Mark 5 suitcase suit-up / suit-down timetable and body pose -- pure data and math (no client classes) so
 * the server sequence ({@link IronManSuitUpManager}), the synced clock ({@link IronManSuitFx}), the client renderer and
 * the gametests all read the same numbers.
 *
 * <p><b>v0.15.8:</b> the build is now per texel, gantry style ({@link #appear}, client {@code IronManGantryBuild}) and the
 * suit-down is exactly the suit-up backwards ({@link #frame}); the per-bone step windows below are the v0.14.29 build,
 * kept for the old reveal path.
 *
 * <h2>Suit-up ({@value #UP_TICKS} ticks = 7 s, right-click the case)</h2>
 * <ol>
 *   <li>0 .. {@value #HOLD_TICKS}: the player holds the closed suitcase out in front in both hands.</li>
 *   <li>The case turns into the chestplate, which the player puts on their chest, then holds the arms out to the side
 *       while the suit builds itself on (base halves + pixel shell, as every other suit) in this order:
 *       <b>chest</b> (torso bones), <b>arms</b>, <b>legs</b> (leggings then boots), <b>head</b> (helmet + brow), and
 *       the <b>faceplate</b> last.</li>
 * </ol>
 * A piece's own build window is split into sub-windows ({@link #boneWindow}) so the chestplate builds its torso before
 * its arms and the helmet builds its shell before the faceplate.
 *
 * <h2>Suit-down ({@value #DOWN_TICKS} ticks = 7 s, C)</h2>
 * The exact reverse -- faceplate, head, boots, legs, arms, chest -- then the arms come in, the chestplate turns back into
 * the suitcase, which ends up held out in front in both hands (and then in the main hand, see
 * {@link IronManSuitUpManager#placeSuitcase}).
 */
public final class IronManMk5Suitcase {
	/** v0.15.8: 7 s suit-up. */
	public static final int UP_TICKS = 140;
	/** v0.15.8, user request: the suit-down is the suit-up played backwards -- just as long. */
	public static final int DOWN_TICKS = UP_TICKS;
	/** The case is held out in front this long before it becomes the chestplate. */
	public static final int HOLD_TICKS = 20;
	/** The case shrinks into the chestplate over this long once the hold is over. */
	public static final int MORPH_TICKS = 8;

	// ---------------- v0.15.8: the gantry-style build (per texel, IronManGantryBuild's Mark 5 scheme) ----------------
	//
	// Explicit user request: the case is brought to the chest and the top half of the chestplate is on; the rest of the
	// chest builds on from it (as on the Stark Gantry), then the arms build down from the chest to the hands, then the
	// legs from the top of the legs to the soles of the boots, then the helmet comes up from the back of the neck over
	// the head, and the faceplate closes down out of it last. Taking it off is the same frames backwards.

	/** Build stages of the Mark 5 scheme (a texel's stage + its position k = 0..1 along the stage's build). */
	public static final int S_CHEST_TOP = 0, S_CHEST = 1, S_ARMS = 2, S_LEGS = 3, S_HELMET = 4, S_FACEPLATE = 5;
	/** The frame the top half of the chestplate is on (the case has shrunk into it). */
	public static final int CHEST_ON = HOLD_TICKS + MORPH_TICKS;
	/** Each stage's frame window {from, to} on the suit-up timetable. */
	private static final int[][] WINDOW = { { CHEST_ON, CHEST_ON }, { CHEST_ON, 52 }, { 52, 76 }, { 76, 104 }, { 104, 124 },
			{ 124, 134 } };

	/** v0.15.8: the frame (suit-up timetable) a texel of {@code stage} at build position {@code k} is on. */
	public static float appear(int stage, float k) {
		int[] w = WINDOW[Math.max(0, Math.min(WINDOW.length - 1, stage))];
		return w[0] + (w[1] - w[0]) * (0.02f + 0.96f * clamp(k));
	}

	/** v0.15.8: the frame of the suit-up timetable shown {@code age} ticks into a suit-up / (backwards) a suit-down. */
	public static float frame(boolean up, float age) {
		return up ? age : UP_TICKS - age;
	}

	/** Suit-up: the tick each piece (bit 0 head, 1 chest, 2 legs, 3 feet) reaches the slot (just before it shows). */
	private static final int[] UP_START = { 102, 26, 74, 74 };
	/** Suit-up: how long each piece builds for (the chest window includes its arms). */
	private static final int[] UP_WINDOW = { 32, 50, 30, 30 };
	/** Suit-down: the tick each piece starts coming apart -- the suit-up's windows mirrored (head first ... chest last). */
	private static final int[] DOWN_START = { UP_TICKS - 134, UP_TICKS - 76, UP_TICKS - 104, UP_TICKS - 104 };
	/** Suit-down: how long each piece takes to come apart. */
	private static final int[] DOWN_WINDOW = { 32, 50, 30, 30 };

	/** Share of the chestplate's window the torso builds in; the arms build in the rest. */
	public static final float CHEST_TORSO_END = 0.5f;
	/** Share of the helmet's window the helmet shell + brow build in; the faceplate builds in the rest. */
	public static final float HEAD_SHELL_END = 0.6f;

	/** Build-order steps, as the user listed them. */
	public static final int STEP_CHEST = 0, STEP_ARMS = 1, STEP_LEGS = 2, STEP_HEAD = 3, STEP_FACEPLATE = 4;

	private IronManMk5Suitcase() {
	}

	public static int upStart(int bit) {
		return bit >= 0 && bit < 4 ? UP_START[bit] : -1;
	}

	public static int upWindow(int bit) {
		return bit >= 0 && bit < 4 ? UP_WINDOW[bit] : IronManSuitFx.BUILD_TICKS;
	}

	public static int downStart(int bit) {
		return bit >= 0 && bit < 4 ? DOWN_START[bit] : -1;
	}

	public static int downWindow(int bit) {
		return bit >= 0 && bit < 4 ? DOWN_WINDOW[bit] : IronManSuitFx.BUILD_TICKS;
	}

	/** Is this bone one of the chestplate's arm bones (built after the torso)? */
	public static boolean armBone(String bone) {
		return bone != null && (bone.startsWith("right_") || bone.startsWith("left_"))
				&& (bone.endsWith("_upper_arm") || bone.endsWith("_shoulder") || bone.endsWith("_gauntlet")
						|| bone.endsWith("_blade") || bone.endsWith("_arm"));
	}

	/** Which build-order step a bone of piece {@code bit} belongs to ({@link #STEP_CHEST} .. {@link #STEP_FACEPLATE}). */
	public static int step(int bit, String bone) {
		return switch (bit) {
			case 0 -> "faceplate".equals(bone) ? STEP_FACEPLATE : STEP_HEAD;
			case 1 -> armBone(bone) ? STEP_ARMS : STEP_CHEST;
			default -> STEP_LEGS;
		};
	}

	/** The sub-window {start, end} (fractions of piece {@code bit}'s own window) {@code bone} builds in. */
	public static float[] boneWindow(int bit, String bone) {
		return stepWindow(step(bit, bone));
	}

	/** The sub-window {start, end} (fractions of the piece's window) of build-order step {@code step}. */
	public static float[] stepWindow(int step) {
		return switch (step) {
			case STEP_CHEST -> new float[] { 0f, CHEST_TORSO_END };
			case STEP_ARMS -> new float[] { CHEST_TORSO_END, 1f };
			case STEP_HEAD -> new float[] { 0f, HEAD_SHELL_END };
			case STEP_FACEPLATE -> new float[] { HEAD_SHELL_END, 1f };
			default -> new float[] { 0f, 1f };
		};
	}

	/** As {@link #remap}, for a texel of build-order step {@code step}. */
	public static float remapStep(int step, float t) {
		float[] w = stepWindow(step);
		return w[0] + (0.02f + 0.98f * clamp(t)) * (w[1] - w[0]);
	}

	/** {@code bone}'s own build progress (0..1) at piece progress {@code p}. */
	public static float localProgress(int bit, String bone, float p) {
		float[] w = boneWindow(bit, bone);
		return clamp((p - w[0]) / (w[1] - w[0]));
	}

	/**
	 * Re-times a texel build time {@code t} (0..1 of the bone's own build) into the piece's window. Never 0, so a
	 * piece at progress 0 (not started / fully taken apart) shows nothing at all.
	 */
	public static float remap(int bit, String bone, float t) {
		return remapStep(step(bit, bone), t);
	}

	/** Suit-up: the sequence tick {start, end} a build-order step runs over. */
	public static int[] upStepTicks(int step) {
		return switch (step) {
			case STEP_CHEST -> span(1, 0f, CHEST_TORSO_END);
			case STEP_ARMS -> span(1, CHEST_TORSO_END, 1f);
			case STEP_LEGS -> new int[] { UP_START[2], Math.max(UP_START[2] + UP_WINDOW[2], UP_START[3] + UP_WINDOW[3]) };
			case STEP_HEAD -> span(0, 0f, HEAD_SHELL_END);
			case STEP_FACEPLATE -> span(0, HEAD_SHELL_END, 1f);
			default -> new int[] { 0, 0 };
		};
	}

	private static int[] span(int bit, float a, float b) {
		return new int[] { Math.round(UP_START[bit] + UP_WINDOW[bit] * a), Math.round(UP_START[bit] + UP_WINDOW[bit] * b) };
	}

	// ---------------- body pose ----------------

	/** Both hands holding the case out in front. */
	private static final float[] HOLD = k(-0.95f, -0.42f, 0.05f, -0.95f, 0.42f, -0.05f, 0f, 0.04f, 0f, -0.04f, 0.25f, 0f);
	/** Hands pressing the chestplate onto the chest. */
	private static final float[] CHEST = k(-1.45f, -0.75f, 0f, -1.45f, 0.75f, 0f, 0f, 0.06f, 0f, -0.06f, 0.35f, 0f);
	/** Arms out to the side (T-pose-ish), feet a little apart, chin up. */
	private static final float[] SPREAD = k(0f, 0f, 1.45f, 0f, 0f, -1.45f, 0f, 0.12f, 0f, -0.12f, -0.1f, 0f);
	/** As {@link #SPREAD}, looking down at the legs. */
	private static final float[] SPREAD_LOOK_DOWN = k(0f, 0f, 1.4f, 0f, 0f, -1.4f, 0f, 0.14f, 0f, -0.14f, 0.5f, 0f);

	private static float[] k(float... v) {
		return v;
	}

	/**
	 * The body pose {@code age} ticks into a suit-up ({@code up}) or suit-down, as {weight, key...} in the
	 * {@link IronManSuitPoses} key layout, or null outside the sequence. Pure, so the gametests can read it.
	 */
	public static float[] pose(boolean up, float age) {
		if (age < 0f || age > UP_TICKS) {
			return null;
		}
		// v0.15.8, user request: the suit-down is the suit-up backwards, pose and all
		float f = frame(up, age);
		float w = Math.min(smooth(f / 5f), smooth((UP_TICKS - f) / 6f));
		float[] key;
		if (f < HOLD_TICKS) {
			key = HOLD; // the case held out
		} else if (f < HOLD_TICKS + 10) {
			key = lerp(HOLD, CHEST, smooth((f - HOLD_TICKS) / 10f)); // brought to the chest
		} else if (f < WINDOW[S_ARMS][0] - 6) {
			key = CHEST; // pressing the chestplate on while the chest builds
		} else if (f < WINDOW[S_ARMS][0] + 4) {
			key = lerp(CHEST, SPREAD, smooth((f - (WINDOW[S_ARMS][0] - 6)) / 10f)); // arms out as they build
		} else if (f < WINDOW[S_LEGS][0] - 4) {
			key = SPREAD;
		} else if (f < WINDOW[S_HELMET][0]) {
			float a = smooth((f - (WINDOW[S_LEGS][0] - 4)) / 8f);
			float b = smooth((f - (WINDOW[S_HELMET][0] - 8)) / 8f);
			key = lerp(lerp(SPREAD, SPREAD_LOOK_DOWN, a), SPREAD, b); // watching the legs build
		} else {
			key = SPREAD; // chin up for the helmet and faceplate
		}
		float[] out = new float[IronManSuitPoses.SIZE + 1];
		out[0] = w;
		System.arraycopy(key, 0, out, 1, IronManSuitPoses.SIZE);
		return out;
	}

	/**
	 * How big the suitcase drawn between the hands is at {@code age} (0 = not drawn .. 1 = full size): suit-up, it is
	 * held out and then shrinks into the chestplate; suit-down (backwards), it grows back out of the chestplate.
	 */
	public static float caseScale(boolean up, float age) {
		float f = frame(up, age);
		return f < 0f || f > UP_TICKS ? 0f : 1f - smooth((f - HOLD_TICKS) / MORPH_TICKS);
	}

	private static float[] lerp(float[] a, float[] b, float t) {
		float[] out = new float[a.length];
		for (int i = 0; i < a.length; i++) {
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
