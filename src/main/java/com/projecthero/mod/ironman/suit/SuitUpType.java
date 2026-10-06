package com.projecthero.mod.ironman.suit;

/**
 * How a suit assembles onto the player (spec sections 22-26): the stage order and the length of the staged timeline in
 * {@link IronManSuitUpManager}. v0.14.21: the never-used {@code SUITCASE} / {@code MODULAR} (Mark 42) / {@code NANOTECH}
 * (Mark 50) types are gone with the marks they were stubbed for.
 */
public enum SuitUpType {
	/** Marks 1-6 (except V): mechanical armour -- pieces lock on boots-up. */
	MECHANICAL_REMOTE(50),
	/**
	 * Mark V ("changes 15"): the movie suitcase build -- ~4 s, assembling <b>chest first</b>, then legs, then feet, then
	 * the helmet last, unfolding out of the Mark V Suitcase in the right hand.
	 */
	SUITCASE_MOVIE(80),
	/**
	 * Mark VII: an inventory suit-up is staged like {@link #MECHANICAL_REMOTE}; a call off a platform comes in the
	 * delivery pod ({@link SummonType#TRACKING_POD}, {@code IronManDeliveryPodEntity}), which lands behind the player and
	 * fires the pieces onto them (mid-air ok).
	 */
	REMOTE_AUTOMATED(45),
	/**
	 * v0.15.4: the Mark 7 with the <b>Colantotte Bracelets</b> on (Stark Gear slot) -- the quick ~4 s wrap-on
	 * ({@link IronManBraceletSuitUp}): each body piece arrives split open down the front, closes around the player
	 * (chestplate, leggings, boots), the helmet swings up out of the back without its faceplate, and the faceplate closes
	 * last. Never a suit's own type: {@link IronManSuitUpManager} picks it for a Mark 7 while the bracelets are worn.
	 */
	BRACELET_QUICK(IronManBraceletSuitUp.UP_TICKS);

	private final int durationTicks;

	SuitUpType(int durationTicks) {
		this.durationTicks = durationTicks;
	}

	/** Length of the stage timeline (each piece then locks on / breaks away over its own short window). */
	public int durationTicks() {
		return durationTicks;
	}
}
