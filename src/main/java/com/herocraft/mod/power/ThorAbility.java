package com.herocraft.mod.power;

/**
 * Identifies each independently-cooldown-gated Thor ability. The automatic extra jump Lightning
 * Strike gets when it hits a monster (see {@code ThorPowers#chainLightning}) still rides on the base
 * {@link #LIGHTNING_STRIKE} cooldown, per THOR_DESIGN.md -- {@link #CHAIN_LIGHTNING} here is the
 * separate, standalone ability on its own key and its own cooldown.
 */
public enum ThorAbility {
	LIGHTNING_STRIKE,
	THROW_RETURN,
	FLIGHT,
	THUNDERCLAP,
	/** Retired from the slot kit in v0.6.22 (replaced by {@link #GOD_OF_THUNDER}); kept for save/packet compatibility. */
	STORM_CALL,
	CHAIN_LIGHTNING,
	CALL_HAMMER,
	/** v0.6.22 ultimate: "God of Thunder's Wrath". */
	GOD_OF_THUNDER
}
