package com.projecthero.mod.blackpanther.item;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.3: the Heart-Shaped Herb (a softly glowing purple plant, jungles only, placed by the
 * {@code projecthero:heart_shaped_herb} feature), the soup it makes, and the sprites of Black Panther's two thrown things.
 * v0.17.7: the suit is four crafted armour pieces ({@link #SUIT_HELMET} and the rest, {@link PantherSuitItem}).
 */
public final class BlackPantherItems {
	public static final ResourceKey<PlacedFeature> HERB_FEATURE = ResourceKey.create(Registries.PLACED_FEATURE, ProjectHeroMod.id("heart_shaped_herb"));

	public static Block HERB_BLOCK;
	public static Item HERB;
	public static Item SOUP;
	public static Item DAGGER;
	public static Item KIMOYO_BEAD;
	/** v0.17.4-0.17.6 the folded suit; v0.17.7 legacy only (no recipe): using it unfolds it into the four suit pieces. */
	public static Item HABIT;
	/** v0.17.7: the wearable suit. */
	public static Holder<ArmorMaterial> SUIT_MATERIAL;
	public static Item SUIT_HELMET;
	public static Item SUIT_CHESTPLATE;
	public static Item SUIT_LEGGINGS;
	public static Item SUIT_BOOTS;

	private BlackPantherItems() {
	}

	public static void initialize() {
		HERB_BLOCK = Registry.register(BuiltInRegistries.BLOCK, ProjectHeroMod.id("heart_shaped_herb"),
				new FlowerBlock(MobEffects.REGENERATION, 5.0f, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission()
						.instabreak().sound(SoundType.GRASS).lightLevel(state -> 7).offsetType(BlockBehaviour.OffsetType.XZ)
						.pushReaction(PushReaction.DESTROY)));
		HERB = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("heart_shaped_herb"),
				new BlockItem(HERB_BLOCK, new Item.Properties().rarity(Rarity.RARE)));
		SOUP = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("heart_shaped_herb_soup"),
				new HeartShapedHerbSoupItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
						.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6f).alwaysEdible().usingConvertsTo(Items.BOWL).build())));
		DAGGER = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("vibranium_dagger"), new Item(new Item.Properties().stacksTo(1)));
		KIMOYO_BEAD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("kimoyo_bead"), new Item(new Item.Properties().stacksTo(1)));
		HABIT = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("panther_habit"),
				new PantherHabitItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		SUIT_MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("black_panther_suit")),
				new ArmorMaterial(
						Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 3, ArmorItem.Type.CHESTPLATE, 5, ArmorItem.Type.HELMET, 2,
								ArmorItem.Type.BODY, 8),
						15,
						SoundEvents.ARMOR_EQUIP_NETHERITE,
						() -> Ingredient.of(com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_INGOT),
						// textures/models/armor/black_panther_suit_layer_1/2.png: the suit skin's own texture, so a piece worn on its own
						// already looks like the suit (the full set is drawn by the suit layer instead)
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("black_panther_suit"))),
						1.0f,
						0.0f));
		SUIT_HELMET = suitPiece("black_panther_helmet", ArmorItem.Type.HELMET);
		SUIT_CHESTPLATE = suitPiece("black_panther_chestplate", ArmorItem.Type.CHESTPLATE);
		SUIT_LEGGINGS = suitPiece("black_panther_leggings", ArmorItem.Type.LEGGINGS);
		SUIT_BOOTS = suitPiece("black_panther_boots", ArmorItem.Type.BOOTS);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_JUNGLE), GenerationStep.Decoration.VEGETAL_DECORATION, HERB_FEATURE);
	}

	/** No durability: the suit never wears out. */
	private static Item suitPiece(String path, ArmorItem.Type type) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new PantherSuitItem(SUIT_MATERIAL, type, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(HERB);
		output.accept(SOUP);
		output.accept(SUIT_HELMET);
		output.accept(SUIT_CHESTPLATE);
		output.accept(SUIT_LEGGINGS);
		output.accept(SUIT_BOOTS);
	}
}
