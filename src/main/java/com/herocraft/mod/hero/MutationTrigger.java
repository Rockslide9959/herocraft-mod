package com.herocraft.mod.hero;

/**
 * Data describing the "freak accident" exposure event that permanently unlocks a power once the
 * unstable serum is active. Consumed by the mutation framework (batch 2), advancements, the guide and
 * the reference doc.
 *
 * @param kind          the exposure category (also the trigger listener key)
 * @param descKey       translation key for the player-facing description
 * @param labDeviceKey  translation key naming the reproducible laboratory device alternative, or
 *                      {@code null} if the natural event is the only route
 */
public record MutationTrigger(Kind kind, String descKey, String labDeviceKey) {

	/**
	 * Exposure categories. Each maps to a server-side detector in the mutation framework. Several
	 * powers share a kind (e.g. an explosion unlocks both Super Durability and Shockwave); the active
	 * unstable serum decides which power is actually granted.
	 */
	public enum Kind {
		ELECTRICAL_DISCHARGE,
		LIGHTNING,
		HIGH_INTENSITY_LIGHT,
		GRAVITY_DISTORTION,
		GEOLOGICAL_RESONANCE,
		AMETHYST_GEODE,
		FIRE_EXPOSURE,
		POWDER_SNOW,
		PSIONIC_RESONANCE,
		ENDER_PEARL,
		NEAR_DEATH,
		EXPLOSION,
		RESONANT_HORN,
		DIRECT_SUNLIGHT,
		SPIDER_VENOM,
		SLIME_IMPACT,
		MOLECULAR_COMPRESSION,
		TRUE_DARKNESS,
		ENERGY_OVERLOAD,
		PLANT_SURROUNDINGS,
		PRESSURE_CHAMBER,
		SUBMERSION,
		MAGNETIC_FIELD,
		MASS_COMPRESSION
	}

	public static MutationTrigger of(Kind kind, String descKey, String labDeviceKey) {
		return new MutationTrigger(kind, descKey, labDeviceKey);
	}
}
