package com.projecthero.mod.allmight.item;

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

/** Items belonging to the All Might / One For All Hero-Tier power. */
public final class AllMightItems {
	/** Craftable: using it grants the power. */
	public static Item ONE_FOR_ALL_VESTIGE;

	public static AllMightArmorItem HELMET;
	public static AllMightArmorItem CHESTPLATE;
	public static AllMightArmorItem LEGGINGS;
	public static AllMightArmorItem BOOTS;

	private AllMightItems() {
	}

	public static void initialize() {
		ONE_FOR_ALL_VESTIGE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("one_for_all_vestige"),
				new OneForAllVestigeItem(new Item.Properties().rarity(Rarity.EPIC)));

		HELMET = registerPiece("all_might_helmet", ArmorItem.Type.HELMET);
		CHESTPLATE = registerPiece("all_might_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = registerPiece("all_might_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = registerPiece("all_might_boots", ArmorItem.Type.BOOTS);

		// two looks, one costume: the model swaps with the wearer's form (AllMightArmorItem#armorSetId)
		for (String form : new String[] { "base", "full" }) {
			SuperheroArmorVisuals.register("all_might_" + form, new ArmorVisualDefinition(
					ProjectHeroMod.id("geo/all_might_" + form + ".geo.json"),
					ProjectHeroMod.id("textures/armor/all_might.png"),
					SuperheroArmorVisuals.SHARED_ANIMATION));
		}
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ONE_FOR_ALL_VESTIGE);
		output.accept(HELMET);
		output.accept(CHESTPLATE);
		output.accept(LEGGINGS);
		output.accept(BOOTS);
	}

	private static AllMightArmorItem registerPiece(String path, ArmorItem.Type type) {
		AllMightArmorItem item = new AllMightArmorItem(ModArmorMaterials.THOR, type,
				new Item.Properties().rarity(Rarity.EPIC).durability(type.getDurability(37)));
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
