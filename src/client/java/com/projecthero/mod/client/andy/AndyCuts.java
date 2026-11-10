package com.projecthero.mod.client.andy;

import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.AndyFlight;
import com.projecthero.mod.andy.data.AndyState;

/**
 * v0.17.3: a part of Andy that is there but missing its end, worked out from the synced state -- the hand / foot segment gone
 * or growing back (NBC's segments: in flight, after a shot, a strip from the Regen Menu), a stage half burnt by the boost, the
 * bite the finger shots took out of an arm (and its regrowth). {@link #of} gives how many rows (model px from the joint, of
 * 12) are left and how far the regrowth from there is ({@code q}, 0 while it is still a fresh cut).
 */
public final class AndyCuts {
	/** {@code rows} left from the joint, {@code q} through the regrowth from there, {@code embers}: burning right now. */
	public record Cut(float rows, float q, boolean embers) {
	}

	/** The rows of an arm / leg above its hand / foot (the wrist / ankle). */
	public static final float WRIST = 12f - AndyConfig.HAND_ROWS;

	private AndyCuts() {
	}

	/** The cut on {@code part}, or null if the part is whole (or broken: then it is a regrowth of its own). */
	public static Cut of(AndyState s, int part, float now) {
		if (s == null || !s.hasPower || s.broken(part) || part == AndyState.HEAD) {
			return null;
		}
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		float burnt = AndyFlight.burnt(s, part);
		if (part == AndyState.BODY) {
			return burnt <= 0f ? null : new Cut(12f * (1f - burnt), 0f, flying && s.flag(AndyState.FLAG_BOOST));
		}
		int end = AndyState.extremity(part);
		boolean endGone = end >= 0 && s.broken(end);
		if (flying && (endGone || burnt > 0f)) {
			return new Cut(WRIST * (1f - burnt), 0f, burnt > 0f && s.flag(AndyState.FLAG_BOOST));
		}
		Cut bite = null;
		if (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) {
			float c0 = s.eaten0(part);
			if (c0 > 0f) {
				float q = s.eatenRegrow(part, now);
				bite = q >= 1f ? null : new Cut(12f * (1f - c0), q, false);
			}
		}
		if (endGone && (bite == null || bite.rows() >= WRIST)) {
			// the hand / foot segment: gone, or growing back from the wrist / ankle (one frame a tick)
			return new Cut(WRIST, s.regrowth(end, now), false);
		}
		return bite;
	}
}
