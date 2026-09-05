package com.herocraft.mod.hero;

import java.util.List;

/**
 * Data describing how a power's experimental serum is brewed. Consumed by the mutation/brewing
 * framework (batch 2) and by the guide/reference doc. Item references are plain namespaced id
 * strings so the catalogue has no hard dependency on registration order.
 *
 * @param basePotion  vanilla potion id the serum is brewed from (e.g. {@code "minecraft:strength"})
 * @param additives   ingredient item ids added in the brewing stand, in order
 * @param fuel        optional non-standard experimental brewing fuel item id, or {@code null}
 * @param resultName  translation key for the resulting serum's display name
 */
public record SerumRecipe(
		String basePotion,
		List<String> additives,
		String fuel,
		String resultName) {

	public static SerumRecipe of(String basePotion, String resultName, String... additives) {
		return new SerumRecipe(basePotion, List.of(additives), null, resultName);
	}

	public SerumRecipe withFuel(String fuelItemId) {
		return new SerumRecipe(basePotion, additives, fuelItemId, resultName);
	}
}
