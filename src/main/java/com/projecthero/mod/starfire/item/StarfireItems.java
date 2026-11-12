package com.projecthero.mod.starfire.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * v0.17.3 Starfire: the power's items. The <b>Tamaranean Starstone</b> grants it ({@code StarfireLearning}: End City chests,
 * meteor craters; right-click in daylight). (The craftable Starfire costume was removed at the user's request on 2026-11-12; its
 * old ids {@code projecthero:starfire_suit_*} load as air in saved worlds.)
 */
public final class StarfireItems {
	public static Item STARSTONE;

	private StarfireItems() {
	}

	public static void initialize() {
		if (STARSTONE != null) {
			return;
		}
		STARSTONE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("tamaranean_starstone"),
				new StarstoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(STARSTONE);
	}
}
