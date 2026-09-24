package com.projecthero.mod.hero;

/**
 * v0.11.14: every power in the mod is either {@link #PRIMARY} or {@link #SECONDARY}.
 *
 * <p><b>Primary</b> powers -- Thor, Iron Man, Spider-Man, Max Steel, the Punisher, Green Lantern and the
 * 27 experimental mutations -- are what a player <em>is</em>. Gaining a new Primary power replaces the
 * old one ({@link HeroTiers#claimPrimary}): win the Green Lantern ring while you are Spider-Man and you
 * become Green Lantern, not both. (Mutations are one Primary family that still stack with each other up
 * to the mutation capacity; gaining one replaces any non-mutation Primary.)
 *
 * <p><b>Secondary</b> powers are add-ons that ride on top of a Primary one. The Symbiote is the only one
 * right now. Because it does not yet work properly with anything but Spider-Man, gaining any Primary
 * power other than Spider-Man also removes the Symbiote.
 */
public enum PowerClass {
	PRIMARY,
	SECONDARY;

	public String translationKey() {
		return "projecthero.power_class." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
