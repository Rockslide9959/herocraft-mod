package com.projecthero.mod.ironman.suit;

import java.util.List;

/**
 * v0.14.21 self-assembly: the per-bone timetable an Iron Man piece builds itself on by. Pure math, no client classes, so
 * the renderer ({@code client.ironman.IronManAssemblyClient}), the tile reveal ({@code client.ironman.IronManAssemblyReveal})
 * and the gametests all read the same plan.
 *
 * <p>A piece's lock-on window ({@link IronManSuitFx#LOCK_TICKS}) is normalised to progress {@code p} 0..1. Its bones are
 * split into groups that fly in one after another in a mechanical order; group {@code g} of {@code G} starts at
 * {@code g * (END - MOTION) / (G - 1)} and takes {@link #MOTION} of the window: it eases in on an easeOutBack curve (a few
 * percent past home, then settling) and locks home at {@link #SNAP} of its own motion -- continuous every frame. Every bone is home by {@link #END} (< 1), before the window closes, so a
 * piece is always whole by the time the lock-on clock runs out. The release ({@link IronManSuitFx#RELEASE_TICKS}) plays the
 * same timetable backwards (p 1 -> 0), so the last bone in is the first one out.
 */
public final class IronManAssemblyPlan {
	/** Share of the window one bone's motion takes. */
	public static final float MOTION = 0.36f;
	/** Every bone is home by this much of the window. */
	public static final float END = 0.9f;
	/** Within a bone's motion: the moment it locks home (tiles have finished flipping on by then). */
	public static final float SNAP = 0.82f;
	/** Within a bone's motion: the plate tiles flip on over [0, TILE_END). */
	public static final float TILE_END = 0.78f;
	/** easeOutBack strength of the fly-in: 1.2 swings about 6% past home before settling (the servo feel). */
	public static final float OVERSHOOT = 1.2f;

	/** Bedrock geometry coordinates (+x = the wearer's left, -z = front): the right hand, where the Mark V case is held. */
	public static final float[] CASE_HAND = { -6.0f, 12.0f, -1.0f };
	/** The arc reactor (bedrock coordinates): plate tiles flip on outward from here. */
	public static final float[] REACTOR = { 0.0f, 18.7f, -3.0f };

	private static final List<List<String>> HEAD = List.of(
			List.of("helmet"), List.of("helmet_brow"), List.of("faceplate"));
	private static final List<List<String>> CHEST = List.of(
			List.of("arc_reactor"), List.of("chest_armor", "chest"), List.of("waist", "back_panel"),
			List.of("right_shoulder", "left_shoulder"), List.of("right_upper_arm", "left_upper_arm"),
			List.of("right_gauntlet", "left_gauntlet"));
	private static final List<List<String>> LEGS = List.of(
			List.of("right_thigh", "left_thigh"), List.of("right_thigh_plate", "left_thigh_plate"),
			List.of("right_knee", "left_knee"));
	private static final List<List<String>> FEET = List.of(List.of("right_boot"), List.of("left_boot"));

	/** Mark V: outward from the case in the right hand -- right arm first, then the torso, then the left arm. */
	private static final List<List<String>> CASE_CHEST = List.of(
			List.of("right_gauntlet"), List.of("right_upper_arm"), List.of("right_shoulder"), List.of("arc_reactor"),
			List.of("chest_armor", "chest"), List.of("waist", "back_panel"), List.of("left_shoulder"),
			List.of("left_upper_arm"), List.of("left_gauntlet"));
	private static final List<List<String>> CASE_LEGS = List.of(
			List.of("right_thigh"), List.of("right_thigh_plate", "right_knee"), List.of("left_thigh"),
			List.of("left_thigh_plate", "left_knee"));

	// ---------------- v0.14.27: the 3 s build-on (STYLE_PLATES lock-on) ----------------

	/**
	 * v0.14.27: an ordinary suit-up builds each piece over {@link IronManSuitFx#BUILD_TICKS}: the <b>base layer</b> (the
	 * {@code base_*} skin-rig cubes) closes in two halves over [0, {@link #BUILD_BASE_END}), then the <b>outer shell</b>
	 * (the inflated shell cubes and every detail bone) fills in one texel at a time over [{@link #BUILD_BASE_END},
	 * {@link #BUILD_SHELL_END}). Nothing flies in -- the piece grows on the body where it sits.
	 */
	public static final float BUILD_BASE_END = 0.3f;
	/** v0.14.27: the last shell texel is on by here (the rest of the window is the finished piece settling / glowing on). */
	public static final float BUILD_SHELL_END = 0.96f;

	/** v0.14.27: is this a base-layer (skin rig) cube? The geo names them {@code base_head}, {@code base_body}, ... */
	public static boolean isBaseCube(String cubeName) {
		return cubeName != null && cubeName.startsWith("base_");
	}

	/** v0.14.27: how far the base halves have closed at piece progress {@code p}: 0 apart .. 1 sealed (smoothstep). */
	public static float halvesClosed(float p) {
		float x = clamp(p / BUILD_BASE_END);
		return x * x * (3f - 2f * x);
	}

	/**
	 * v0.14.27: when a base texel at normalised distance {@code fromSeam} (0 on the seam .. 1 at the far edge of its half)
	 * appears: the far edges first, the two halves growing toward each other and meeting on the seam at
	 * {@link #BUILD_BASE_END}.
	 */
	public static float baseTexelTime(float fromSeam) {
		return BUILD_BASE_END * (1f - clamp(fromSeam)) * 0.95f;
	}

	/** v0.14.27: when the shell texel ranked {@code rank} of {@code count} (0 first) flips on -- one after another. */
	public static float shellTexelTime(int rank, int count) {
		float k = count <= 1 ? 0f : rank / (float) (count - 1);
		return BUILD_BASE_END + (BUILD_SHELL_END - BUILD_BASE_END) * k;
	}

	/** v0.14.27: where piece {@code bit}'s shell starts building from (bedrock coordinates): it spreads out from here. */
	public static float[] shellOrigin(int bit) {
		return switch (bit) {
			case 0 -> new float[] { 0f, 24f, -4f };  // the jaw line, up over the face to the crown
			case 1 -> REACTOR;                      // outward from the arc reactor
			case 2 -> new float[] { 0f, 0f, -2f };   // up from the ankles
			default -> new float[] { 0f, -1f, -3f }; // the toes, back to the heels
		};
	}

	private IronManAssemblyPlan() {
	}

	/** The fly-in groups of piece {@code bit} (0 head, 1 chest, 2 legs, 3 feet), in order. */
	public static List<List<String>> groups(int bit, boolean fromCase) {
		return switch (bit) {
			case 0 -> HEAD;
			case 1 -> fromCase ? CASE_CHEST : CHEST;
			case 2 -> fromCase ? CASE_LEGS : LEGS;
			case 3 -> FEET;
			default -> List.of();
		};
	}

	/** Which group {@code bone} is in, or -1 if it is not one of the piece's moving bones. */
	public static int groupOf(int bit, String bone, boolean fromCase) {
		List<List<String>> g = groups(bit, fromCase);
		for (int i = 0; i < g.size(); i++) {
			if (g.get(i).contains(bone)) {
				return i;
			}
		}
		return -1;
	}

	/** The piece (0..3) a bone belongs to, or -1. */
	public static int pieceOf(String bone) {
		for (int bit = 0; bit < 4; bit++) {
			if (groupOf(bit, bone, false) >= 0) {
				return bit;
			}
		}
		return -1;
	}

	/** Where in the window (0..1) {@code bone} starts moving, or -1 if it is not part of the plan. */
	public static float start(int bit, String bone, boolean fromCase) {
		int g = groupOf(bit, bone, fromCase);
		if (g < 0) {
			return -1f;
		}
		int n = groups(bit, fromCase).size();
		return n <= 1 ? 0f : g * (END - MOTION) / (n - 1);
	}

	/** Where in the window {@code bone} snaps home ({@link #start} + {@link #SNAP} of its motion), or -1. */
	public static float snapAt(int bit, String bone, boolean fromCase) {
		float s = start(bit, bone, fromCase);
		return s < 0f ? -1f : s + MOTION * SNAP;
	}

	/** {@code bone}'s own motion, 0 (not started) .. 1 (home), at piece progress {@code p}. Unplanned bones are always 1. */
	public static float local(int bit, String bone, boolean fromCase, float p) {
		float s = start(bit, bone, fromCase);
		if (s < 0f) {
			return 1f;
		}
		return clamp((p - s) / MOTION);
	}

	/** Has {@code bone} snapped home at piece progress {@code p}? (Glow, sparks.) */
	public static boolean snapped(int bit, String bone, boolean fromCase, float p) {
		return local(bit, bone, fromCase, p) >= SNAP;
	}

	/**
	 * How far {@code bone} is from home at bone-local motion {@code t} (0..1), as a fraction of its full displacement:
	 * 1 = fully exploded, 0 = home, slightly negative = overshooting inward. Locking on it eases in on an easeOutBack
	 * curve that overshoots a little and lands on exactly 0 at {@link #SNAP}. Releasing ({@code assembling} false, t
	 * running 1 -> 0) it eases out of the lock and accelerates away. Continuous in t on both paths (no frame jumps).
	 */
	public static float displacement(float t, boolean assembling) {
		if (t >= SNAP) {
			return 0f;
		}
		if (t <= 0f) {
			return 1f;
		}
		if (assembling) {
			// easeOutBack: decelerates in, swings a few percent past home and settles exactly on it at SNAP --
			// continuous every frame (no jump), so nothing stutters
			float x = t / SNAP - 1f;
			float back = 1f + (OVERSHOOT + 1f) * x * x * x + OVERSHOOT * x * x;
			return 1f - back;
		}
		float r = 1f - t / SNAP; // 0 at the moment it unlocks .. 1 gone
		return r * r * (2f - r); // eases out of the lock, then accelerates away -- continuous from 0
	}

	/** Scale of a bone at displacement {@code d} with its start scale {@code s0}: s0 fully out, 1 home, a hair over 1 on the overshoot. */
	public static float scale(float d, float s0) {
		return d <= 0f ? 1f - d * 0.25f : 1f - (1f - s0) * Math.min(1f, d);
	}

	/** A stable 0..1 hash of a bone name and a salt (deterministic for every viewer). */
	public static float hash(String name, int salt) {
		int h = name.hashCode() * 0x9E3779B1 + salt * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFF) / 65535f;
	}

	/** Displacement distance (px) of a bone fully exploded: 3..8. */
	public static float distance(String bone) {
		return 3f + 5f * hash(bone, 1);
	}

	/** Start rotation (degrees) of a bone fully exploded: 10..35, sign from the hash. */
	public static float tilt(String bone) {
		float deg = 10f + 25f * hash(bone, 2);
		return hash(bone, 3) < 0.5f ? -deg : deg;
	}

	/** Which axis the tilt is about: 0 x, 1 y, 2 z. */
	public static int tiltAxis(String bone) {
		return Math.min(2, (int) (hash(bone, 4) * 3f));
	}

	/** Start scale of a bone fully exploded: 0.6..0.8. */
	public static float startScale(String bone) {
		return 0.6f + 0.2f * hash(bone, 5);
	}

	/**
	 * The outward direction a bone flies in from, in bedrock geometry coordinates (+x = the wearer's left, +y up, -z
	 * front), unit length. Right-side bones mirror their left twins.
	 */
	public static float[] outward(String bone) {
		boolean right = bone.startsWith("right_");
		String base = bone.startsWith("right_") ? bone.substring(6) : bone.startsWith("left_") ? bone.substring(5) : bone;
		float[] d = switch (base) {
			case "helmet" -> new float[] { 0f, 0.8f, 0.6f };
			case "helmet_brow" -> new float[] { 0f, 0.7f, -0.7f };
			case "faceplate" -> new float[] { 0f, 1f, -0.35f };
			case "chest", "chest_armor" -> new float[] { 0f, 0.15f, -1f };
			case "arc_reactor" -> new float[] { 0f, 0f, -1f };
			case "waist" -> new float[] { 0f, -0.5f, -0.85f };
			case "back_panel" -> new float[] { 0f, 0.2f, 1f };
			case "shoulder" -> new float[] { 1f, 0.7f, 0f };
			case "upper_arm" -> new float[] { 1f, 0.1f, 0.2f };
			case "gauntlet" -> new float[] { 0.6f, -1f, -0.2f };
			case "thigh" -> new float[] { 0.7f, 0f, -0.7f };
			case "thigh_plate" -> new float[] { 0.3f, 0f, -1f };
			case "knee" -> new float[] { 0.1f, -0.2f, -1f };
			case "boot" -> new float[] { 0.3f, -1f, -0.2f };
			default -> new float[] { 0f, 0f, -1f };
		};
		if (right) {
			d[0] = -d[0];
		}
		float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
		return new float[] { d[0] / len, d[1] / len, d[2] / len };
	}

	static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}
