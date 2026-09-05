package com.herocraft.mod.hero;

/**
 * The <em>definition</em> of one ability slot within a power: stable metadata only. The runtime
 * behaviour lives in an {@link AbilityHandler} looked up from {@link AbilityHandlers} by
 * {@code powerId + "/" + id}, so mechanics can be added batch by batch without touching the data
 * catalogue.
 *
 * @param id            short id, unique within its power (e.g. {@code "power_punch"})
 * @param slot          which of the six universal slots this is
 * @param nameKey       translation key for the display name
 * @param descKey       translation key for the one-line description (guide / HUD expanded view)
 * @param activation    how the input is interpreted
 * @param cooldownTicks base cooldown in ticks (0 for none / resource-gated / toggle); the effective
 *                      value is scaled by {@link com.herocraft.mod.hero.HeroConfig#cooldownMultiplier}
 */
public record Ability(
		String id,
		AbilitySlot slot,
		String nameKey,
		String descKey,
		AbilityActivation activation,
		int cooldownTicks) {

	public static Ability of(String id, AbilitySlot slot, String nameKey, AbilityActivation activation, int cooldownTicks) {
		return new Ability(id, slot, nameKey, nameKey + ".desc", activation, cooldownTicks);
	}
}
