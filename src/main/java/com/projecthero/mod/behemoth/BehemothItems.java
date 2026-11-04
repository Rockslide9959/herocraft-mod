package com.projecthero.mod.behemoth;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.behemoth.item.AbyssalLureItem;
import com.projecthero.mod.behemoth.item.TokustarShardItem;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The Abyssal Behemoth's items (spec section 24). The Abyssal Core is its unique boss material -- the rest of its loot table
 * (spec: "netherite-related materials, Ghast-related materials, blaze materials") is ordinary vanilla drops.
 * v0.17.2: the Abyssal Lure (calls it in to fight) and the To'kustar Shard (its guaranteed drop: unlocks Way Big).
 */
public final class BehemothItems {
	/** The main boss reward -- future crafting uses (weapons/armour/upgrades) can consume this later. */
	public static Item ABYSSAL_CORE;
	/** v0.17.2: summons the Behemoth in the Nether. */
	public static Item ABYSSAL_LURE;
	/** v0.17.2: one per kill; a prototype Omnitrix wearer absorbs it to unlock Way Big. */
	public static Item TOKUSTAR_SHARD;

	private BehemothItems() {
	}

	public static void initialize() {
		ABYSSAL_CORE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("abyssal_core"),
				new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
		ABYSSAL_LURE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("abyssal_lure"),
				new AbyssalLureItem(new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(16)));
		TOKUSTAR_SHARD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("tokustar_shard"),
				new TokustarShardItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16)));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ABYSSAL_CORE);
		output.accept(ABYSSAL_LURE);
		output.accept(TOKUSTAR_SHARD);
	}
}
