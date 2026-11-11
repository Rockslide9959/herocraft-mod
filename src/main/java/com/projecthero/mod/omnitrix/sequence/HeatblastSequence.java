package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... make the posing and everything look
 * exactly like how it looks in the show"): Heatblast's show transformation, cut from the compilation video at 10.30-20.40 s
 * ({@code heatblast_sequence.ogg}, 202 ticks). The body changes at tick 95, under the black after the eye close-up; the cut back to the
 * world is at 190. Client half: {@code HeatblastShow}.
 */
public final class HeatblastSequence extends ShowSequence {
	public HeatblastSequence() {
		super(Aliens.HEATBLAST.id(), 202, 95);
	}
}
