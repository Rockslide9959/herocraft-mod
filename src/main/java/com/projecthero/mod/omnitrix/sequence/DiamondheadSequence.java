package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... make the posing and everything look
 * exactly like how it looks in the show"): Diamondhead's show transformation, cut from the compilation video at 61.95-77.90 s
 * ({@code diamondhead_sequence.ogg}, 319 ticks). The body changes at tick 126, inside the eye close-up (Ben's eye turning into
 * Diamondhead's); the hard cut back to the world is at 287 and the soundtrack runs on to 319. Client half: {@code DiamondheadShow}.
 */
public final class DiamondheadSequence extends ShowSequence {
	public DiamondheadSequence() {
		super(Aliens.DIAMONDHEAD.id(), 319, 126);
	}
}
