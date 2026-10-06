package com.projecthero.mod.hammer;

import com.projecthero.mod.item.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.1: the two bindable, callable Thor weapons that share {@link MjolnirRegistry}'s ownership tracking and
 * anti-duplication scheme. Every weapon of either kind carries the same identity components (a
 * {@code HAMMER_ID} + {@code HAMMER_GENERATION}); ids are random UUIDs, so one registry holds both kinds
 * without collision and each {@link HammerRecord} remembers which kind it describes (old saves, which only
 * ever held Mjolnirs, default to {@link #MJOLNIR}).
 */
public enum ThorWeapon {
	MJOLNIR("mjolnir"),
	STORMBREAKER("stormbreaker");

	private final String key;

	ThorWeapon(String key) {
		this.key = key;
	}

	/** Lower-case name: the saved-data value and the suffix of this weapon's message keys. */
	public String key() {
		return key;
	}

	public Item item() {
		return this == STORMBREAKER ? ModItems.STORMBREAKER : ModItems.MJOLNIR;
	}

	public ThorWeapon other() {
		return this == MJOLNIR ? STORMBREAKER : MJOLNIR;
	}

	public boolean is(ItemStack stack) {
		return stack.is(item());
	}

	/** The weapon {@code stack} is, or null for anything else. */
	public static ThorWeapon of(ItemStack stack) {
		if (stack.is(ModItems.MJOLNIR)) {
			return MJOLNIR;
		}
		if (stack.is(ModItems.STORMBREAKER)) {
			return STORMBREAKER;
		}
		return null;
	}

	/** Whether {@code stack} is one of the registry-tracked weapons. */
	public static boolean isTracked(ItemStack stack) {
		return stack.is(ModItems.MJOLNIR) || stack.is(ModItems.STORMBREAKER);
	}

	/** Anything unrecognised (an older save with no field, a hand-edited file) is a Mjolnir. */
	public static ThorWeapon byKey(String key) {
		return STORMBREAKER.key.equals(key) ? STORMBREAKER : MJOLNIR;
	}
}
