package com.projecthero.mod.hammer;

/**
 * The one authoritative "what is this hammer doing" value, shared by the in-world entity and the
 * persistent record so both always agree -- replacing the pile of independent booleans
 * ({@code isThrown}, {@code isReturning}, {@code hitBlock}, ...) that a system like this otherwise
 * grows.
 *
 * <p>{@link #HELD} and {@link #STORED} only ever appear on a {@link HammerRecord} (there is no
 * entity in those states); {@link #THROWN}, {@link #IMPACT}, {@link #RETURNING} and {@link #RESTING}
 * are the entity's own states and are mirrored into the record so an unloaded hammer can still be
 * described accurately.
 */
public enum MjolnirStatus {
	/** In the owner's hand. */
	HELD,
	/** In an inventory/container somewhere -- not in a hand, not in the world. */
	STORED,
	/** Flying away from the thrower. Collides with terrain. */
	THROWN,
	/** Momentarily embedded in whatever it just struck, about to turn around. */
	IMPACT,
	/** Flying home to its owner. Phases through terrain. */
	RETURNING,
	/** Lying in the world, waiting to be picked up or called. */
	RESTING,
	/** Known to exist and known to be bound, but its physical whereabouts are unresolved. */
	LOST
}
