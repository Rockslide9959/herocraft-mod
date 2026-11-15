package com.projecthero.mod.loot;

/**
 * v0.17.5 loot framework entry point: rarity, the Hero Vault, the gear-tier materials, the hero supplies and the
 * Locator. See {@code docs/LOOT_PROGRESSION_REFERENCE.md} for the API later features build on.
 */
public final class LootMod {
	private LootMod() {
	}

	public static void initialize() {
		HeroRarity.bootstrap();
		LootItems.initialize();
		MedKitHealing.initialize();
		SupplyTargetDefaults.registerAll();
	}

	/** Static per-session state (the Med Kit heal timers). Called from {@code ServerStateReset.clearAll}. */
	public static void clearSessionState() {
		MedKitHealing.clearSessionState();
	}
}
