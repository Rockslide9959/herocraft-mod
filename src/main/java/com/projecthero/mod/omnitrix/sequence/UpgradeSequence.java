package com.projecthero.mod.omnitrix.sequence;

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video"): Upgrade's show transformation, cut from
 * the compilation video at 211.10-220.10 s ({@code upgrade_sequence.ogg}, 9 s). The body changes at tick 133, under the white flash
 * after the circuits close over the head; the show cuts straight back to the scene at 168. Client half: {@code UpgradeShow}.
 */
public final class UpgradeSequence extends ShowSequence {
	public UpgradeSequence() {
		super(Aliens.UPGRADE.id(), 180, 133);
	}
}
