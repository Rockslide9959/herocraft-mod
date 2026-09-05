package com.herocraft.mod.hero;

/**
 * Which acquisition tier a {@link Power} belongs to.
 *
 * <p>Right now every registered {@link Power} is {@link #EXPERIMENTAL} -- the 27 mutation superpowers.
 * The Hero-Tier powers (Thor, Iron Man, Spider-Man, Max Steel, the Punisher) are implemented in their
 * own packages and are not {@link Power}s, so they are not enumerated here; the constant exists so the
 * distinction is explicit in code (see {@code HeroTiers}) and so a future non-experimental {@link Power}
 * would be classified rather than silently treated as stackable.
 *
 * <p><b>Experimental Tier is the stacking tier.</b> A player may own several Experimental Tier powers
 * at once (up to {@code HeroConfig.mutationCapacity}); all of their passive buffs and toggled modes
 * run simultaneously and permanently, and only the currently <em>selected</em> power drives the six
 * hotbar ability slots.
 */
public enum PowerTier {
	EXPERIMENTAL
}
