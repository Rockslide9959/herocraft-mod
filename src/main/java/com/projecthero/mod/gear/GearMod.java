package com.projecthero.mod.gear;

/**
 * v0.17.5 gear tiers I-IV: gadgets (Grapple Line, Smoke Bomb, Flashbang), Hero Alloy, Vibranium and Celestial armour
 * and weapons, the Utility Belt and the Celestial set bonus. Gear is for anyone, power or not.
 * See {@code docs/GEAR_TIERS_REFERENCE.md}.
 */
public final class GearMod {
	private GearMod() {
	}

	public static void initialize() {
		GearMaterials.initialize();
		GearItems.initialize();
		CelestialSet.initialize();
	}

	/** Static per-session state (the Utility Belt spare charges). Called from {@code ServerStateReset.clearAll}. */
	public static void clearSessionState() {
		GadgetCooldowns.clearSessionState();
	}
}
