package com.projecthero.mod.symbiote;

/**
 * The maths behind the Symbiote suit-up / suit-down animation clock -- shared, common code so the
 * server (settling the animation), the client bone reveal ({@code SymbioteReveal}) and the client
 * particle sweep ({@code SymbioteFxClient}) all agree on exactly the same timeline. Mirrors
 * {@code MaxSteelTransform}'s clock maths.
 */
public final class SymbioteTransform {
	private SymbioteTransform() {
	}

	public static boolean isAnimating(SymbioteState s) {
		return s.transformDir != SymbioteState.DIR_IDLE;
	}

	/**
	 * How "suited" the player is right now, in {@code [0,1]}: 0 = bare, 1 = fully suited. During
	 * {@code DIR_UP} this rises from 0; during {@code DIR_DOWN} it falls from 1 (the raw elapsed
	 * fraction is inverted), so a viewer sees exactly the reverse of the put-on sequence when it comes
	 * off. Idle simply reflects whether the suit is on at all.
	 */
	public static float effectiveProgress(SymbioteState s, long now) {
		if (s.transformDir == SymbioteState.DIR_IDLE) {
			return s.active ? 1f : 0f;
		}
		long elapsed = now - s.transformStartTick;
		float raw = Math.max(0f, Math.min(1f, (float) elapsed / Math.max(1, s.transformDurationTicks)));
		return s.transformDir == SymbioteState.DIR_DOWN ? 1f - raw : raw;
	}
}
