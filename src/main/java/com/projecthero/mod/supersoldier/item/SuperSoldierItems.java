package com.projecthero.mod.supersoldier.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * v0.14.8 Super Soldier items: the unrefined serum (crafted from a Potion of Strength + Swiftness + Leaping -- see
 * {@code SuperSoldierSerumRecipe}), the refined serum (the unrefined one after 10 minutes in a Blast Furnace), and the
 * Soldier's Shield, which only exists as the model the thrown shield is drawn with (never in an inventory).
 */
public final class SuperSoldierItems {
	public static Item UNREFINED_SERUM;
	public static Item REFINED_SERUM;
	/** Display-only: the model of the thrown shield (G). Not in the creative tab, never dropped. */
	public static Item SOLDIER_SHIELD;

	private SuperSoldierItems() {
	}

	public static void initialize() {
		UNREFINED_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("unrefined_super_soldier_serum"),
				new SuperSoldierSerumItem(new Item.Properties().rarity(Rarity.UNCOMMON), false));
		REFINED_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("refined_super_soldier_serum"),
				new SuperSoldierSerumItem(new Item.Properties().rarity(Rarity.EPIC), true));
		SOLDIER_SHIELD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("soldier_shield"),
				new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
	}

	/** Appended to the {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(UNREFINED_SERUM);
		output.accept(REFINED_SERUM);
	}
}
