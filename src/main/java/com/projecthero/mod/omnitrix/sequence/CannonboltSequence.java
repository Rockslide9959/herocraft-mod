package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... make the posing and everything look
 * exactly like how it looks in the show"): Cannonbolt's show transformation, cut from the compilation video at 252.15 s
 * ({@code cannonbolt_sequence.ogg}, 13.65 s). The body changes at tick 112, under the orange dissolve after his eye turns yellow; the
 * green has faded back into the world at 273. Client half: {@code CannonboltShow}.
 */
public final class CannonboltSequence extends ShowSequence {
	public CannonboltSequence() {
		super(Aliens.CANNONBOLT.id(), 273, 112);
	}
}
