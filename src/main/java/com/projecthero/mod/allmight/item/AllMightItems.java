package com.projecthero.mod.allmight.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items belonging to the All Might / One For All Hero-Tier power (v0.12.34: the costume armour is gone -- he keeps the player's own skin). */
public final class AllMightItems {
	/** Craftable: using it grants the power. */
	public static Item ONE_FOR_ALL_VESTIGE;

	private AllMightItems() {
	}

	public static void initialize() {
		ONE_FOR_ALL_VESTIGE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("one_for_all_vestige"),
				new OneForAllVestigeItem(new Item.Properties().rarity(Rarity.EPIC)));
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ONE_FOR_ALL_VESTIGE);
	}
}
