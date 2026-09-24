package com.projecthero.mod.symbiote.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.item.ModArmorMaterials;

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
	/** v0.11.15: empty / filled Symbiote Vial -- see {@link SymbioteVialItem}. */
	public static Item SYMBIOTE_VIAL;
	public static Item SYMBIOTE_VIAL_FILLED;

	private SymbioteHostItems() {
	}

	public static void initialize() {
		HELMET = register("symbiote_host_helmet", ArmorItem.Type.HELMET);
		CHESTPLATE = register("symbiote_host_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = register("symbiote_host_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = register("symbiote_host_boots", ArmorItem.Type.BOOTS);
		SYMBIOTE_VIAL = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("symbiote_vial"),
				new SymbioteVialItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), false));
		SYMBIOTE_VIAL_FILLED = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("symbiote_vial_filled"),
				new SymbioteVialItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), true));

		// Bespoke geometry converted from a user-supplied Blockbench rig by
		// scratchpad/convert_symbiote_geo.js -- same shared-animation convention as every other set.
		SuperheroArmorVisuals.register("symbiote_host", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/symbiote_host.geo.json"),
				ProjectHeroMod.id("textures/armor/symbiote_host.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
	}

	private static SymbioteHostArmorItem register(String path, ArmorItem.Type type) {
		SymbioteHostArmorItem item = new SymbioteHostArmorItem(ModArmorMaterials.SYMBIOTE_HOST, type,
				new Item.Properties().rarity(Rarity.EPIC).fireResistant());
		return (SymbioteHostArmorItem) Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
