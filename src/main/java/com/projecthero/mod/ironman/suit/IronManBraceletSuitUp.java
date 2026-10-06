package com.projecthero.mod.ironman.suit;

/**
 * v0.15.4: the Mark 7's quick <b>bracelet suit-up</b> ({@link SuitUpType#BRACELET_QUICK}) -- what happens when the Mark 7
 * goes on while the Colantotte Bracelets are worn (Stark Gear slot). Pure data and math (no client classes), so the server
 * sequence ({@link IronManSuitUpManager}), the synced clock ({@link IronManSuitFx#STYLE_BRACELET}), the client renderer
 * and the gametests all read the same numbers.
 *
 * <h2>Timeline ({@value #UP_TICKS} ticks = 4 s)</h2>
 * <ol>
 *   <li><b>Chestplate</b>, then <b>leggings</b>, then <b>boots</b> (overlapping a little): each piece arrives from behind
 *       the player already <em>split open down the front</em> -- its left and right halves swung out on hinges at the
 *       back corners like a clamshell -- settles onto the body, then the halves swing shut around the player and lock.
 *       The suit's own geometry is drawn twice as two half-shells (nothing is added to the user's models).</li>
 *   <li>The <b>helmet</b> swings up out of the back, hinged at the back of the neck, with no faceplate on it.</li>
 *   <li>The <b>faceplate</b> flips out over the brow into its raised position and then closes down -- the closing is the
 *       ordinary H faceplate swing ({@code IronManFaceplate} / {@code IronManFaceplateLook}): the server raises the
 *       faceplate flag when the helmet arrives and lowers it at {@link #FACEPLATE_CLOSE_AT}.</li>
 * </ol>
 */
public final class IronManBraceletSuitUp {
	/** 4 s, about a third of the ordinary 12 s piece-by-piece build. */
	public static final int UP_TICKS = 80;
	/** The tick each piece (bit 0 head, 1 chest, 2 legs, 3 feet) reaches the body and starts wrapping on. */
	private static final int[] UP_START = { 47, 1, 17, 31 };
	/** How long each piece takes to arrive and close. */
	private static final int[] UP_WINDOW = { 16, 22, 20, 18 };
	/** The tick the faceplate starts closing (the helmet has landed); the swing then takes {@code LIFT_TICKS}. */
	public static final int FACEPLATE_CLOSE_AT = 65;

	/** Share of a body piece's window spent sliding in (already open) from behind. */
	public static final float ARRIVE_END = 0.35f;
	/** By this share of its window the halves have swung shut. */
	public static final float CLOSE_END = 0.85f;
	/** How far (degrees) each half is swung open while the piece arrives. */
	public static final float OPEN_DEG = 100f;
	/** How far behind the body (pixels) an open piece starts. */
	public static final float SLIDE_PX = 9f;
	/** How big an arriving piece starts (it grows to full size as it settles). */
	public static final float START_SCALE = 0.6f;

	/** The helmet starts folded this far back (degrees about the hinge at the back of the neck), lying on the upper back. */
	public static final float HELMET_START_DEG = 150f;
	/** By this share of its window the helmet is home. */
	public static final float HELMET_LAND = 0.75f;
	/** The helmet's hinge, bedrock geometry pixels: the bottom-back edge of the helmet (back of the neck). */
	public static final float[] HELMET_HINGE = { 0f, 23.5f, 4.75f };
	/** The faceplate shows (flipped up over the brow) from this share of the helmet's window, then swings to raised. */
	public static final float FACEPLATE_SHOW_AT = 0.7f;
	/** Where the faceplate starts when it shows: pointing straight up off its hinge (degrees, raised = 90). */
	public static final float FACEPLATE_FLIP_DEG = 175f;

	private IronManBraceletSuitUp() {
	}

	public static int upStart(int bit) {
		return bit >= 0 && bit < 4 ? UP_START[bit] : -1;
	}

	public static int upWindow(int bit) {
		return bit >= 0 && bit < 4 ? UP_WINDOW[bit] : IronManSuitFx.BUILD_TICKS;
	}

	/** The tick the last body piece has closed and the helmet is home: the end of the piece windows. */
	public static int piecesDoneAt() {
		int end = 0;
		for (int bit = 0; bit < 4; bit++) {
			end = Math.max(end, UP_START[bit] + UP_WINDOW[bit]);
		}
		return end;
	}

	/** Do the pieces in {@code bit} split open down the front? Everything but the helmet (which swings up instead). */
	public static boolean splits(int bit) {
		return bit >= 1 && bit <= 3;
	}

	/** How far each half of a body piece is swung open (degrees) at its own progress {@code p}. */
	public static float openAngle(float p) {
		if (p < ARRIVE_END) {
			return OPEN_DEG;
		}
		if (p >= CLOSE_END) {
			return 0f;
		}
		float u = (p - ARRIVE_END) / (CLOSE_END - ARRIVE_END);
		// ease-in-out, with the last few degrees snapping shut a little faster (the clasp)
		float s = smooth(u);
		return OPEN_DEG * (1f - (s + 0.15f * s * s * (1f - s)) / 1f);
	}

	/** How far behind the body the piece still is (0..1 of {@link #SLIDE_PX}) at progress {@code p}. */
	public static float slide(float p) {
		float u = clamp(p / ARRIVE_END);
		float e = 1f - (1f - u) * (1f - u) * (1f - u); // ease-out: it decelerates onto the body
		return 1f - e;
	}

	/** The piece's scale at progress {@code p}: grows in while it arrives, then a small clamp pulse once it is shut. */
	public static float scale(float p) {
		if (p < ARRIVE_END) {
			float u = clamp(p / ARRIVE_END);
			float e = 1f - (1f - u) * (1f - u);
			return START_SCALE + (1f - START_SCALE) * e;
		}
		if (p >= CLOSE_END && p < 1f) {
			float u = (p - CLOSE_END) / (1f - CLOSE_END);
			return 1f + 0.035f * (float) Math.sin(Math.PI * u);
		}
		return 1f;
	}

	/** Is a body piece shut (its lights may come on)? */
	public static boolean closed(float p) {
		return p >= CLOSE_END;
	}

	/** The helmet's swing about {@link #HELMET_HINGE} (degrees; 0 = home) at its progress {@code p}. */
	public static float helmetAngle(float p) {
		if (p >= HELMET_LAND) {
			return 0f;
		}
		float u = clamp(p / HELMET_LAND);
		// ease-out with a soft landing
		float e = 1f - (1f - u) * (1f - u) * (1f - u);
		return HELMET_START_DEG * (1f - e);
	}

	/**
	 * The faceplate's angle about its own hinge (degrees, 0 shut, 90 raised) while the helmet is still coming in, or -1
	 * while it is not drawn at all (the helmet comes out of the back without it).
	 */
	public static float faceplateAngle(float p) {
		if (p < FACEPLATE_SHOW_AT) {
			return -1f;
		}
		float u = smooth((p - FACEPLATE_SHOW_AT) / (1f - FACEPLATE_SHOW_AT));
		return 90f + (FACEPLATE_FLIP_DEG - 90f) * (1f - u);
	}

	// ---------------- body pose ----------------

	private static float[] k(float... v) {
		return v;
	}

	/** Arms a little out and back so the chestplate can wrap round, chin up. */
	private static final float[] OPEN = k(0.15f, 0f, 0.55f, 0.15f, 0f, -0.55f, 0f, 0.10f, 0f, -0.10f, -0.12f, 0f);
	/** Looking down while the legs and boots close, feet apart. */
	private static final float[] LEGS = k(0.08f, 0f, 0.62f, 0.08f, 0f, -0.62f, 0f, 0.17f, 0f, -0.17f, 0.5f, 0f);
	/** Head a little bowed while the helmet swings up over it. */
	private static final float[] HELMET = k(0f, 0f, 0.38f, 0f, 0f, -0.38f, 0f, 0.1f, 0f, -0.1f, 0.18f, 0f);
	/** The faceplate seals: arms settling, head level. */
	private static final float[] SEAL = k(0f, 0f, 0.12f, 0f, 0f, -0.12f, 0f, 0.06f, 0f, -0.06f, -0.06f, 0f);

	/**
	 * The body pose {@code age} ticks into the bracelet suit-up, as {weight, key...} in the {@link IronManSuitPoses} key
	 * layout, or null outside it. Pure, so the gametests can read it.
	 */
	public static float[] pose(float age) {
		if (age < 0f || age > UP_TICKS) {
			return null;
		}
		float w = Math.min(smooth(age / 5f), smooth((UP_TICKS - age) / 8f));
		float[] key;
		if (age < UP_START[2]) {
			key = OPEN;
		} else if (age < UP_START[2] + 10) {
			key = lerp(OPEN, LEGS, smooth((age - UP_START[2]) / 10f));
		} else if (age < UP_START[0] - 7) {
			key = LEGS;
		} else if (age < UP_START[0] + 3) {
			key = lerp(LEGS, HELMET, smooth((age - (UP_START[0] - 7)) / 10f));
		} else if (age < FACEPLATE_CLOSE_AT - 3) {
			key = HELMET;
		} else {
			key = lerp(HELMET, SEAL, smooth((age - (FACEPLATE_CLOSE_AT - 3)) / 8f));
		}
		float[] out = new float[IronManSuitPoses.SIZE + 1];
		out[0] = w;
		System.arraycopy(key, 0, out, 1, IronManSuitPoses.SIZE);
		return out;
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
