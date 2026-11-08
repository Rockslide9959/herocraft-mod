package com.projecthero.mod.client.andy;

import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.AndyFlight;
import com.projecthero.mod.andy.data.AndyState;

/**
 * v0.17.3: a part of Andy that is there but missing its end, worked out from the synced state -- the hands and feet while he
 * flies (and growing back after), a stage half burnt by the boost, the bite the finger shots took out of an arm (and its
 * regrowth). {@link #of} gives how many rows (model px from the joint, of 12) are left and how far the regrowth from there
 * is ({@code q}, 0 while it is still a fresh cut).
 */
public final class AndyCuts {
	/** {@code rows} left from the joint, {@code q} through the regrowth from there, {@code embers}: burning right now. */
	public record Cut(float rows, float q, boolean embers) {
	}

	private AndyCuts() {
	}

	/** The cut on {@code part}, or null if the part is whole (or broken: then it is a regrowth of its own). */
	public static Cut of(AndyState s, int part, float now) {
		if (s == null || !s.hasPower || s.broken(part) || part == AndyState.HEAD) {
			return null;
		}
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		long cutStart = s.ex(AndyState.X_CUT_REGROW);
		float burnt = AndyFlight.burnt(s, part);
		if (part == AndyState.BODY) {
			if (burnt <= 0f) {
				return null;
			}
			float rows = 12f * (1f - burnt);
			if (flying) {
				return new Cut(rows, 0f, s.flag(AndyState.FLAG_BOOST));
			}
			if (cutStart > 0L) {
				float q = (now - cutStart) / AndyFlight.cutRegrowTicks(s);
				return q >= 1f ? null : new Cut(rows, Math.max(0f, q), false);
			}
			return new Cut(rows, 0f, false);
		}
		if (flying || cutStart > 0L) {
			float rows = (12f - AndyConfig.HAND_ROWS) * (1f - burnt);
			if (flying) {
				return new Cut(rows, 0f, burnt > 0f && s.flag(AndyState.FLAG_BOOST));
			}
			float q = (now - cutStart) / AndyFlight.cutRegrowTicks(s);
			if (q < 1f) {
				return new Cut(rows, Math.max(0f, q), false);
			}
		}
		if (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) {
			float c0 = s.eaten0(part);
			if (c0 > 0f) {
				float q = s.eatenRegrow(part, now);
				return q >= 1f ? null : new Cut(12f * (1f - c0), q, false);
			}
		}
		return null;
	}
}
