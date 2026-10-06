package com.projecthero.mod.hulk.gladiator;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * v0.15.3: the seven pieces of the Hulk's Gladiator Gear. Plain craftable items (stack size 1, not vanilla armour):
 * they only do anything in the Gladiator Gear slots ({@link GladiatorGear}), opened with a tap of N as Banner. The
 * index of each piece is its slot ({@link GladiatorGear#HELMET} ... {@link GladiatorGear#AXE}).
 */
public final class GladiatorItems {
	public static Item HELMET;
	public static Item PAULDRON;
	public static Item HARNESS;
	public static Item BRACERS;
	public static Item KILT;
	public static Item HAMMER;
	public static Item AXE;

	private GladiatorItems() {
	}

	public static void initialize() {
		HELMET = register("gladiator_helmet", GladiatorGear.HELMET, Rarity.UNCOMMON);
		PAULDRON = register("gladiator_pauldron", GladiatorGear.PAULDRON, Rarity.UNCOMMON);
		HARNESS = register("gladiator_harness", GladiatorGear.HARNESS, Rarity.UNCOMMON);
		BRACERS = register("gladiator_bracers", GladiatorGear.BRACERS, Rarity.UNCOMMON);
		KILT = register("gladiator_kilt", GladiatorGear.KILT, Rarity.UNCOMMON);
		HAMMER = register("gladiator_hammer", GladiatorGear.HAMMER, Rarity.EPIC);
		AXE = register("gladiator_axe", GladiatorGear.AXE, Rarity.EPIC);
	}

	private static Item register(String id, int slot, Rarity rarity) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(id),
				new GladiatorGearItem(slot, new Item.Properties().stacksTo(1).rarity(rarity).fireResistant()));
	}

	/** The item that belongs in a slot (null for an out-of-range slot). */
	public static Item forSlot(int slot) {
		return switch (slot) {
			case GladiatorGear.HELMET -> HELMET;
			case GladiatorGear.PAULDRON -> PAULDRON;
			case GladiatorGear.HARNESS -> HARNESS;
			case GladiatorGear.BRACERS -> BRACERS;
			case GladiatorGear.KILT -> KILT;
			case GladiatorGear.HAMMER -> HAMMER;
			case GladiatorGear.AXE -> AXE;
			default -> null;
		};
	}

	/** Appended to the mod's creative tab right after the Hulk's own items. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (int i = 0; i < GladiatorGear.SLOTS; i++) {
			output.accept(forSlot(i));
		}
	}
}
