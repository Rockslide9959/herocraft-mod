package com.projecthero.mod.omnitrix.alien;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;

/**
 * v0.17.1: one move of an alien kit, as the HUD, the guide and the cooldown helpers see it.
 *
 * @param id         the move id (lang {@code projecthero.omnitrix.<alien>.<id>} and {@code .desc}; cooldown key {@code <alien>.<id>})
 * @param slot       its key: R ({@code SLOT_1}), G ({@code SLOT_2}), X ({@code SLOT_3}, movement), Z ({@code SLOT_4}), V
 *                   ({@code SLOT_5}, utility / mode -- v0.17.1 user) or C ({@code SLOT_6}); N belongs to the Omnitrix (turn back),
 *                   H is never used. Null for a vanilla-key move.
 * @param shift      the Shift version of the key
 * @param hold       a hold move (the kit declares the key holdable, {@link AlienKit#holdable})
 * @param cooldown   ticks
 * @param vanillaKey for a move on a vanilla key (no HUD box): the guide's key label, e.g. "Use" or "Shift+Use"; else null
 */
public record AlienMove(String id, @Nullable AbilitySlot slot, boolean shift, boolean hold, int cooldown, @Nullable String vanillaKey) {
	public static AlienMove tap(String id, AbilitySlot slot, int cooldown) {
		return new AlienMove(id, slot, false, false, cooldown, null);
	}

	public static AlienMove shift(String id, AbilitySlot slot, int cooldown) {
		return new AlienMove(id, slot, true, false, cooldown, null);
	}

	public static AlienMove hold(String id, AbilitySlot slot, int cooldown) {
		return new AlienMove(id, slot, false, true, cooldown, null);
	}

	/** A move on the vanilla attack / use keys ({@code label}: "Attack", "Use", "Shift+Use"...). */
	public static AlienMove vanilla(String id, String label, int cooldown) {
		return new AlienMove(id, null, false, false, cooldown, label);
	}

	public boolean onSlot() {
		return slot != null;
	}

	/** The key label the guide shows ("R", "Shift+G", "Hold C", "Use"). */
	public String keyLabel() {
		if (slot == null) {
			return vanillaKey == null ? "?" : vanillaKey;
		}
		String k = String.valueOf(slot.defaultKey());
		return shift ? "Shift+" + k : hold ? "Hold " + k : k;
	}
}
