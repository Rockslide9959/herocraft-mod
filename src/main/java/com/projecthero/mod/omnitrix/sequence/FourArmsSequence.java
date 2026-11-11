package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.2 (user: "this is the fourarms transformation from the ben 10 show, can you try and recreate this in minecraft"), moved onto
 * the v0.17.3 framework: cut 1:1 to the user's episode clip ({@code four_arms_sequence.ogg}, 13.05 s; in the compilation video it
 * starts at 132.585 s). The body changes at tick 95 (video 7.3 s, under the golden flash); the green has faded back into the world
 * at 261 (video 15.6 s). Client half: {@code FourArmsShow}.
 */
public final class FourArmsSequence extends ShowSequence {
	public FourArmsSequence() {
		super(Aliens.FOUR_ARMS.id(), 261, 95);
	}
}
