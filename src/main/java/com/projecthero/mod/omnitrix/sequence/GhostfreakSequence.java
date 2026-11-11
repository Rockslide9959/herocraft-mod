package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video"): Ghostfreak's show transformation, cut
 * from the compilation video at 231.00-243.10 s ({@code ghostfreak_sequence.ogg}, 12.1 s). The body changes at tick 52, inside the
 * black of the eye the camera dives into; the green has faded back into the world by 242. Client half: {@code GhostfreakShow}.
 */
public final class GhostfreakSequence extends ShowSequence {
	public GhostfreakSequence() {
		super(Aliens.GHOSTFREAK.id(), 242, 52);
	}
}
