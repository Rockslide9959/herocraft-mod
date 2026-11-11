package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video"): Ripjaws' show transformation, cut from
 * the compilation video at 186.30-201.10 s ({@code ripjaws_sequence.ogg}, 14.8 s). The body changes at tick 187, under the white flash
 * after the eye close-up; the green has faded back into the world by 296. Client half: {@code RipjawsShow}.
 */
public final class RipjawsSequence extends ShowSequence {
	public RipjawsSequence() {
		super(Aliens.RIPJAWS.id(), 296, 187);
	}
}
