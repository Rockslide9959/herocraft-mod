package com.projecthero.mod.ironman.gantry;

import com.projecthero.mod.ironman.suit.IronManSuitPoses;

/**
 * v0.15.4: the timetable of a Stark Gantry suit-up -- a pure function of the frame (ticks since the sequence started),
 * so the server (which moves the real stacks), the client renderer (lift, hatches, arms, the piece in a clamp), the
 * wearer's pose and the gametests all read the same numbers.
 *
 * <p>A suit-up always takes {@link #TOTAL} ticks (10 s), however many pieces there are:
 * <ol>
 *   <li>{@code 0 .. LEAD}: the centre lift raises the wearer {@link #LIFT} blocks, the hatches on the wearer's left,
 *       right and in front of them slide open, two arm masts telescope up out of the side hatches and the arms unfold;</li>
 *   <li>one equal window per piece (boots, leggings, chestplate, helmet), each:
 *     <ul>
 *       <li>{@code 0 .. ELEVATOR_UP}: the piece rides an elevator up out of the front hatch while the carrying arm
 *           reaches over it;</li>
 *       <li>{@code .. GRIP}: the jaws close on it;</li>
 *       <li>{@code GRIP .. FIT}: the arm lifts it off the elevator and carries it onto the body -- at {@code FIT} the
 *           real stack moves from the floor to the armour slot in one server tick (the elevator sinks back meanwhile);</li>
 *       <li>{@code FIT .. LET_GO}: both arms hold it while the clamps lock (sparks);</li>
 *       <li>{@code LET_GO .. 1}: the jaws open and the arm pulls back to its ready hover;</li>
 *     </ul></li>
 *   <li>{@code TOTAL - OUTRO .. TOTAL}: the lead-in in reverse -- the arms fold, sink into the floor, the hatches
 *       close and the lift lowers; then the faceplate closes and the suit comes online.</li>
 * </ol>
 * The lead-in and the outro are mirror images ({@link #edge}), so taking a suit OFF is exactly this timetable played
 * backwards ({@link #frame}): the arms come up, take the helmet off first, lower each piece into the floor on the
 * elevator, and fold away.
 */
public final class GantryTimeline {
	/** The whole sequence: 10 seconds. */
	public static final int TOTAL = 200;
	/** Lift + hatches + arms coming up, before the first piece (and the same, reversed, after the last). */
	public static final int LEAD = 40;
	public static final int OUTRO = 40;
	/** How high the centre lift raises the wearer (blocks). */
	public static final float LIFT = 0.5f;

	public static final int LIFT_TO = 14;
	public static final int HATCH_FROM = 3;
	public static final int HATCH_TO = 15;
	public static final int RISE_FROM = 14;
	public static final int RISE_TO = 32;
	public static final int UNFOLD_FROM = 30;
	public static final int UNFOLD_TO = 40;

	/** Fractions of a piece's window. */
	public static final float REACH = 0.22f;
	public static final float ELEVATOR_UP = 0.28f;
	public static final float GRIP_CLOSE = 0.26f;
	public static final float GRIP = 0.36f;
	public static final float SINK_FROM = 0.40f;
	public static final float SINK_TO = 0.70f;
	public static final float FIT = 0.75f;
	public static final float LET_GO = 0.88f;
	/** Sneaking only cancels after this many ticks (a shift still held from the screen must not cancel it at once). */
	public static final int CANCEL_GRACE = 10;

	private GantryTimeline() {
	}

	/** The frame a sequence shows {@code t} ticks in: the timetable itself on, backwards off. */
	public static float frame(boolean equip, float t) {
		return equip ? t : TOTAL - t;
	}

	/** Ticks each piece gets: the 120 between the lead-in and the outro, shared evenly. */
	public static int window(int pieces) {
		return (TOTAL - LEAD - OUTRO) / Math.max(1, pieces);
	}

	public static int pieceStart(int i, int pieces) {
		return LEAD + i * window(pieces);
	}

	/** Frame at which the i-th piece's jaws have closed and it leaves the elevator. */
	public static int liftTick(int i, int pieces) {
		return pieceStart(i, pieces) + Math.round(window(pieces) * GRIP);
	}

	/** Frame at which the i-th piece reaches the body: the real stack moves floor -> armour slot in this one tick. */
	public static int equipTick(int i, int pieces) {
		return pieceStart(i, pieces) + Math.round(window(pieces) * FIT);
	}

	/** Frame at which the i-th piece's clamp lets go. */
	public static int letGoTick(int i, int pieces) {
		return pieceStart(i, pieces) + Math.round(window(pieces) * LET_GO);
	}

	/** Taking the suit off: the tick at which the jaws close on (put-on-order) piece {@code i} on the body. */
	public static int clampTick(int i, int pieces) {
		return TOTAL - letGoTick(i, pieces);
	}

	/** Taking the suit off: the tick at which piece {@code i} comes off the body -- armour slot -> floor in one tick. */
	public static int removeTick(int i, int pieces) {
		return TOTAL - equipTick(i, pieces);
	}

	/** Taking the suit off: the tick at which piece {@code i} is set down on the elevator. */
	public static int stowTick(int i, int pieces) {
		return TOTAL - liftTick(i, pieces);
	}

	/** The piece window frame {@code f} falls in, or -1 during the lead-in / outro. */
	public static int pieceAt(float f, int pieces) {
		if (pieces <= 0 || f < LEAD || f >= TOTAL - OUTRO) {
			return -1;
		}
		return Math.min(pieces - 1, (int) ((f - LEAD) / window(pieces)));
	}

	/** How far through its window piece {@code i} is at frame {@code f} (0..1, clamped). */
	public static float pieceFrac(float f, int i, int pieces) {
		return clamp((f - pieceStart(i, pieces)) / (float) window(pieces));
	}

	/** Is the right arm (true) or the left arm the one that carries piece {@code i}? */
	public static boolean rightArmCarries(int i) {
		return (i & 1) == 0;
	}

	// ---------------- the lead-in / outro (mirror images) ----------------

	/** Frames into the lead-in, or out of the end of the outro -- whichever edge {@code f} is nearer. */
	public static float edge(float f) {
		return Math.min(f, TOTAL - f);
	}

	/** Lift height above the floor top (blocks). */
	public static float lift(float f) {
		return LIFT * smooth(edge(f) / LIFT_TO);
	}

	/** Hatch panels: 0 shut .. 1 fully slid open. */
	public static float hatch(float f) {
		return smooth((edge(f) - HATCH_FROM) / (float) (HATCH_TO - HATCH_FROM));
	}

	/** Arm masts: 0 sunk in the floor .. 1 fully up. */
	public static float rise(float f) {
		return smooth((edge(f) - RISE_FROM) / (float) (RISE_TO - RISE_FROM));
	}

	/** Arms: 0 folded upright .. 1 at the ready hover. */
	public static float unfold(float f) {
		return smooth((edge(f) - UNFOLD_FROM) / (float) (UNFOLD_TO - UNFOLD_FROM));
	}

	/** The front elevator during a piece window: 0 sunk .. 1 up at the floor (u = the window fraction). */
	public static float elevator(float u) {
		if (u < SINK_FROM) {
			return smooth(u / ELEVATOR_UP);
		}
		return 1f - smooth((u - SINK_FROM) / (SINK_TO - SINK_FROM));
	}

	// ---------------- the wearer's body pose ----------------

	/**
	 * The wearer's pose at frame {@code f} of a {@code pieces}-piece sequence, as {weight, key...} in
	 * {@link IronManSuitPoses}' key layout: arms held down and out (clear of the arm masts at their sides), a slightly
	 * wide stance, and the head following the work up the body. Eases in over 10 frames and out over the last 10.
	 */
	public static float[] pose(float f, int pieces) {
		float w = smooth(f / 10f) * smooth((TOTAL - f) / 10f);
		int n = Math.max(1, pieces);
		int i = pieceAt(f, n);
		float progress;
		if (f < LEAD) {
			progress = 0f;
		} else if (i < 0) {
			progress = 1f;
		} else {
			progress = clamp((i + smooth(pieceFrac(f, i, n) / GRIP)) / n);
		}
		float head = 0.55f - 0.65f * progress;
		float arm = 0.62f;
		float[] out = new float[1 + IronManSuitPoses.SIZE];
		out[0] = w;
		out[1 + IronManSuitPoses.RAX] = -0.18f;
		out[1 + IronManSuitPoses.RAZ] = arm;
		out[1 + IronManSuitPoses.LAX] = -0.18f;
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
