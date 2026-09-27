package com.projecthero.mod.behemoth;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The Abyssal Behemoth's unique boss material (spec section 24). Deliberately just one new item --
 * the rest of its loot table (spec: "netherite-related materials, Ghast-related materials, blaze
 * materials") is ordinary vanilla drops, so killing it feels like "I have a unique boss material", not
 * a pile of new, disconnected currencies.
 */
public final class BehemothItems {
	/** The main boss reward -- future crafting uses (weapons/armour/upgrades) can consume this later. */
	public static Item ABYSSAL_CORE;

	private BehemothItems() {
	}

	public static void initialize() {
		ABYSSAL_CORE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("abyssal_core"),
				new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ABYSSAL_CORE);
	}
}
