package com.projecthero.mod.ironman;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlock;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformMenu;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlock;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorMenu;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Iron Man blocks: the Stark Fabricator (custom crafting station) and the Iron Man Suit Platform,
 * plus their block-entity types and the Fabricator's menu type.
 */
public final class IronManBlocks {
	public static final Block STARK_FABRICATOR = register("stark_fabricator",
			new StarkFabricatorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLUE).strength(4.0f, 8.0f).sound(SoundType.NETHERITE_BLOCK)
					.requiresCorrectToolForDrops().lightLevel(s -> 7)
					.noOcclusion()));

	public static final Block IRON_MAN_SUIT_PLATFORM = register("iron_man_suit_platform",
			new IronManSuitPlatformBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL).strength(4.0f, 8.0f).sound(SoundType.NETHERITE_BLOCK)
					.requiresCorrectToolForDrops().lightLevel(s -> 5)
					.noOcclusion()));

	public static final Item STARK_FABRICATOR_ITEM = registerItem("stark_fabricator",
			new BlockItem(STARK_FABRICATOR, new Item.Properties().rarity(Rarity.RARE)));
	public static final Item IRON_MAN_SUIT_PLATFORM_ITEM = registerItem("iron_man_suit_platform",
			new BlockItem(IRON_MAN_SUIT_PLATFORM, new Item.Properties().rarity(Rarity.RARE)));

	public static final BlockEntityType<StarkFabricatorBlockEntity> STARK_FABRICATOR_BE =
			BlockEntityType.Builder.of(StarkFabricatorBlockEntity::new, STARK_FABRICATOR).build(null);
	public static final BlockEntityType<IronManSuitPlatformBlockEntity> SUIT_PLATFORM_BE =
			BlockEntityType.Builder.of(IronManSuitPlatformBlockEntity::new, IRON_MAN_SUIT_PLATFORM).build(null);

	public static final MenuType<StarkFabricatorMenu> STARK_FABRICATOR_MENU =
			new ExtendedScreenHandlerType<>(StarkFabricatorMenu::new, BlockPos.STREAM_CODEC);
	public static final MenuType<IronManSuitPlatformMenu> SUIT_PLATFORM_MENU =
			new ExtendedScreenHandlerType<>(IronManSuitPlatformMenu::new, BlockPos.STREAM_CODEC);

	private IronManBlocks() {
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("stark_fabricator"), STARK_FABRICATOR_BE);
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("iron_man_suit_platform"), SUIT_PLATFORM_BE);
		Registry.register(BuiltInRegistries.MENU, ProjectHeroMod.id("stark_fabricator"), STARK_FABRICATOR_MENU);
		Registry.register(BuiltInRegistries.MENU, ProjectHeroMod.id("iron_man_suit_platform"), SUIT_PLATFORM_MENU);
	}

	private static Block register(String path, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK,
				ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static Item registerItem(String path, Item item) {
		Item registered = Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
		if (registered instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, blockItem);
		}
		return registered;
	}
}
