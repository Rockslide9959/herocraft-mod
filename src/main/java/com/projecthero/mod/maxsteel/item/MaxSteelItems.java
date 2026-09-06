package com.projecthero.mod.maxsteel.item;

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

/** Items belonging to the Max Steel Hero-Tier power. Registered separately from every other set. */
public final class MaxSteelItems {
	public static Item TURBO_STABILIZER;

	/** The four synthesised suit pieces -- never crafted, never in a tab. Indexed by armour slot. */
	public static MaxSteelArmorItem SUIT_HELMET;
	public static MaxSteelArmorItem SUIT_CHESTPLATE;
	public static MaxSteelArmorItem SUIT_LEGGINGS;
	public static MaxSteelArmorItem SUIT_BOOTS;

	private MaxSteelItems() {
	}

	public static void initialize() {
		TURBO_STABILIZER = register("turbo_stabilizer",
				new TurboStabilizerItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));

		SUIT_HELMET = registerSuit("max_steel_suit_helmet", ArmorItem.Type.HELMET);
		SUIT_CHESTPLATE = registerSuit("max_steel_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_LEGGINGS = registerSuit("max_steel_suit_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_BOOTS = registerSuit("max_steel_suit_boots", ArmorItem.Type.BOOTS);

		// Map the "max_steel" armour-set id (and the five specialised-form ids) to their converted
		// GeckoLib geometry + untouched form skin. armorSetId() picks which one per the wearer's mode.
		for (String set : new String[] {
				"max_steel", "max_steel_strength", "max_steel_speed",
				"max_steel_flight", "max_steel_stealth", "max_steel_cannon" }) {
			SuperheroArmorVisuals.register(set, new ArmorVisualDefinition(
					ProjectHeroMod.id("geo/" + set + ".geo.json"),
					ProjectHeroMod.id("textures/armor/" + set + ".png"),
					SuperheroArmorVisuals.SHARED_ANIMATION));
		}
	}

	/** Appended to the existing {@code projecthero:superheroes} creative tab (the Stabilizer only). */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(TURBO_STABILIZER);
	}

	private static MaxSteelArmorItem registerSuit(String path, ArmorItem.Type type) {
		MaxSteelArmorItem item = new MaxSteelArmorItem(ModArmorMaterials.MAX_STEEL, type,
				new Item.Properties().rarity(Rarity.EPIC).durability(type.getDurability(50)).fireResistant());
		return (MaxSteelArmorItem) register(path, item);
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), item);
	}
}
