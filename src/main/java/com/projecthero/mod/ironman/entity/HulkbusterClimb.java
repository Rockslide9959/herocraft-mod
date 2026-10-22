package com.projecthero.mod.ironman.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

/**
 * v0.16.7 (user) Hulkbuster fixes: "when going into sentry mode of the hulkbuster the player needs to come out from the
 * back of the suit ... also add an animation of the player climbing out the suit since its so big". The pilot's path out
 * of (and into) the Hulkbuster's open back, shared by the server (which carries the pilot along it as a passenger of the
 * {@link IronManSentryEntity}, see {@code positionRider}) and the client (whose limb / body keys in
 * {@code HulkbusterClimbPose} are timed to the same ticks), so every viewer sees the same climb.
 *
 * <p>Frame: {@code x} = blocks BEHIND the suit (along minus its facing), {@code y} = blocks above its feet. The back plate
 * hinges at its bottom edge (y ~1.09, 0.23 behind the centre) and drops open to a ramp reaching ~1.37 behind at y ~1.46.
 * <ul>
 *   <li><b>Out</b> ({@link #OUT_TICKS} = 2.3 s, facing away from the suit): stood inside at chest height while the back
 *       swings open -> reach up for the rim -> pull up and over it, crawling out along the open plate -> knees on the
 *       plate's end -> hop off -> drop to the ground {@link #DROP_BACK} behind the suit and stand.</li>
 *   <li><b>In</b> ({@link #IN_TICKS} = 1.8 s, facing the suit): crouch -> jump up onto the open plate -> crawl in over the
 *       rim -> settle inside at chest height (then the back closes round them).</li>
 * </ul>
 */
public final class HulkbusterClimb {
	public static final int OUT_TICKS = 46;
	public static final int IN_TICKS = 36;
	/** Where the climb-out lands / the climb-in starts: this far behind the suit, on the ground. */
	public static final double DROP_BACK = 1.7;
	/** The pilot's feet while stood inside the suit. */
	public static final float INSIDE_Y = 0.85f;
	/** The tick of the climb-out at which the pilot lets go of the plate (a hop, then the fall)... */
	public static final int OUT_HOP = 35;
	/** ...and lands. */
	public static final int OUT_LAND = 40;
	/** The tick of the climb-in at which they jump... */
	public static final int IN_JUMP = 6;
	/** ...and land on the plate. */
	public static final int IN_ON_PLATE = 12;

	// keys: tick, behind, up
	private static final float[][] OUT = {
			{ 0, 0.00f, INSIDE_Y },
			{ 8, 0.00f, 0.95f },
			{ 14, 0.08f, 1.10f },
			{ 22, 0.45f, 1.32f },
			{ 28, 0.85f, 1.30f },
			{ 32, 1.05f, 1.32f },
			{ OUT_HOP, 1.25f, 1.48f },
			{ OUT_LAND, (float) DROP_BACK, 0.00f },
			{ OUT_TICKS, (float) DROP_BACK, 0.00f },
	};
	private static final float[][] IN = {
			{ 0, (float) DROP_BACK, 0.00f },
			{ IN_JUMP, (float) DROP_BACK, 0.00f },
			{ 9, 1.38f, 1.62f },
			{ IN_ON_PLATE, 1.10f, 1.32f },
			{ 16, 1.00f, 1.30f },
			{ 24, 0.45f, 1.32f },
			{ 30, 0.08f, 1.05f },
			{ IN_TICKS, 0.00f, INSIDE_Y },
	};

	private HulkbusterClimb() {
	}

	/** The pilot's spot on the climb out at tick {@code t} (fractional): (behind, up). */
	public static Vec2 out(float t) {
		// the drop from the plate falls like a body (ease-in on the height), everything else is eased key to key
		if (t > OUT_HOP && t < OUT_LAND) {
			float u = (t - OUT_HOP) / (OUT_LAND - OUT_HOP);
			float behind = Mth.lerp(u, OUT[6][1], OUT[7][1]);
			// a little lift off the plate first, then gravity
			float up = OUT[6][2] + 0.18f * u * 4f * (1f - u) - OUT[6][2] * u * u;
			return new Vec2(behind, Math.max(0f, up));
		}
		return sample(OUT, t);
	}

	/** The pilot's spot on the climb in at tick {@code t} (fractional): (behind, up). */
	public static Vec2 in(float t) {
		return sample(IN, t);
	}

	private static Vec2 sample(float[][] keys, float t) {
		if (t <= keys[0][0]) {
			return new Vec2(keys[0][1], keys[0][2]);
		}
		for (int i = 1; i < keys.length; i++) {
			if (t <= keys[i][0]) {
				float[] a = keys[i - 1];
				float[] b = keys[i];
				float u = smooth((t - a[0]) / Math.max(1e-4f, b[0] - a[0]));
				return new Vec2(Mth.lerp(u, a[1], b[1]), Mth.lerp(u, a[2], b[2]));
			}
		}
		float[] z = keys[keys.length - 1];
		return new Vec2(z[1], z[2]);
	}

	private static float smooth(float u) {
		u = Mth.clamp(u, 0f, 1f);
		return u * u * (3f - 2f * u);
	}
}
