package com.projecthero.mod.speedforce;

/**
 * v0.17.1: shared by the two speedster-hunger mixins -- the horizontal distance vanilla is charging movement hunger for
 * right now (set around {@code ServerPlayer.checkMovementStatistics}, read in {@code Player.causeFoodExhaustion}), or -1.
 * Server thread only.
 */
public final class SpeedsterHunger {
	public static double movedNow = -1.0;

	private SpeedsterHunger() {
	}
}
