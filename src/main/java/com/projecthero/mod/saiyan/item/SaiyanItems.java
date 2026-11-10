package com.projecthero.mod.saiyan.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** v0.17.3: the Saiyan's items -- Saiyan Blood (crafted: see {@code data/projecthero/recipe/saiyan_blood.json}). */
public final class SaiyanItems {
	public static Item SAIYAN_BLOOD;

	private SaiyanItems() {
	}

	public static void initialize() {
		SAIYAN_BLOOD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("saiyan_blood"),
				new SaiyanBloodItem(new Item.Properties().rarity(Rarity.EPIC)));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(SAIYAN_BLOOD);
	}
}
