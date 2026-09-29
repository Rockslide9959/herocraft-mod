package com.projecthero.mod.symbiote.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Symbiote blocks: the Symbiote Meteorite a free Symbiote crawls out of when it is broken open. */
public final class SymbioteBlocks {
	/**
	 * Dark alien rock with pulsing violet veins, the core of every {@code symbiote_meteor} crater.
	 * Tough (between ancient debris and obsidian) but an iron pickaxe is enough; it cannot be pushed
	 * by pistons, so the organism inside cannot be shunted around.
	 */
	public static final Block SYMBIOTE_METEORITE = register("symbiote_meteorite",
			new SymbioteMeteoriteBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK).strength(30.0f, 1200.0f).sound(SoundType.ANCIENT_DEBRIS)
					.requiresCorrectToolForDrops().lightLevel(s -> 3).pushReaction(PushReaction.BLOCK)));

	public static final Item SYMBIOTE_METEORITE_ITEM = registerItem("symbiote_meteorite",
			new BlockItem(SYMBIOTE_METEORITE, new Item.Properties().rarity(Rarity.RARE)));

	private SymbioteBlocks() {
	}

	public static void initialize() {
		// Registration happens via the static initializers above; this exists for an explicit init call.
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
