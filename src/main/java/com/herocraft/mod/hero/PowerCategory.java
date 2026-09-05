package com.herocraft.mod.hero;

/**
 * Broad power families from the design spec (section 4). Purely descriptive -- used for the guide,
 * the reference doc and HUD grouping; it does not gate anything.
 */
public enum PowerCategory {
	PHYSICAL,
	ELEMENTAL,
	MENTAL,
	MOLECULAR,
	MOVEMENT,
	ENERGY,
	KINETIC,
	FORCE,
	NATURE,
	LIGHT;

	public String translationKey() {
		return "herocraft.power_category." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
