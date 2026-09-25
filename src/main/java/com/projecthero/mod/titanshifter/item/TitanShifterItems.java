package com.projecthero.mod.titanshifter.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items belonging to the Titan Shifter Hero-Tier power. */
public final class TitanShifterItems {
	public static Item TITAN_SERUM;

	private TitanShifterItems() {
	}

	public static void initialize() {
		TITAN_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("titan_serum"),
				new TitanSerumItem(new Item.Properties().rarity(Rarity.EPIC)));
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(TITAN_SERUM);
	}
}
