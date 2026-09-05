package com.herocraft.mod.symbiote.item;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.item.ModArmorMaterials;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The four synthesised pieces of a Normal Symbiote Host's black armour. Never crafted, never in a
 * creative tab, no durability (unbreakable while bonded) -- registered the same way
 * {@code SpiderItems.registerSymbiote} registers the Black Suit's pieces, just pointed at
 * {@link SymbioteHostArmorItem}/{@link ModArmorMaterials#SYMBIOTE_HOST} instead.
 */
public final class SymbioteHostItems {
	public static SymbioteHostArmorItem HELMET;
	public static SymbioteHostArmorItem CHESTPLATE;
	public static SymbioteHostArmorItem LEGGINGS;
	public static SymbioteHostArmorItem BOOTS;

	private SymbioteHostItems() {
	}

	public static void initialize() {
		HELMET = register("symbiote_host_helmet", ArmorItem.Type.HELMET);
		CHESTPLATE = register("symbiote_host_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = register("symbiote_host_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = register("symbiote_host_boots", ArmorItem.Type.BOOTS);
	}

	private static SymbioteHostArmorItem register(String path, ArmorItem.Type type) {
		SymbioteHostArmorItem item = new SymbioteHostArmorItem(ModArmorMaterials.SYMBIOTE_HOST, type,
				new Item.Properties().rarity(Rarity.EPIC).fireResistant());
		return (SymbioteHostArmorItem) Registry.register(BuiltInRegistries.ITEM, HeroCraftMod.id(path), item);
	}
}
