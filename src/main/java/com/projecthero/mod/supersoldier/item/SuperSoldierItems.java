package com.projecthero.mod.supersoldier.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Super Soldier items: the unrefined serum (crafted from a Potion of Strength + Swiftness + Leaping -- see
 * {@code SuperSoldierSerumRecipe}), the refined serum (the unrefined one after 10 minutes in a Blast Furnace), and since
 * v0.14.9 the craftable Adamantium Shield and the craftable four-piece Captain America suit (only a Super Soldier can
 * wear it). {@link #SOLDIER_SHIELD} is the old display-only shield model, kept as the thrown shield's fallback look.
 */
public final class SuperSoldierItems {
	public static Item UNREFINED_SERUM;
	public static Item REFINED_SERUM;
	/** Display-only: never in an inventory (the thrown shield's fallback model). */
	public static Item SOLDIER_SHIELD;
	/** v0.14.9: the round, unbreakable, throwable shield. */
	public static Item ADAMANTIUM_SHIELD;

	public static SuperSoldierArmorItem HELMET;
	public static SuperSoldierArmorItem CHESTPLATE;
	public static SuperSoldierArmorItem LEGGINGS;
	public static SuperSoldierArmorItem BOOTS;

	/**
	 * The suit: v0.17.7 diamond level -- 3 / 8 / 6 / 3 = 20 armour, 2.0 toughness (was 3 / 7 / 6 / 2, 1.0), repaired with iron.
	 * The flat fallback layer reuses Thor's (never seen: QuirkSuitArmourMixin skips the vanilla armour model, QuirkCostume draws
	 * the skin-tight suit).
	 */
	public static Holder<ArmorMaterial> SUIT_MATERIAL;

	private SuperSoldierItems() {
	}

	public static void initialize() {
		UNREFINED_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("unrefined_super_soldier_serum"),
				new SuperSoldierSerumItem(new Item.Properties().rarity(Rarity.UNCOMMON), false));
		REFINED_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("refined_super_soldier_serum"),
				new SuperSoldierSerumItem(new Item.Properties().rarity(Rarity.EPIC), true));
		SOLDIER_SHIELD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("soldier_shield"),
				new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
		ADAMANTIUM_SHIELD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("adamantium_shield"),
				new AdamantiumShieldItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

		SUIT_MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id(SuperSoldierArmorItem.SET_ID)),
				new ArmorMaterial(Map.of(
						ArmorItem.Type.BOOTS, 3,
						ArmorItem.Type.LEGGINGS, 6,
						ArmorItem.Type.CHESTPLATE, 8,
						ArmorItem.Type.HELMET, 3,
						ArmorItem.Type.BODY, 11),
						12, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT),
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("thor"))), 2.0f, 0.0f));
		HELMET = piece("captain_america_helmet", ArmorItem.Type.HELMET);
		CHESTPLATE = piece("captain_america_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = piece("captain_america_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = piece("captain_america_boots", ArmorItem.Type.BOOTS);
		// v0.17.5: the Captain America Battle Suit (the user's NameMC skin) with the Super Soldier's shield slot on the top
		CapBattleSuitItem.register();
		// v0.17.7: the suit is a skin-tight costume (QuirkCostume), no GeckoLib model any more
	}

	private static SuperSoldierArmorItem piece(String path, ArmorItem.Type type) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path), new SuperSoldierArmorItem(SUIT_MATERIAL, type,
				new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(com.projecthero.mod.supersoldier.SuperSoldierConfig.SUIT_DURABILITY_MULTIPLIER)))); // v0.17.7: diamond 33 (was 25)
	}

	/** Appended to the {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(UNREFINED_SERUM);
		output.accept(REFINED_SERUM);
		output.accept(ADAMANTIUM_SHIELD);
		output.accept(HELMET);
		output.accept(CHESTPLATE);
		output.accept(LEGGINGS);
		output.accept(BOOTS);
		output.accept(CapBattleSuitItem.COWL);
		output.accept(CapBattleSuitItem.TOP);
		output.accept(CapBattleSuitItem.TROUSERS);
		output.accept(CapBattleSuitItem.BOOTS);
	}
}
