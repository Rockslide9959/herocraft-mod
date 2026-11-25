package com.projecthero.mod.power;

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
	GOD_OF_THUNDER,
	/** v0.13.4: "Hammer Volley" -- V (without Shift; Shift+V is {@link #THUNDERCLAP}). */
	HAMMER_VOLLEY,
	/** v0.16.1: "Twin Thunder Strike" -- the Mjolnir + Stormbreaker dual-wield combo's finisher. */
	TWIN_THUNDER,
	/** v0.16.1: "Thunder Field" -- Shift + G. */
	THUNDER_FIELD,

	// ---- v0.17.7 Cosmic revamp ({@link ThorKit}): one cooldown per move; the state that owns it is the prefix ----
	U_THUNDER_FIST, U_LIGHTNING_PALM, U_GRAPPLE, U_STORM_RUSH, U_STATIC_BURST, U_GROUND_POUND, U_STORM_SPRINT, U_THUNDER_LEAP,
	M_WHIRLWIND, M_STORM_DASH, M_METEOR_DIVE,
	S_BIFROST_HURL, S_STORM_CLEAVE, S_BIFROST_STEP, S_RENDING_ARC, S_EXECUTIONER, S_BIFROST_DASH,
	T_TWIN_THROW, T_THUNDERSTRUCK, T_STORM_ANCHOR, T_CROSSFIRE,
	/** The one ultimate cooldown every state shares (Shift+Z). */
	ULTIMATE,
	/** Shift+V God of Thunder's cooldown. */
	GOD_OF_THUNDER_BUFF,
	/** Not cooldowns: "ready at" = when the timed state ends (God of Thunder, Storm Sprint, Lightning Incarnate). */
	GOD_ACTIVE, SPRINT_ACTIVE, INCARNATE_ACTIVE,
	/** Internal throttle: God of Thunder's chain bolt fires at most once per {@link ThorKit#GOD_CHAIN_GAP} ticks. */
	GOD_CHAIN
}
