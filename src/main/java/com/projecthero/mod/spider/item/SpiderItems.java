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
	}

	private static SpiderManArmorItem registerSuit(String path, ArmorItem.Type type) {
		SpiderManArmorItem item = new SpiderManArmorItem(ModArmorMaterials.SPIDER_MAN, type,
				new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(22)));
		return (SpiderManArmorItem) register(path, item);
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
