package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.1: the survival constructs' hard-light blocks -- the Scaffold Tower's column / deck and the Hard-Light Chest.
 * Same rules as {@code GreenLanternBlocks#HARD_LIGHT}: no item, no loot, unbreakable, immovable, no mob spawns, drawn
 * full-bright. Registered from {@code GreenLanternSurvival#initialize}.
 */
public final class GreenLanternSurvivalBlocks {
	public static final Block HARD_LIGHT_SCAFFOLD = register("hard_light_scaffold",
			new HardLightScaffoldBlock(hardLight().lightLevel(s -> 7).forceSolidOn()));
	public static final Block HARD_LIGHT_CHEST = register("hard_light_chest",
			new HardLightChestBlock(hardLight().lightLevel(s -> 9).forceSolidOn()));

	private GreenLanternSurvivalBlocks() {
	}

	public static void initialize() {
		// static initialisers above
	}

	private static BlockBehaviour.Properties hardLight() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(-1.0f, 3600000.0f).noLootTable()
				.sound(SoundType.AMETHYST).noOcclusion()
				.pushReaction(PushReaction.BLOCK)
				.isValidSpawn((state, level, pos, type) -> false)
				.isRedstoneConductor((state, level, pos) -> false)
				.isSuffocating((state, level, pos) -> false)
				.isViewBlocking((state, level, pos) -> false)
				.emissiveRendering((state, level, pos) -> true);
	}

	private static Block register(String path, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}
}
