package com.projecthero.mod.kryptonian.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.kryptonian.block.KryptoniteBlock;
import com.projecthero.mod.kryptonian.block.MeteorCoreBlock;
import com.projecthero.mod.kryptonian.meteor.KryptoniteMeteorEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * v0.14.8: everything the Kryptonite Meteor brings -- kryptonite ore (drops shards), the kryptonite block (nine
 * shards), the Meteor Core (the heart of the crater, drops the Kryptonian Crystal) and the falling meteor itself.
 * v0.14.9: plus the Superman Suit ({@link com.projecthero.mod.kryptonian.SupermanSuit}).
 */
public final class KryptonianItems {
	public static Block KRYPTONITE_ORE;
	public static Block KRYPTONITE_BLOCK;
	public static Block METEOR_CORE;
	public static Item KRYPTONITE_ORE_ITEM;
	public static Item KRYPTONITE_BLOCK_ITEM;
	public static Item METEOR_CORE_ITEM;
	public static Item KRYPTONITE_SHARD;
	public static Item KRYPTONIAN_CRYSTAL;
	public static EntityType<KryptoniteMeteorEntity> METEOR;

	private KryptonianItems() {
	}

	public static void initialize() {
		KRYPTONITE_ORE = block("kryptonite_ore", new KryptoniteBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(4.0f, 6.0f).sound(SoundType.AMETHYST)
				.requiresCorrectToolForDrops().lightLevel(s -> 9).emissiveRendering((s, l, p) -> true)));
		KRYPTONITE_BLOCK = block("kryptonite_block", new KryptoniteBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(5.0f, 6.0f).sound(SoundType.AMETHYST)
				.requiresCorrectToolForDrops().lightLevel(s -> 12).emissiveRendering((s, l, p) -> true)));
		METEOR_CORE = block("meteor_core", new MeteorCoreBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE).strength(6.0f, 1200.0f).sound(SoundType.ANCIENT_DEBRIS)
				.requiresCorrectToolForDrops().lightLevel(s -> 15).emissiveRendering((s, l, p) -> true)));
		KRYPTONITE_ORE_ITEM = blockItem("kryptonite_ore", KRYPTONITE_ORE, Rarity.UNCOMMON);
		KRYPTONITE_BLOCK_ITEM = blockItem("kryptonite_block", KRYPTONITE_BLOCK, Rarity.UNCOMMON);
		METEOR_CORE_ITEM = blockItem("meteor_core", METEOR_CORE, Rarity.EPIC);
		KRYPTONITE_SHARD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("kryptonite_shard"),
				new KryptoniteShardItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
		KRYPTONIAN_CRYSTAL = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("kryptonian_crystal"),
				new KryptonianCrystalItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		METEOR = Registry.register(BuiltInRegistries.ENTITY_TYPE,
				ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("kryptonite_meteor")),
				EntityType.Builder.<KryptoniteMeteorEntity>of(KryptoniteMeteorEntity::new, MobCategory.MISC)
						.sized(2.0f, 2.0f)
						.clientTrackingRange(16)
						.updateInterval(1)
						.noSave()
						.noSummon()
						.fireImmune()
						.build("kryptonite_meteor"));
		// v0.14.9: the craftable, Kryptonian-only Superman Suit
		com.projecthero.mod.kryptonian.SupermanSuit.initialize();
	}

	private static Block block(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(name)), block);
	}

	private static Item blockItem(String name, Block block, Rarity rarity) {
		BlockItem item = new BlockItem(block, new Item.Properties().rarity(rarity));
		Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(name), item);
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}

	/** Appended to the {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(KRYPTONIAN_CRYSTAL);
		output.accept(KRYPTONITE_SHARD);
		output.accept(KRYPTONITE_ORE_ITEM);
		output.accept(KRYPTONITE_BLOCK_ITEM);
		output.accept(METEOR_CORE_ITEM);
		output.accept(com.projecthero.mod.kryptonian.SupermanSuit.CHESTPLATE);
		output.accept(com.projecthero.mod.kryptonian.SupermanSuit.LEGGINGS);
		output.accept(com.projecthero.mod.kryptonian.SupermanSuit.BOOTS);
	}
}
