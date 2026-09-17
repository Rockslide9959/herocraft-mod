package com.projecthero.mod.greenlantern.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;
import com.projecthero.mod.item.ModArmorMaterials;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Items belonging to the Green Lantern power: the cosmetic bonded Power Ring, the Lantern Core
 * (Battery crafting ingredient + unique Will Trial reward), the four suit pieces and the Power
 * Battery's {@code BlockItem}.
 */
public final class GreenLanternItems {
	/** Cosmetic -- appears in the player's inventory once bonded. Not consumed or tradeable for anything. */
	public static Item POWER_RING;
	/** Battery crafting ingredient; also the Fallen Lantern Site's unique reward. */
	public static Item LANTERN_CORE;

	public static GreenLanternArmorItem SUIT_HELMET;
	public static GreenLanternArmorItem SUIT_CHESTPLATE;
	public static GreenLanternArmorItem SUIT_LEGGINGS;
	public static GreenLanternArmorItem SUIT_BOOTS;

	private GreenLanternItems() {
	}

	public static void initialize() {
		POWER_RING = register("power_ring", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
		LANTERN_CORE = register("lantern_core", new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

		SUIT_HELMET = registerArmor("green_lantern_suit_helmet", ArmorItem.Type.HELMET);
		SUIT_CHESTPLATE = registerArmor("green_lantern_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_LEGGINGS = registerArmor("green_lantern_suit_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_BOOTS = registerArmor("green_lantern_suit_boots", ArmorItem.Type.BOOTS);

		// v0.11.2: bespoke geometry (own bone layout, same GeckoLib armour bone names as
		// crimson_vanguard so it still rides SHARED_ANIMATION) instead of the shared placeholder shell.
		SuperheroArmorVisuals.register("green_lantern", new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/green_lantern.geo.json"),
				ProjectHeroMod.id("textures/armor/green_lantern.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));

		GreenLanternBlocks.initialize();
	}

	public static void addToCreativeTab(CreativeModeTab.Output out) {
		out.accept(POWER_RING);
		out.accept(LANTERN_CORE);
		out.accept(SUIT_HELMET);
		out.accept(SUIT_CHESTPLATE);
		out.accept(SUIT_LEGGINGS);
		out.accept(SUIT_BOOTS);
		out.accept(GreenLanternBlocks.POWER_BATTERY);
	}

	private static GreenLanternArmorItem registerArmor(String path, ArmorItem.Type type) {
		Item.Properties props = new Item.Properties().rarity(Rarity.EPIC).durability(durabilityFor(type));
		return (GreenLanternArmorItem) register(path, new GreenLanternArmorItem(ModArmorMaterials.GREEN_LANTERN, type, props));
	}

	private static int durabilityFor(ArmorItem.Type type) {
		return switch (type) {
			case CHESTPLATE -> 680;
			case LEGGINGS -> 600;
			case BOOTS -> 529;
			default -> 400;
		};
	}

	static Item register(String path, Item item) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
