package com.projecthero.mod.ultron.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * v0.15.12: the Ultron Uprising's blocks -- the Ultron Beacon (the uplink), the Ultron Core trophy, and the pylon segment
 * (never placed: only its model is used, to draw the relay pylons).
 */
public final class UltronBlocks {
	public static UltronBeaconBlock ULTRON_BEACON;
	public static UltronCoreBlock ULTRON_CORE;
	public static Block PYLON_SEGMENT;
	public static Block PYLON_HEAD;
	public static BlockEntityType<UltronCoreBlockEntity> ULTRON_CORE_BE;

	private UltronBlocks() {
	}

	public static void initialize() {
		ULTRON_BEACON = register("ultron_beacon", new UltronBeaconBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(5.0f, 1200.0f)
				.requiresCorrectToolForDrops()
				.sound(SoundType.NETHERITE_BLOCK)
				.lightLevel(s -> s.getValue(UltronBeaconBlock.ACTIVE) ? 12 : 3)));
		ULTRON_CORE = register("ultron_core", new UltronCoreBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(3.0f, 600.0f)
				.sound(SoundType.NETHERITE_BLOCK)
				.noOcclusion()
				.lightLevel(s -> 7)));
		PYLON_SEGMENT = register("ultron_pylon_segment", new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
				.strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()));
		PYLON_HEAD = register("ultron_pylon_head", new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
				.strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()));
		ULTRON_CORE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("ultron_core"),
				BlockEntityType.Builder.of(UltronCoreBlockEntity::new, ULTRON_CORE).build(null));
	}

	private static <T extends Block> T register(String path, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}
}
