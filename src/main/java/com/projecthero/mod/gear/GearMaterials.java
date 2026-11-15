package com.projecthero.mod.gear;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.loot.LootItems;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * v0.17.5 gear tiers: the three armour materials (Hero Alloy, Vibranium, Celestial) and the weapon tiers.
 * Numbers are documented in {@code docs/GEAR_TIERS_REFERENCE.md}.
 */
public final class GearMaterials {
	/** Tier II: between iron (2/6/5/2, 0 toughness, x15) and diamond (3/8/6/3, 2.0, x33). */
	public static final Holder<ArmorMaterial> HERO_ALLOY = register("hero_alloy", 2, 6, 5, 2, 12,
			SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f, () -> Ingredient.of(LootItems.HERO_ALLOY_INGOT));
	/** Tier III: diamond-plus, plus a fall-damage cut on the items themselves ({@link GearArmorItem}). */
	public static final Holder<ArmorMaterial> VIBRANIUM = register("vibranium", 3, 8, 6, 3, 15,
			SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0f, 0.1f, () -> Ingredient.of(com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_INGOT));
	/** Tier IV: netherite-plus; the full set adds +2 hearts and sight immunity ({@link CelestialSet}). */
	public static final Holder<ArmorMaterial> CELESTIAL = register("celestial", 3, 8, 6, 3, 20,
			SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0f, 0.15f, () -> Ingredient.of(LootItems.CELESTIAL_CORE));

	/** Durability multipliers (vanilla: iron 15, diamond 33, netherite 37). */
	public static final int ALLOY_DURABILITY = 25;
	public static final int VIBRANIUM_DURABILITY = 40;
	public static final int CELESTIAL_DURABILITY = 45;

	public static final Tier ALLOY_TIER = new GearTier(900, 7.0f, 2.5f, BlockTags.INCORRECT_FOR_IRON_TOOL, 12,
			() -> Ingredient.of(LootItems.HERO_ALLOY_INGOT));
	public static final Tier VIBRANIUM_TIER = new GearTier(2200, 9.0f, 3.5f, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 15,
			() -> Ingredient.of(com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_INGOT));
	public static final Tier CELESTIAL_TIER = new GearTier(2600, 10.0f, 5.0f, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 18,
			() -> Ingredient.of(LootItems.CELESTIAL_CORE));

	private GearMaterials() {
	}

	static void initialize() {
	}

	private record GearTier(int uses, float speed, float damage, TagKey<Block> incorrect, int enchant, Supplier<Ingredient> repair)
			implements Tier {
		@Override
		public int getUses() {
			return uses;
		}

		@Override
		public float getSpeed() {
			return speed;
		}

		@Override
		public float getAttackDamageBonus() {
			return damage;
		}

		@Override
		public TagKey<Block> getIncorrectBlocksForDrops() {
			return incorrect;
		}

		@Override
		public int getEnchantmentValue() {
			return enchant;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return repair.get();
		}
	}

	private static Holder<ArmorMaterial> register(String name, int helmet, int chest, int legs, int boots, int enchant,
			Holder<SoundEvent> sound, float toughness, float knockback, Supplier<Ingredient> repair) {
		ArmorMaterial material = new ArmorMaterial(
				Map.of(ArmorItem.Type.HELMET, helmet, ArmorItem.Type.CHESTPLATE, chest, ArmorItem.Type.LEGGINGS, legs,
						ArmorItem.Type.BOOTS, boots, ArmorItem.Type.BODY, chest),
				enchant, sound, repair, List.of(new ArmorMaterial.Layer(ProjectHeroMod.id(name))), toughness, knockback);
		return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id(name)), material);
	}
}
