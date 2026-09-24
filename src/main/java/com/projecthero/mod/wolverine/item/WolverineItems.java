package com.projecthero.mod.wolverine.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items belonging to the Wolverine Hero-Tier power. */
public final class WolverineItems {
	public static Item ADAMANTIUM_SERUM;

	private WolverineItems() {
	}

	public static void initialize() {
		ADAMANTIUM_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("adamantium_serum"),
				new AdamantiumSerumItem(new Item.Properties().rarity(Rarity.EPIC)));
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ADAMANTIUM_SERUM);
	}
}
