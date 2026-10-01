package com.projecthero.mod.horde;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.horde.entity.HordeEntityTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** v0.14.12: the three Horde blocks, their items and the new mobs' spawn eggs. */
public final class HordeBlocks {
	public static HordeBlock ZOMBIE_HORDE;
	public static HordeBlock SKELETON_HORDE;
	public static HordeBlock SPIDER_HORDE;
	public static Item ZOMBIE_HORDE_ITEM;
	public static Item SKELETON_HORDE_ITEM;
	public static Item SPIDER_HORDE_ITEM;
	public static Item HORDE_SPIDER_SPAWN_EGG;
	public static Item BONE_TYRANT_SPAWN_EGG;
	public static Item BROOD_QUEEN_SPAWN_EGG;

	private HordeBlocks() {
	}

	static void initialize() {
		ZOMBIE_HORDE = block("zombie_horde", HordeKind.ZOMBIE, MapColor.COLOR_GREEN, SoundType.SLIME_BLOCK);
		SKELETON_HORDE = block("skeleton_horde", HordeKind.SKELETON, MapColor.SAND, SoundType.BONE_BLOCK);
		SPIDER_HORDE = block("spider_horde", HordeKind.SPIDER, MapColor.COLOR_BLACK, SoundType.WOOL);
		ZOMBIE_HORDE_ITEM = blockItem("zombie_horde", ZOMBIE_HORDE, Rarity.UNCOMMON);
		SKELETON_HORDE_ITEM = blockItem("skeleton_horde", SKELETON_HORDE, Rarity.RARE);
		SPIDER_HORDE_ITEM = blockItem("spider_horde", SPIDER_HORDE, Rarity.EPIC);
		HORDE_SPIDER_SPAWN_EGG = item("horde_spider_spawn_egg", new SpawnEggItem(HordeEntityTypes.HORDE_SPIDER, 0x2A1F1A, 0xE0E0E0, new Item.Properties()));
		BONE_TYRANT_SPAWN_EGG = item("bone_tyrant_spawn_egg", new SpawnEggItem(HordeEntityTypes.BONE_TYRANT, 0xE6E2D3, 0xD4AF37, new Item.Properties()));
		BROOD_QUEEN_SPAWN_EGG = item("brood_queen_spawn_egg", new SpawnEggItem(HordeEntityTypes.BROOD_QUEEN, 0x1A0D10, 0xC0182A, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ZOMBIE_HORDE_ITEM);
		output.accept(SKELETON_HORDE_ITEM);
		output.accept(SPIDER_HORDE_ITEM);
		output.accept(HORDE_SPIDER_SPAWN_EGG);
		output.accept(BONE_TYRANT_SPAWN_EGG);
		output.accept(BROOD_QUEEN_SPAWN_EGG);
	}

	private static HordeBlock block(String path, HordeKind kind, MapColor color, SoundType sound) {
		HordeBlock block = new HordeBlock(kind, BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(3.0f, 1200.0f)
				.sound(sound)
				.lightLevel(s -> s.getValue(HordeBlock.ACTIVE) ? 11 : 0));
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static Item blockItem(String path, Block block, Rarity rarity) {
		BlockItem item = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)),
				new BlockItem(block, new Item.Properties().rarity(rarity)));
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
