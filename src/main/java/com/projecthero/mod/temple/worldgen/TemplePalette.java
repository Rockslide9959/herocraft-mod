package com.projecthero.mod.temple.worldgen;

import com.projecthero.mod.hideout.worldgen.HideoutCanvas;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * v0.17.7: the Ancient Temple's two looks -- <b>desert</b> (sandstone in every finish, orange terracotta and gold, lapis
 * bands, ochre-lit) and <b>jungle</b> (mossy and cracked stone brick, moss and vines, green and gold, shroom-lit). Walls and
 * floors are noise-mixed per block so no surface is one flat colour.
 */
public final class TemplePalette {
	public final boolean desert;

	TemplePalette(boolean desert) {
		this.desert = desert;
	}

	/** A wall block at (x, y, z): mostly the main brick with weathered variants mixed in. */
	public BlockState wall(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 11);
		if (desert) {
			return (n < 0.6 ? Blocks.CUT_SANDSTONE : n < 0.85 ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
		}
		return (n < 0.45 ? Blocks.STONE_BRICKS : n < 0.75 ? Blocks.MOSSY_STONE_BRICKS : n < 0.93 ? Blocks.CRACKED_STONE_BRICKS
				: Blocks.MOSSY_COBBLESTONE).defaultBlockState();
	}

	public BlockState floor(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 12);
		if (desert) {
			return (n < 0.7 ? Blocks.SMOOTH_SANDSTONE : n < 0.92 ? Blocks.CUT_SANDSTONE : Blocks.SANDSTONE).defaultBlockState();
		}
		return (n < 0.5 ? Blocks.POLISHED_ANDESITE : n < 0.8 ? Blocks.STONE_BRICKS : n < 0.95 ? Blocks.MOSSY_STONE_BRICKS
				: Blocks.MOSS_BLOCK).defaultBlockState();
	}

	public BlockState trim() {
		return (desert ? Blocks.CHISELED_SANDSTONE : Blocks.CHISELED_STONE_BRICKS).defaultBlockState();
	}

	public BlockState pillar() {
		return (desert ? Blocks.CUT_SANDSTONE : Blocks.STONE_BRICKS).defaultBlockState();
	}

	public BlockState smooth() {
		return (desert ? Blocks.SMOOTH_SANDSTONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
	}

	/** The coloured band (terracotta). */
	public BlockState band() {
		return (desert ? Blocks.ORANGE_TERRACOTTA : Blocks.GREEN_TERRACOTTA).defaultBlockState();
	}

	/** The second band colour. */
	public BlockState band2() {
		return (desert ? Blocks.BLUE_TERRACOTTA : Blocks.CYAN_TERRACOTTA).defaultBlockState();
	}

	public BlockState gold() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	/** A ceiling light. */
	public BlockState light() {
		return (desert ? Blocks.OCHRE_FROGLIGHT : Blocks.SHROOMLIGHT).defaultBlockState();
	}

	public Block stairsBlock() {
		return desert ? Blocks.SANDSTONE_STAIRS : Blocks.STONE_BRICK_STAIRS;
	}

	public BlockState stairs(net.minecraft.core.Direction facing) {
		return stairsBlock().defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	public BlockState stairsTop(net.minecraft.core.Direction facing) {
		return stairsBlock().defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
	}

	public BlockState slab(boolean top) {
		return (desert ? Blocks.SANDSTONE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	public BlockState wallPost() {
		return (desert ? Blocks.SANDSTONE_WALL : Blocks.MOSSY_STONE_BRICK_WALL).defaultBlockState();
	}

	/** The ground round the temple. */
	public BlockState ground() {
		return (desert ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
	}
}
