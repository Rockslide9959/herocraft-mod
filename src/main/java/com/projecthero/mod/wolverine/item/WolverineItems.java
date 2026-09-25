package com.projecthero.mod.wolverine.item;

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

/** Items belonging to the Wolverine Hero-Tier power. */
public final class WolverineItems {
	public static Item ADAMANTIUM_SERUM;

	/** The craftable Wolverine Suit (v0.12.14) -- a costume, grants nothing. */
	public static WolverineArmorItem SUIT_HELMET;
	public static WolverineArmorItem SUIT_CHESTPLATE;
	public static WolverineArmorItem SUIT_LEGGINGS;
	public static WolverineArmorItem SUIT_BOOTS;

	private WolverineItems() {
	}

	public static void initialize() {
		ADAMANTIUM_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("adamantium_serum"),
				new AdamantiumSerumItem(new Item.Properties().rarity(Rarity.EPIC)));

		SUIT_HELMET = registerSuit("wolverine_suit_helmet", ArmorItem.Type.HELMET);
		SUIT_CHESTPLATE = registerSuit("wolverine_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_LEGGINGS = registerSuit("wolverine_suit_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_BOOTS = registerSuit("wolverine_suit_boots", ArmorItem.Type.BOOTS);

		SuperheroArmorVisuals.register("wolverine", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/wolverine.geo.json"),
				ProjectHeroMod.id("textures/armor/wolverine.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ADAMANTIUM_SERUM);
		output.accept(SUIT_HELMET);
		output.accept(SUIT_CHESTPLATE);
		output.accept(SUIT_LEGGINGS);
		output.accept(SUIT_BOOTS);
	}

	private static WolverineArmorItem registerSuit(String path, ArmorItem.Type type) {
		WolverineArmorItem item = new WolverineArmorItem(ModArmorMaterials.WOLVERINE, type,
				new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(33)));
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
