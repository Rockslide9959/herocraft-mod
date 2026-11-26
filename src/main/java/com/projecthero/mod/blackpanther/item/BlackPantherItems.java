package com.projecthero.mod.blackpanther.item;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * v0.17.7 (user: "Make the suit just a necklace, player crafts the necklace, equips it and presses H to make it transform into the
 * full suit"): the suit is the crafted Black Panther Necklace ({@link #NECKLACE}, {@link BlackPantherNecklaceItem}), worn in the
 * chestplate slot like the Flash Ring. The four armour pieces of the first v0.17.7 build are legacy items
 * ({@link PantherLegacySuitItem}): they turn into the necklace.
 */
public final class BlackPantherItems {
	public static final ResourceKey<PlacedFeature> HERB_FEATURE = ResourceKey.create(Registries.PLACED_FEATURE, ProjectHeroMod.id("heart_shaped_herb"));

	public static Block HERB_BLOCK;
	public static Item HERB;
	public static Item SOUP;
	public static Item DAGGER;
	public static Item KIMOYO_BEAD;
	/** v0.17.4-0.17.6 the folded suit; legacy only (no recipe, hidden): it turns into the necklace. */
	public static Item HABIT;
	/** v0.17.7: the suit, folded into a vibranium necklace. Worn in the chestplate slot; H lets the suit out. */
	public static Item NECKLACE;
	/** v0.17.7 legacy: the four armour pieces of the first v0.17.7 build (no recipe, hidden); they turn into the necklace. */
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
		NECKLACE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("black_panther_necklace"),
				new BlackPantherNecklaceItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		SUIT_HELMET = legacyPiece("black_panther_helmet");
		SUIT_CHESTPLATE = legacyPiece("black_panther_chestplate");
		SUIT_LEGGINGS = legacyPiece("black_panther_leggings");
		SUIT_BOOTS = legacyPiece("black_panther_boots");
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_JUNGLE), GenerationStep.Decoration.VEGETAL_DECORATION, HERB_FEATURE);
	}

	/** The old ids stay registered so their stacks still load; in a player's inventory each one turns into the necklace. */
	private static Item legacyPiece(String path) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new PantherLegacySuitItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
	}

	/** A legacy suit piece of the first v0.17.7 build. */
	public static boolean isLegacyPiece(ItemStack stack) {
		return stack.getItem() instanceof PantherLegacySuitItem;
	}

	/** The legacy items (the four pieces and the Panther Habit): hidden from JEI. */
	public static Item[] legacyItems() {
		return new Item[] { SUIT_HELMET, SUIT_CHESTPLATE, SUIT_LEGGINGS, SUIT_BOOTS, HABIT };
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(HERB);
		output.accept(SOUP);
		output.accept(NECKLACE);
	}
}
