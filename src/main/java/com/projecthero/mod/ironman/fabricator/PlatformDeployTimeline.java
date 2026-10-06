package com.projecthero.mod.ironman.fabricator;

import com.projecthero.mod.ironman.suit.IronManSuitPoses;

/**
 * v0.15.1: the timetable of the Suit Platform's robotic-arm deploy -- a pure function of the ticks since the sequence
 * started (the block entity syncs that start tick), so the server (which moves the real stacks), the client renderer
 * (which animates the two arms and the piece riding in a clamp) and the gametests all read the same numbers.
 *
 * <p>The whole suit-up always takes {@link #TOTAL} ticks (8 s), however many pieces are racked:
 * <ol>
 *   <li>{@code 0 .. LEAD}: the arms unfold from their folded rest against the gantry posts to a ready hover;</li>
 *   <li>one equal window per piece (boots, leggings, chestplate, helmet), each:
 *     <ul>
 *       <li>{@code 0 .. GRIP}: the carrying arm swings to the rack, jaws open, and closes them on the piece;</li>
 *       <li>{@code GRIP .. FIT}: it carries the piece off the rack and round onto the body, growing from rack size to
 *           full size and turning from the rack's heading to the player's -- at {@code FIT} the real stack moves from
 *           the rack to the armour slot in one server tick;</li>
 *       <li>{@code FIT .. LET_GO}: both arms hold it while the clamps lock (sparks, torque);</li>
 *       <li>{@code LET_GO .. 1}: the jaws open and the arms pull back to the ready hover;</li>
 *     </ul></li>
 *   <li>{@code TOTAL - OUTRO .. TOTAL}: the arms fold away; then the faceplate closes and the suit comes online.</li>
 * </ol>
 * The arm that carries alternates (right, left, right, left); the other one assists at the body.
 */
public final class PlatformDeployTimeline {
	/** The whole deploy: 8 seconds. */
	public static final int TOTAL = 160;
	/** Arms unfolding before the first piece. */
	public static final int LEAD = 20;
	/** Arms folding away after the last piece. */
	public static final int OUTRO = 20;
	/** Fractions of a piece's window. */
	public static final float GRIP_CLOSE = 0.22f;
	public static final float GRIP = 0.30f;
	public static final float FIT = 0.75f;
	public static final float LET_GO = 0.88f;
	/** Sneaking only cancels after this many ticks (a shift still held from the screen must not cancel it instantly). */
	public static final int CANCEL_GRACE = 10;

	private PlatformDeployTimeline() {
	}

	/** Ticks each piece gets: the 120 ticks between the lead-in and the fold-away, shared evenly. */
	public static int window(int pieces) {
		return (TOTAL - LEAD - OUTRO) / Math.max(1, pieces);
	}

	/** Tick (since the start) at which the i-th piece's window opens: the carrying arm leaves the ready hover. */
	public static int pieceStart(int i, int pieces) {
		return LEAD + i * window(pieces);
	}

	/** Tick at which the i-th piece's jaws have closed and it leaves the rack (drawn in the clamp from here). */
	public static int liftTick(int i, int pieces) {
		return pieceStart(i, pieces) + Math.round(window(pieces) * GRIP);
	}

	/** Tick at which the i-th piece reaches the body: the real stack moves rack -> armour slot in this one tick. */
	public static int equipTick(int i, int pieces) {
		return pieceStart(i, pieces) + Math.round(window(pieces) * FIT);
	}

	/** Tick at which the i-th piece's clamp lets go. */
	public static int letGoTick(int i, int pieces) {
		return pieceStart(i, pieces) + Math.round(window(pieces) * LET_GO);
	}

	/** Tick at which the arms start folding away. */
	public static int foldTick() {
		return TOTAL - OUTRO;
	}

	/** The piece window {@code t} falls in, or -1 during the lead-in / fold-away. */
	public static int pieceAt(float t, int pieces) {
		if (pieces <= 0 || t < LEAD || t >= TOTAL - OUTRO) {
			return -1;
		}
		return Math.min(pieces - 1, (int) ((t - LEAD) / window(pieces)));
	}

	/** How far through its window piece {@code i} is at {@code t} (0..1, clamped). */
	public static float pieceFrac(float t, int i, int pieces) {
		return clamp((t - pieceStart(i, pieces)) / (float) window(pieces));
	}

	/** Is the right arm (true) or the left arm the one that carries piece {@code i}? */
	public static boolean rightArmCarries(int i) {
		return (i & 1) == 0;
	}

	// ---------------- the wearer's body pose ----------------

	/**
	 * The wearer's pose {@code age} ticks into a {@code pieces}-piece deploy, as {weight, key...} in
	 * {@link IronManSuitPoses}' key layout: arms held out to the sides for the arms to work on, a slightly wide stance,
	 * and the head following the work up the body (looking down at the boots, chin up by the helmet). Eases in over 10
	 * ticks and out over the last 10, so it is gone exactly when the sequence ends.
	 */
	public static float[] pose(float age, int pieces) {
		float w = smooth(age / 10f) * smooth((TOTAL - age) / 10f);
		int n = Math.max(1, pieces);
		int i = pieceAt(age, n);
		float progress;
		if (age < LEAD) {
			progress = 0f;
		} else if (i < 0) {
			progress = 1f;
		} else {
			progress = clamp((i + smooth(pieceFrac(age, i, n) / GRIP)) / n);
		}
		float head = 0.62f - 0.70f * progress;
		float arm = 1.22f;
		float[] out = new float[1 + IronManSuitPoses.SIZE];
		out[0] = w;
		out[1 + IronManSuitPoses.RAX] = -0.12f;
		out[1 + IronManSuitPoses.RAZ] = arm;
		out[1 + IronManSuitPoses.LAX] = -0.12f;
		out[1 + IronManSuitPoses.LAZ] = -arm;
		out[1 + IronManSuitPoses.RLZ] = 0.10f;
		out[1 + IronManSuitPoses.LLZ] = -0.10f;
		out[1 + IronManSuitPoses.HX] = head;
		return out;
	}

	public static float smooth(float x) {
		x = clamp(x);
		return x * x * (3f - 2f * x);
	}

	public static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}
