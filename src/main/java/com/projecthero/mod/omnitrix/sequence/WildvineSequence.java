package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... make the posing and everything look
 * exactly like how it looks in the show"): Wildvine's show transformation, cut from the compilation video at 277.45 s
 * ({@code wildvine_sequence.ogg}, 16.95 s). The body changes at tick 90, under the second full green flash (281.75-282.25 s); the green
 * has faded back into the world at 339. Client half: {@code WildvineShow}.
 */
public final class WildvineSequence extends ShowSequence {
	public WildvineSequence() {
		super(Aliens.WILDVINE.id(), 339, 90);
	}
}
