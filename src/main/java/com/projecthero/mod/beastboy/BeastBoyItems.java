package com.projecthero.mod.beastboy;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * v0.17.3 Beast Boy: the <b>Sakutia Serum</b> ({@code projecthero:sakutia_serum}) -- jungle temple loot; drink it (right-click,
 * {@link BeastBoyLearning}) to become Beast Boy. Icon drawn by {@code scratchpad/beastboy/build_sakutia_serum.js}.
 */
public final class BeastBoyItems {
	public static Item SAKUTIA_SERUM;

	private BeastBoyItems() {
	}

	public static void initialize() {
		if (SAKUTIA_SERUM != null) {
			return;
		}
		SAKUTIA_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("sakutia_serum"),
				new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(SAKUTIA_SERUM);
	}
}
