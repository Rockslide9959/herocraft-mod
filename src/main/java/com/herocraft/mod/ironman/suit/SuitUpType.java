package com.herocraft.mod.ironman.suit;

/**
 * How a suit assembles onto the player (spec sections 22-26). The staged animation and the FX differ
 * per type; the {@link IronManSuitUpManager} branches on this. All types currently share a
 * staged-FX-plus-progressive-equip implementation -- the visual-entity / nanotech-formation upgrades
 * are documented per constant.
 */
public enum SuitUpType {
	/** Mark III: mechanical remote armour -- pieces fly in from storage and lock on boots-up. */
	MECHANICAL_REMOTE(50),
	/** Mark V: unfolds from the Mark V Suitcase around the player -- fast, no incoming pieces. */
	SUITCASE(30),
	/**
	 * Mark 5 ("changes 15"): the movie Mark V suitcase build -- ~4 s, assembling <b>chest first</b>,
	 * then arms/hands, then legs/feet, then the helmet last, growing outward from the case on the
	 * chest. No incoming pieces (the pieces are materialised from the case).
	 */
	SUITCASE_MOVIE(80),
	/** Mark VII: remote automated pod tracks and intercepts the player, builds around them (mid-air ok). */
	REMOTE_AUTOMATED(45),
	/** Mark 42: modular -- every component flies in independently; supports partial summons. */
	MODULAR(55),
	/** Mark 50: nanotech -- deploys from the chest Arc Reactor housing, spreads over the body. No flying pieces. */
	NANOTECH(25);

	private final int durationTicks;

	SuitUpType(int durationTicks) {
		this.durationTicks = durationTicks;
	}

	/** Total length of the suit-up sequence. Suit-down uses the same length in reverse. */
	public int durationTicks() {
		return durationTicks;
	}
}
