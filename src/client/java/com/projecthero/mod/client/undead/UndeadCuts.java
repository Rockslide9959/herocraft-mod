package com.projecthero.mod.client.undead;

import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.UndeadEjectConfig;
import com.projecthero.mod.undead.data.UndeadState;

/**
 * v0.17.3: a part of Undead that is there but missing its end, worked out from the synced state -- an end blown off by an
 * ejection ({@code UndeadEject}: the hand / foot and half the forearm / calf, {@link UndeadState#X_EJECTED}) and its regrowth
 * from there, the hand / foot segment gone or growing back on its own (after a shot, a strip from the Regen Menu), the torso cut
 * to its top third by the Combustion Punch, the bite the finger shots took out of an arm (and its regrowth). {@link #of} gives
 * how many rows (model px from the joint, of 12) are left and how far the regrowth from there is ({@code q}, 0 while it is
 * still a fresh cut).
 */
public final class UndeadCuts {
	/** {@code rows} left from the joint, {@code q} through the regrowth from there, {@code embers}: burning right now. */
	public record Cut(float rows, float q, boolean embers) {
	}

	/** The rows of an arm / leg above its hand / foot (the wrist / ankle). */
	public static final float WRIST = 12f - UndeadConfig.HAND_ROWS;

	private UndeadCuts() {
	}

	/** The cut on {@code part}, or null if the part is whole (or broken: then it is a regrowth of its own). */
	public static Cut of(UndeadState s, int part, float now) {
		if (s == null || !s.hasPower || s.broken(part) || part == UndeadState.HEAD) {
			return null;
		}
		long ejected = s.ex(UndeadState.X_EJECTED);
		if (part == UndeadState.BODY) {
			return (ejected & (1L << UndeadState.BODY)) != 0L ? new Cut(UndeadEjectConfig.COMBUSTION_BODY_ROWS, 0f, false) : null;
		}
		int end = UndeadState.extremity(part);
		boolean endGone = end >= 0 && s.broken(end);
		if (endGone && (ejected & (1L << end)) != 0L) {
			// blown off with half the forearm / calf: cut there, growing back from there with the hand / foot's own frames
			return new Cut(UndeadEjectConfig.EJECT_ROWS, s.regrowth(end, now), false);
		}
		Cut bite = null;
		if (part == UndeadState.RIGHT_ARM || part == UndeadState.LEFT_ARM) {
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
