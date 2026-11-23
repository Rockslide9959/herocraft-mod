package com.projecthero.mod.hideout.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.loot.HeroRarity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/** v0.17.5: the five hideout artifacts (one per faction, from its own Hideout Vault). */
public final class HideoutItems {
	public static Item GILDED_CANE;
	public static Item CORTEX_SHARD;
	public static Item MOTHER_BOX_FRAGMENT;
	public static Item HIVE_HEART;
	public static Item FROSTBRAND;
	// v0.17.7: the three new hideouts' artifacts
	public static Item GRAVECALLER_BELL;
	public static Item BROOD_FANG;
	public static Item OATH_SIGIL;

	private HideoutItems() {
	}

	public static void initialize() {
		GILDED_CANE = item("gilded_cane", new GildedCaneItem(HeroRarity.props(HeroRarity.EPIC)));
		CORTEX_SHARD = item("ultron_cortex_shard", new CortexShardItem(HeroRarity.props(HeroRarity.EPIC).stacksTo(1).fireResistant()));
		MOTHER_BOX_FRAGMENT = item("mother_box_fragment", new MotherBoxFragmentItem(HeroRarity.props(HeroRarity.LEGENDARY).stacksTo(1)
				.fireResistant()));
		HIVE_HEART = item("hive_heart", new HiveHeartItem(HeroRarity.props(HeroRarity.LEGENDARY).stacksTo(1)));
		FROSTBRAND = item("frostbrand", new FrostbrandItem(HeroRarity.props(HeroRarity.EPIC)));
		GRAVECALLER_BELL = item("gravecaller_bell", new GravecallerBellItem(HeroRarity.props(HeroRarity.EPIC).stacksTo(1)));
		BROOD_FANG = item("brood_fang", new BroodFangItem(HeroRarity.props(HeroRarity.EPIC)));
		OATH_SIGIL = item("oath_sigil", new OathSigilItem(HeroRarity.props(HeroRarity.LEGENDARY).stacksTo(1).fireResistant()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (Item i : new Item[] { GILDED_CANE, CORTEX_SHARD, MOTHER_BOX_FRAGMENT, HIVE_HEART, FROSTBRAND, GRAVECALLER_BELL, BROOD_FANG, OATH_SIGIL }) {
			output.accept(i);
		}
		output.accept(HideoutBlocks.HIVE_BIOMASS_ITEM);
		output.accept(HideoutBlocks.HIVE_POD_ITEM);
		output.accept(HideoutBlocks.CRIMSON_BIOMASS_ITEM);
		output.accept(HideoutBlocks.CRIMSON_EGG_ITEM);
		output.accept(HideoutBlocks.BALLISTA_ITEM);
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
