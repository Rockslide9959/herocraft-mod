package com.projecthero.mod.hulk.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hulk.block.GammaReactorBlock;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** v0.13.12 (Hulk Phase 4): the Gamma Serum and the Gamma Reactor block. */
public final class HulkItems {
	public static Item GAMMA_SERUM;
	public static Block GAMMA_REACTOR;
	public static Item GAMMA_REACTOR_ITEM;

	private HulkItems() {
	}

	public static void initialize() {
		GAMMA_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("gamma_serum"),
				new GammaSerumItem(new Item.Properties().rarity(Rarity.EPIC)));
		GAMMA_REACTOR = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("gamma_reactor")),
				new GammaReactorBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(5.0f, 1200.0f).sound(SoundType.METAL)
						.requiresCorrectToolForDrops().lightLevel(s -> 15).emissiveRendering((s, l, p) -> true)));
		BlockItem item = new BlockItem(GAMMA_REACTOR, new Item.Properties().rarity(Rarity.RARE));
		GAMMA_REACTOR_ITEM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("gamma_reactor"), item);
		item.registerBlocks(Item.BY_BLOCK, item);
	}

	/** Appended to the {@code projecthero:superheroes} creative tab (creative only -- in survival the serum is loot). */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(GAMMA_SERUM);
		output.accept(GAMMA_REACTOR_ITEM);
	}
}
