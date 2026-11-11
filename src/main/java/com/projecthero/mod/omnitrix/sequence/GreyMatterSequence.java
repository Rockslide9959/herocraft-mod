package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show"): Grey Matter's show transformation, cut from the compilation at 111.10-123.80 s
 * ({@code grey_matter_sequence.ogg}, 254 ticks). The hands go grey on the human body first; the rest of him changes at tick 86, under
 * the white flash as the camera comes round behind him. Client half: {@code GreyMatterShow}.
 */
public final class GreyMatterSequence extends ShowSequence {
	public GreyMatterSequence() {
		super(Aliens.GREY_MATTER.id(), 254, 86);
	}
}
