package com.projecthero.mod.blackpanther.item;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
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
 */
public final class BlackPantherItems {
	public static final ResourceKey<PlacedFeature> HERB_FEATURE = ResourceKey.create(Registries.PLACED_FEATURE, ProjectHeroMod.id("heart_shaped_herb"));

	public static Block HERB_BLOCK;
	public static Item HERB;
	public static Item SOUP;
	public static Item DAGGER;
	public static Item KIMOYO_BEAD;
	/** v0.17.4: the craftable suit. */
	public static Item HABIT;

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
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_JUNGLE), GenerationStep.Decoration.VEGETAL_DECORATION, HERB_FEATURE);
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(HERB);
		output.accept(SOUP);
		output.accept(HABIT);
	}
}
