package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... make the posing and everything look
 * exactly like how it looks in the show"): Wildmutt's show transformation, cut from the compilation video at 31.10-48.60 s
 * ({@code wildmutt_sequence.ogg}, 350 ticks). Ben swells and grows orange fur still in his clothes; the body becomes the GeckoLib
 * Wildmutt at tick 180, under the orange streak whip; the green out lands in the alley at 338. Client half: {@code WildmuttShow}.
 */
public final class WildmuttSequence extends ShowSequence {
	public WildmuttSequence() {
		super(Aliens.WILDMUTT.id(), 350, 180);
	}
}
