package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show"): XLR8's show transformation, cut from the compilation at 87.15-100.10 s
 * ({@code xlr8_sequence.ogg}, 259 ticks). The body changes at tick 77, under the smear flash at the end of the eye close-up (the next
 * shot is already XLR8's eye). Client half: {@code Xlr8Show}.
 */
public final class Xlr8Sequence extends ShowSequence {
	public Xlr8Sequence() {
		super(Aliens.XLR8.id(), 259, 77);
	}
}
