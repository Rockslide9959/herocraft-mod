package com.projecthero.mod.spider.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.item.ModArmorMaterials;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items belonging to the Spider-Man Hero Class. Registered separately from every other set. */
public final class SpiderItems {
	public static Item ARACHNID_MUTAGEN;

	/** The craftable Spider-Man Suit (v0.6.18) -- a costume, grants nothing. Indexed by armour slot. */
	public static SpiderManArmorItem SUIT_HELMET;
	public static SpiderManArmorItem SUIT_CHESTPLATE;
	public static SpiderManArmorItem SUIT_LEGGINGS;
	public static SpiderManArmorItem SUIT_BOOTS;

	/**
	 * v0.16.2: the Spider-Man Suit v2 -- the classic red-and-blue upgrade, crafted from the matching original piece (which
	 * is kept, {@link SpiderSuitUpgradeRecipe}). Same skin-tight rig, mask and poses; on the chest it adds the Web Wings
	 * glide and swaps Shift+C for the Trip Mine.
	 */
	public static SpiderManV2ArmorItem SUIT_V2_HELMET;
	public static SpiderManV2ArmorItem SUIT_V2_CHESTPLATE;
	public static SpiderManV2ArmorItem SUIT_V2_LEGGINGS;
	public static SpiderManV2ArmorItem SUIT_V2_BOOTS;
	/** v0.16.4 (user): the Classic suit, a second original-style suit to compare with the first ({@link SpiderManClassicArmorItem}). */
	public static SpiderManClassicArmorItem SUIT_CLASSIC_HELMET;
	public static SpiderManClassicArmorItem SUIT_CLASSIC_CHESTPLATE;
	public static SpiderManClassicArmorItem SUIT_CLASSIC_LEGGINGS;
	public static SpiderManClassicArmorItem SUIT_CLASSIC_BOOTS;

	/**
	 * The black Symbiote suit (v0.9.10) -- synthesised onto a bonded Spider-Man when the Symbiote is
	 * activated ({@link com.projecthero.mod.symbiote.SymbioteSuit}). Never crafted, never in a
	 * tab, no durability. Indexed by armour slot.
	 */
	public static SymbioteArmorItem SYMBIOTE_HELMET;
	public static SymbioteArmorItem SYMBIOTE_CHESTPLATE;
	public static SymbioteArmorItem SYMBIOTE_LEGGINGS;
	public static SymbioteArmorItem SYMBIOTE_BOOTS;

	private SpiderItems() {
	}

	public static void initialize() {
		ARACHNID_MUTAGEN = register("arachnid_mutagen",
				new ArachnidMutagenItem(new Item.Properties().rarity(Rarity.RARE)));

		SUIT_HELMET = registerSuit("spider_man_suit_helmet", ArmorItem.Type.HELMET);
		SUIT_CHESTPLATE = registerSuit("spider_man_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_LEGGINGS = registerSuit("spider_man_suit_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_BOOTS = registerSuit("spider_man_suit_boots", ArmorItem.Type.BOOTS);

		SUIT_V2_HELMET = registerV2("spider_man_suit_v2_helmet", ArmorItem.Type.HELMET);
		SUIT_V2_CHESTPLATE = registerV2("spider_man_suit_v2_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_V2_LEGGINGS = registerV2("spider_man_suit_v2_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_V2_BOOTS = registerV2("spider_man_suit_v2_boots", ArmorItem.Type.BOOTS);
		SUIT_CLASSIC_HELMET = registerClassic("spider_man_suit_classic_helmet", ArmorItem.Type.HELMET);
		SUIT_CLASSIC_CHESTPLATE = registerClassic("spider_man_suit_classic_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_CLASSIC_LEGGINGS = registerClassic("spider_man_suit_classic_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_CLASSIC_BOOTS = registerClassic("spider_man_suit_classic_boots", ArmorItem.Type.BOOTS);
		SpiderSuitUpgradeRecipe.initialize();

		SYMBIOTE_HELMET = registerSymbiote("spider_man_symbiote_helmet", ArmorItem.Type.HELMET);
		SYMBIOTE_CHESTPLATE = registerSymbiote("spider_man_symbiote_chestplate", ArmorItem.Type.CHESTPLATE);
		SYMBIOTE_LEGGINGS = registerSymbiote("spider_man_symbiote_leggings", ArmorItem.Type.LEGGINGS);
		SYMBIOTE_BOOTS = registerSymbiote("spider_man_symbiote_boots", ArmorItem.Type.BOOTS);

		// Map the "spider_man" armour-set id to the converted GeckoLib geometry + the untouched skin.
		SuperheroArmorVisuals.register("spider_man", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/spider_man.geo.json"),
				ProjectHeroMod.id("textures/armor/spider_man.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));

		// The Symbiote suit: same rig, converted from the black-suit GeckoLib bundle by
		// scratchpad/convert_spiderman_geo.js, over the black-suit skin.
		SuperheroArmorVisuals.register("spider_man_symbiote", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/spider_man_symbiote.geo.json"),
				ProjectHeroMod.id("textures/armor/spider_man_symbiote.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ARACHNID_MUTAGEN);
		output.accept(SUIT_HELMET);
		output.accept(SUIT_CHESTPLATE);
		output.accept(SUIT_LEGGINGS);
		output.accept(SUIT_BOOTS);
		output.accept(SUIT_V2_HELMET);
		output.accept(SUIT_V2_CHESTPLATE);
		output.accept(SUIT_V2_LEGGINGS);
		output.accept(SUIT_V2_BOOTS);
		output.accept(SUIT_CLASSIC_HELMET);
		output.accept(SUIT_CLASSIC_CHESTPLATE);
		output.accept(SUIT_CLASSIC_LEGGINGS);
		output.accept(SUIT_CLASSIC_BOOTS);
	}

	private static SpiderManArmorItem registerSuit(String path, ArmorItem.Type type) {
		SpiderManArmorItem item = new SpiderManArmorItem(ModArmorMaterials.SPIDER_MAN, type,
				new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(22)));
		return (SpiderManArmorItem) register(path, item);
	}

	private static SpiderManV2ArmorItem registerV2(String path, ArmorItem.Type type) {
		SpiderManV2ArmorItem item = new SpiderManV2ArmorItem(ModArmorMaterials.SPIDER_MAN, type,
				new Item.Properties().rarity(Rarity.EPIC).durability(type.getDurability(28)));
		return (SpiderManV2ArmorItem) register(path, item);
	}

	private static SpiderManClassicArmorItem registerClassic(String path, ArmorItem.Type type) {
		SpiderManClassicArmorItem item = new SpiderManClassicArmorItem(ModArmorMaterials.SPIDER_MAN, type,
				new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(22)));
		return (SpiderManClassicArmorItem) register(path, item);
	}

	/** Symbiote piece: EPIC, fire-resistant, and deliberately NO {@code .durability(...)} -> unbreakable. */
	private static SymbioteArmorItem registerSymbiote(String path, ArmorItem.Type type) {
		SymbioteArmorItem item = new SymbioteArmorItem(ModArmorMaterials.SYMBIOTE, type,
				new Item.Properties().rarity(Rarity.EPIC).fireResistant());
		return (SymbioteArmorItem) register(path, item);
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
