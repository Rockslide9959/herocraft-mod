package com.herocraft.mod.hero.item;

import java.util.LinkedHashMap;
import java.util.Map;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.mutation.ModSerums;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * HeroPack (experimental-power) items. Registered separately from Thor's {@code ModItems}.
 *
 * <ul>
 *   <li>27 "reagent" items -- crafted from a power's obscure additives, then brewed with the base
 *       potion to make that power's serum (a custom {@link ModSerums} potion).</li>
 *   <li>{@code research_note} -- found in structures; studying it unlocks research.</li>
 * </ul>
 */
public final class HeroPackItems {
	private static final Map<String, Item> REAGENTS = new LinkedHashMap<>();
	public static Item RESEARCH_NOTE;
	public static Item GUIDE;

	private HeroPackItems() {
	}

	public static void initialize() {
		HeroPackComponents.initialize();
		for (Power power : Powers.all()) {
			Item item = register(ModSerums.shortName(power) + "_reagent", new Item(new Item.Properties()));
			REAGENTS.put(power.key(), item);
		}
		RESEARCH_NOTE = register("research_note", new ResearchNoteItem(new Item.Properties().stacksTo(16)));
		GUIDE = register("heropack_guide", new com.herocraft.mod.hero.guide.HeroPackGuideItem(new Item.Properties()));
	}

	public static Item reagent(String powerKey) {
		return REAGENTS.get(powerKey);
	}

	public static Item reagent(Power power) {
		return REAGENTS.get(power.key());
	}

	/** Appended to the existing {@code herocraft:superheroes} creative tab (see {@code ModCreativeTab}). */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(GUIDE);
		output.accept(RESEARCH_NOTE);
		for (Power power : Powers.all()) {
			output.accept(new net.minecraft.world.item.ItemStack(REAGENTS.get(power.key())));
			output.accept(PotionContents.createItemStack(Items.POTION, ModSerums.serum(power)));
		}
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, HeroCraftMod.id(path), item);
	}
}
