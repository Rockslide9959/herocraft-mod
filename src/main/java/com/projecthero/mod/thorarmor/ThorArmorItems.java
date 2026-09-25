package com.projecthero.mod.thorarmor;

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

/** Items and visual registration for Thor's Armour (v0.12.32). */
public final class ThorArmorItems {
	public static ThorArmorItem CHESTPLATE;
	public static ThorArmorItem LEGGINGS;
	public static ThorArmorItem BOOTS;

	private ThorArmorItems() {
	}

	public static void initialize() {
		CHESTPLATE = register("thor_armor_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = register("thor_armor_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = register("thor_armor_boots", ArmorItem.Type.BOOTS);

		SuperheroArmorVisuals.register("thor", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/thor.geo.json"),
				ProjectHeroMod.id("textures/armor/thor.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(CHESTPLATE);
		output.accept(LEGGINGS);
		output.accept(BOOTS);
	}

	private static ThorArmorItem register(String path, ArmorItem.Type type) {
		ThorArmorItem item = new ThorArmorItem(ModArmorMaterials.THOR, type,
				new Item.Properties().rarity(Rarity.EPIC).durability(type.getDurability(37)));
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
