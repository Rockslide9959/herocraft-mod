package com.projecthero.mod.omnitrix.alien.diamondhead;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.1 Diamondhead: his conjured Petrosapien crystal -- a glassy green block (translucent, a faint glow) that every crystal
 * construct is made of (walls, the dome, ramps, the vault pillar, Use-placed blocks). It is only ever placed through
 * {@code TempBlocks} (it reverts to what was there), drops nothing and has no item.
 */
public final class DiamondheadBlocks {
	public static Block CRYSTAL;

	private DiamondheadBlocks() {
	}

	static void initialize() {
		CRYSTAL = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("diamondhead_crystal")),
				new TransparentBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.EMERALD)
						.strength(1.2f, 6.0f)
						.sound(SoundType.AMETHYST)
						.noOcclusion()
						.lightLevel(s -> 5)
						.noLootTable()
						.pushReaction(PushReaction.BLOCK)
						.isValidSpawn((s, l, p, t) -> false)
						.isRedstoneConductor((s, l, p) -> false)
						.isSuffocating((s, l, p) -> false)
						.isViewBlocking((s, l, p) -> false)));
	}
}
