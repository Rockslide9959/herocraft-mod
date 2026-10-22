package com.projecthero.mod.ironman.suit;

import java.util.Arrays;
import java.util.function.Supplier;

import com.projecthero.mod.ironman.gantry.GantryTimeline;

import net.minecraft.world.item.Item;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: one suit's <b>portable form</b> -- what it packs away into, and how it deploys
 * out of it (C) and folds back into it (hold Shift+C). Pure data + pure math (no client classes), so the server sequence
 * ({@link PortableDeploy}), the synced clock ({@link IronManSuitFx} pose kinds from {@link PortableForms#poseKind}), the
 * client renderers and the gametests all read the same numbers. See {@code docs/IRONMAN_ERA2_FOLD.md}.
 *
 * <h2>The timetable</h2>
 * Everything runs on one <b>frame</b> clock of {@link Beats#total} ticks: a deploy plays frames 0 -> total, a fold plays
 * the very same frames backwards (total -> 0, {@link #frame}). On it:
 * <ul>
 *   <li>{@link Beats}: when the packed prop has left the hand / back ({@code dropFrom}), landed ({@code dropTo}), is fully
 *       open ({@code openTo}), when the faceplate seals ({@code sealAt}), when the empty prop starts closing
 *       ({@code closeFrom}) and the end ({@code total});</li>
 *   <li>{@link #stageWindows}: one {from, to} frame window per Stark Gantry stage ({@link GantryTimeline#TORSO_TOP} ..
 *       {@link GantryTimeline#FACEPLATE}). The client build ({@code IronManGantryBuild} portable scheme) sorts every texel
 *       of the worn piece into those stages exactly as the gantry does -- base plates SLIDE out from under the part before
 *       them, every other part builds on texel by texel, no glowing seam -- and shows it once the frame reaches it.
 *       {@link #direction} says which way a texel stage builds (up the body or down it).</li>
 *   <li>{@link #pieceOnAt}: the deploy frame the server puts each real armour piece on the body (the first frame any of
 *       its stages starts), so a piece is always in its slot before its first texel shows.</li>
 * </ul>
 *
 * @param suitId       the suit ({@code IronManSuits} id) this form packs
 * @param packedItem   the item the packed suit is (holds the 4 real armour stacks, {@code SuitcaseContents}); supplier so
 *                     the registry can be built before the items are registered
 * @param rest         where the packed form sits when not deployed (hand, back, block)
 * @param during       where the prop is while it deploys / folds (floor in front, on the back, a block)
 * @param beats        the prop's timetable
 * @param stageWindows [GantryTimeline.STAGES][2] frame windows, {-1, -1} = the stage is not used
 * @param direction    which way texel stages build
 * @param propId       the client prop renderer key ({@code IronManPortableClient#registerProp})
 * @param poseKeys     body-pose keyframes {frame, 12 x {@link IronManSuitPoses} key...}, frame-ascending
 */
public record PortableForm(String suitId, Supplier<Item> packedItem, Place rest, Place during, Beats beats,
		int[][] stageWindows, Direction direction, String propId, float[][] poseKeys) {

	/** Where a prop sits. */
	public enum Place {
		/** Carried in the right hand (an item). */
		HAND,
		/** Worn on the back. */
		BACK,
		/** On the floor in front of the wearer, anchored where the deploy / fold started. */
		FLOOR,
		/** A placed block (the Hulkbuster crate). */
		BLOCK
	}

	/** Which way a texel stage builds (its slide stages always slide out from their seam). */
	public enum Direction {
		/** Bottom-up: boots from the soles, legs from the knees, ... (Mk VI briefcase). */
		CLIMB,
		/** Top-down (a pack on the back wrapping down over the body). */
		DROP
	}

	/**
	 * The prop's beats on the frame clock (deploy direction).
	 *
	 * @param dropFrom  the prop leaves its rest place (hand / back)
	 * @param dropTo    it has landed where it deploys from
	 * @param openTo    it is fully open
	 * @param sealAt    the faceplate closes (fold: it opens at this frame)
	 * @param closeFrom the emptied prop starts closing / packing away
	 * @param total     the end of the deploy
	 */
	public record Beats(int dropFrom, int dropTo, int openTo, int sealAt, int closeFrom, int total) {
	}

	/** The piece (0 HEAD .. 3 FEET) a gantry stage belongs to; PANTS also covers the boots' outer layer. */
	private static final int[] STAGE_BIT = new int[GantryTimeline.STAGES];

	static {
		for (int st = 0; st < GantryTimeline.STAGES; st++) {
			STAGE_BIT[st] = switch (st) {
				case GantryTimeline.HELMET, GantryTimeline.FACEPLATE -> 0;
				case GantryTimeline.R_BOOT, GantryTimeline.L_BOOT -> 3;
				case GantryTimeline.THIGH_TOP, GantryTimeline.THIGH_BOTTOM, GantryTimeline.PANTS -> 2;
				default -> 1;
			};
		}
	}

	/** The frame of the clock {@code age} ticks into a deploy ({@code up}) or a fold (the same frames backwards). */
	public float frame(boolean up, float age) {
		return up ? age : beats.total() - age;
	}

	/** Is stage {@code st} part of this form's build? */
	public boolean uses(int st) {
		return st >= 0 && st < stageWindows.length && stageWindows[st][0] >= 0;
	}

	/** The frame a texel of stage {@code st} at build position {@code k} (0..1) is on. NaN if the stage is unused. */
	public float appear(int st, float k) {
		if (!uses(st)) {
			return Float.NaN;
		}
		int[] w = stageWindows[st];
		return w[0] + (w[1] - w[0]) * (0.02f + 0.96f * clamp(k));
	}

	/** The first frame any of {@code stages} starts (Integer.MAX_VALUE if none is used). */
	public int firstAppear(int... stages) {
		int best = Integer.MAX_VALUE;
		for (int st : stages) {
			if (uses(st)) {
				best = Math.min(best, stageWindows[st][0]);
			}
		}
		return best;
	}

	/** The last frame of the whole build (every texel on). */
	public int lastAppear() {
		int last = 0;
		for (int st = 0; st < stageWindows.length; st++) {
			if (uses(st)) {
				last = Math.max(last, stageWindows[st][1]);
			}
		}
		return last;
	}

	/** Deploy: the frame piece {@code bit} (0 HEAD .. 3 FEET) is put on the body -- its first stage's start. */
	public int pieceOnAt(int bit) {
		int best = Integer.MAX_VALUE;
		for (int st = 0; st < GantryTimeline.STAGES; st++) {
			if (uses(st) && (STAGE_BIT[st] == bit || bit == 3 && st == GantryTimeline.PANTS)) {
				best = Math.min(best, stageWindows[st][0]);
			}
		}
		return best == Integer.MAX_VALUE ? beats.dropTo() : best;
	}

	/** Which piece a gantry stage belongs to (0 HEAD .. 3 FEET). */
	public static int bitOf(int stage) {
		return stage >= 0 && stage < STAGE_BIT.length ? STAGE_BIT[stage] : -1;
	}

	/**
	 * The body pose at {@code frame}, as {weight, key...} in the {@link IronManSuitPoses} layout (null outside the clock):
	 * a blend of {@link #poseKeys}, eased in over the first 5 frames and out over the last 6. Pure -- every viewer and the
	 * gametests read the same pose, and the fold (frames backwards) mirrors the deploy.
	 */
	public float[] pose(float frame) {
		if (poseKeys == null || poseKeys.length == 0 || frame < 0f || frame > beats.total()) {
			return null;
		}
		float w = Math.min(smooth(frame / 5f), smooth((beats.total() - frame) / 6f));
		float[] key;
		if (frame <= poseKeys[0][0]) {
			key = Arrays.copyOfRange(poseKeys[0], 1, 1 + IronManSuitPoses.SIZE);
		} else if (frame >= poseKeys[poseKeys.length - 1][0]) {
			key = Arrays.copyOfRange(poseKeys[poseKeys.length - 1], 1, 1 + IronManSuitPoses.SIZE);
		} else {
			int i = 0;
			while (i < poseKeys.length - 2 && frame > poseKeys[i + 1][0]) {
				i++;
			}
			float[] a = poseKeys[i];
			float[] b = poseKeys[i + 1];
			float t = smooth((frame - a[0]) / Math.max(0.001f, b[0] - a[0]));
			key = new float[IronManSuitPoses.SIZE];
			for (int j = 0; j < IronManSuitPoses.SIZE; j++) {
				key[j] = a[j + 1] + (b[j + 1] - a[j + 1]) * t;
			}
		}
		float[] out = new float[IronManSuitPoses.SIZE + 1];
		out[0] = w;
		System.arraycopy(key, 0, out, 1, IronManSuitPoses.SIZE);
		return out;
	}

	// ---------------- building a form ----------------

	/** A fresh stage table with every stage unused. */
	public static int[][] noStages() {
		int[][] w = new int[GantryTimeline.STAGES][];
		for (int i = 0; i < w.length; i++) {
			w[i] = new int[] { -1, -1 };
		}
		return w;
	}

	/** Sets stage windows on {@code table}: {@code stage, from, to} triples. Returns the table. */
	public static int[][] stages(int[][] table, int... triples) {
		for (int i = 0; i + 2 < triples.length; i += 3) {
			table[triples[i]] = new int[] { triples[i + 1], triples[i + 2] };
		}
		return table;
	}

	/** One body-pose keyframe: the frame, then a 12-angle key (see {@link IronManSuitPoses}). */
	public static float[] poseKey(float frame, float rax, float ray, float raz, float lax, float lay, float laz, float rlx,
			float rlz, float llx, float llz, float hx, float hy) {
		return new float[] { frame, rax, ray, raz, lax, lay, laz, rlx, rlz, llx, llz, hx, hy };
	}

	static float smooth(float x) {
		x = clamp(x);
		return x * x * (3f - 2f * x);
	}

	static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}
