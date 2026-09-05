package com.herocraft.mod.ironman.suit;

/**
 * How a stored suit travels to the player when recalled (spec section 21). Consumed by
 * {@link IronManSuitSummonManager}. Kept separate from {@link SuitUpType} because "how the armour
 * gets to you" and "how it assembles once it arrives" are independent axes (e.g. Mark 42 flies each
 * piece separately AND assembles modularly; Mark 50 does not fly at all -- it is already on you as a
 * nanite housing).
 */
public enum SummonType {
	/** Whole suit launches from storage and flies to the player as a set of part entities. */
	FLYING_SET,
	/** Each armour piece is its own independent flying entity; enables partial summons (Mark 42). */
	FLYING_MODULAR,
	/** A pod/case launches, tracks the player, and unfolds on interception (Mark VII). */
	TRACKING_POD,
	/** No travel -- the suit is nanite matter already carried on the player's chest housing (Mark 50). */
	NANOTECH_ONBOARD,
	/** No travel -- the suit is carried as the Mark V Suitcase item and deploys from hand. */
	SUITCASE_ITEM;
}
