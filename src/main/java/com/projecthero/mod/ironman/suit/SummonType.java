package com.projecthero.mod.ironman.suit;

/**
 * How a stored suit travels to the player when called (spec section 21). Consumed by {@link IronManSuitCall}. Kept
 * separate from {@link SuitUpType} because "how the armour gets to you" and "how it assembles once it arrives" are
 * independent axes. v0.14.21: the never-used {@code FLYING_MODULAR} (Mark 42) and {@code NANOTECH_ONBOARD} (Mark 50)
 * are gone.
 */
public enum SummonType {
	/** Each piece launches from storage and flies to the player as its own courier entity. */
	FLYING_SET,
	/** A delivery pod carries the set, lands behind the player and fires the pieces onto them (Mark VII). */
	TRACKING_POD,
	/** No travel -- the suit is carried as the Mark V Suitcase item and deploys from hand. */
	SUITCASE_ITEM;
}
