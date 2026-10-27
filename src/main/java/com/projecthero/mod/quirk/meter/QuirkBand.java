package com.projecthero.mod.quirk.meter;

/**
 * v0.17.1 Quirk framework: the three bands every Quirk meter is split into, plus the optional "push past 100".
 * What each band does is the kit's: {@link QuirkMeterSpec#damage} gives the shared damage multipliers, and a kit reads
 * {@link QuirkMeters#band} to change how a move plays.
 */
public enum QuirkBand {
	/** Weaker, but safe. */
	LOW,
	/** Full power plus a perk (the HUD shimmers). */
	SWEET,
	/** Strongest, with a real cost (the HUD pulses red, a warning plays on entry). */
	RED,
	/** Pushed past 100 for a few seconds at an HP cost, then a crash ({@link QuirkMeterSpec#pushPast}). */
	BEYOND;

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
