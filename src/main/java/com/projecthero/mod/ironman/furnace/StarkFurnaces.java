package com.projecthero.mod.ironman.furnace;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * v0.14.26: Stark Industries upgrades of the three vanilla cookers -- the <b>Stark Furnace</b> (furnace upgrade, one item
 * every 2.5 s), the <b>Stark Smelter</b> (blast furnace upgrade, ores and metal gear, one item a second) and the
 * <b>Stark Smoker</b> (smoker upgrade, food, one item a second). They use the vanilla furnace screens, recipes and fuel;
 * only the cook time differs ({@link StarkFurnaceBlockEntity#cookTicks}, applied by
 * {@code AbstractFurnaceBlockEntityStarkMixin}).
 */
public final class StarkFurnaces {
	/** The three kinds: recipe type, ticks per item, and the vanilla screen each one opens. */
	public enum Kind {
		FURNACE("stark_furnace", RecipeType.SMELTING, 50),
		SMELTER("stark_smelter", RecipeType.BLASTING, 20),
		SMOKER("stark_smoker", RecipeType.SMOKING, 20);

		final String id;
		final RecipeType<? extends AbstractCookingRecipe> recipeType;
		final int cookTicks;

		Kind(String id, RecipeType<? extends AbstractCookingRecipe> recipeType, int cookTicks) {
			this.id = id;
			this.recipeType = recipeType;
			this.cookTicks = cookTicks;
		}

		public String id() {
			return id;
		}

		public int cookTicks() {
			return cookTicks;
		}
	}

	public static StarkFurnaceBlock FURNACE;
	public static StarkFurnaceBlock SMELTER;
	public static StarkFurnaceBlock SMOKER;
	public static Item FURNACE_ITEM;
	public static Item SMELTER_ITEM;
	public static Item SMOKER_ITEM;
	public static BlockEntityType<StarkFurnaceBlockEntity> BLOCK_ENTITY;

	private StarkFurnaces() {
	}

	public static void initialize() {
		FURNACE = block(Kind.FURNACE);
		SMELTER = block(Kind.SMELTER);
		SMOKER = block(Kind.SMOKER);
		FURNACE_ITEM = item(FURNACE);
		SMELTER_ITEM = item(SMELTER);
		SMOKER_ITEM = item(SMOKER);
		BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("stark_furnace"),
				BlockEntityType.Builder.of(StarkFurnaceBlockEntity::new, FURNACE, SMELTER, SMOKER).build(null));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(FURNACE_ITEM);
		output.accept(SMELTER_ITEM);
		output.accept(SMOKER_ITEM);
	}

	private static StarkFurnaceBlock block(Kind kind) {
		StarkFurnaceBlock b = new StarkFurnaceBlock(kind, BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_RED)
				.requiresCorrectToolForDrops()
				.strength(4.0f, 8.0f)
				.sound(SoundType.METAL)
				.lightLevel(s -> s.getValue(StarkFurnaceBlock.LIT) ? 14 : 3)
				// v0.15.1: the multi-element models don't fill the whole cube, so neighbours must not cull against them
				// (collision/outline stay a full cube; block states unchanged)
				.noOcclusion());
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(kind.id)), b);
	}

	private static Item item(StarkFurnaceBlock block) {
		BlockItem item = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(block.kind().id)),
				new BlockItem(block, new Item.Properties().rarity(Rarity.UNCOMMON)));
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}
}
