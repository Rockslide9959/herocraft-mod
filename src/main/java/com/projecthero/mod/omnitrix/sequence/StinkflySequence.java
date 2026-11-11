package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show"): Stinkfly's show transformation, cut from the compilation at 160.70-176.80 s
 * ({@code stinkfly_sequence.ogg}, 322 ticks). The hand changes on the human body first; the rest of him changes at tick 146, under the
 * white flash after the face close-up. Client half: {@code StinkflyShow}.
 */
public final class StinkflySequence extends ShowSequence {
	public StinkflySequence() {
		super(Aliens.STINKFLY.id(), 322, 146);
	}
}
