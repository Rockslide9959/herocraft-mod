package com.projecthero.mod.hero.visual;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;

/**
 * v0.17.1: the HUD's Shift-move cooldown strip, for any mutation. Super Speed drew one hard-coded (a thin bar along the
 * bottom of a key's box that refills as the Shift move cools down); a power registers its own here instead: the Shift
 * move on {@code slot} counts down the synced resource {@code powerKey/resource} (ticks left) and keeps its full length in
 * {@code powerKey/resource_full}. Filled from common init, read by {@code AbilityHud}.
 */
public final class ShiftStrips {
	private static final Map<String, String> STRIPS = new ConcurrentHashMap<>();

	private ShiftStrips() {
	}

	public static void register(String powerKey, AbilitySlot slot, String resource) {
		STRIPS.put(powerKey + "#" + slot.ordinal(), resource);
	}

	/** The countdown resource of the Shift move on {@code slot}, or null. */
	public static String get(String powerKey, AbilitySlot slot) {
		return STRIPS.get(powerKey + "#" + slot.ordinal());
	}
}
