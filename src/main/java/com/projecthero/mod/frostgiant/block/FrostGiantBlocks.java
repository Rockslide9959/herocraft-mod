package com.projecthero.mod.frostgiant.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** v0.16.1: the Frost Giant Assault's blocks -- the Jotunheim Rune Stone (the Rift Anchor) and the Jotun Skull trophy. */
public final class FrostGiantBlocks {
	public static JotunheimRuneStoneBlock RUNE_STONE;
	public static JotunSkullBlock JOTUN_SKULL;

	private FrostGiantBlocks() {
	}

	public static void initialize() {
		RUNE_STONE = register("jotunheim_rune_stone", new JotunheimRuneStoneBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.ICE)
				.strength(4.0f, 1200.0f)
				.requiresCorrectToolForDrops()
				.sound(SoundType.STONE)
				.lightLevel(s -> s.getValue(JotunheimRuneStoneBlock.ACTIVE) ? 10 : 3)));
		JOTUN_SKULL = register("jotun_skull", new JotunSkullBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.ICE)
				.strength(2.0f, 600.0f)
				.sound(SoundType.BONE_BLOCK)
				.noOcclusion()
				.lightLevel(s -> 6)));
	}

	private static <T extends Block> T register(String path, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}
}
