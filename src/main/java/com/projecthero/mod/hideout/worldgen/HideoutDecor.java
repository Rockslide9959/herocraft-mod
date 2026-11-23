package com.projecthero.mod.hideout.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * v0.17.7: small dressing helpers the three new hideout builders share (fence / bar runs with their connections set,
 * ladders, candles, skulls, lanterns, stairs, slabs, logs). Everything goes through the {@link HideoutCanvas}, so it is
 * rotated and clipped like the rest.
 */
final class HideoutDecor {
	private HideoutDecor() {
	}

	/** A fence / iron-bar / pane run along local x at (y, z), each post joined to its neighbours. */
	static void runX(HideoutCanvas c, int x0, int x1, int y, int z, Block block) {
		int a = Math.min(x0, x1);
		int b = Math.max(x0, x1);
		for (int x = a; x <= b; x++) {
			c.set(x, y, z, block.defaultBlockState().setValue(CrossCollisionBlock.WEST, x > a).setValue(CrossCollisionBlock.EAST, x < b));
		}
	}

	/** As {@link #runX} along local z. */
	static void runZ(HideoutCanvas c, int z0, int z1, int y, int x, Block block) {
		int a = Math.min(z0, z1);
		int b = Math.max(z0, z1);
		for (int z = a; z <= b; z++) {
			c.set(x, y, z, block.defaultBlockState().setValue(CrossCollisionBlock.NORTH, z > a).setValue(CrossCollisionBlock.SOUTH, z < b));
		}
	}

	/** A ladder column from {@code y0} to {@code y1}, climbing on the wall to its {@code wall} side. */
	static void ladder(HideoutCanvas c, int x, int y0, int y1, int z, Direction wall) {
		for (int y = y0; y <= y1; y++) {
			c.set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, wall.getOpposite()));
		}
	}

	static void candles(HideoutCanvas c, int x, int y, int z, Block candle, int count, boolean lit) {
		c.set(x, y, z, candle.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, count))).setValue(CandleBlock.LIT, lit));
	}

	static void skull(HideoutCanvas c, int x, int y, int z, int rotation) {
		c.set(x, y, z, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rotation & 15));
	}

	static void lantern(HideoutCanvas c, int x, int y, int z, boolean soul, boolean hanging) {
		c.set(x, y, z, (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, hanging));
	}

	/** A hanging lantern on a chain of {@code chain} links under the block at {@code yTop}. */
	static void chainLantern(HideoutCanvas c, int x, int yTop, int z, int chain, boolean soul) {
		for (int i = 1; i <= chain; i++) {
			c.set(x, yTop - i, z, Blocks.CHAIN);
		}
		lantern(c, x, yTop - chain - 1, z, soul, true);
	}

	static BlockState stair(Block stairs, Direction facing, boolean top) {
		return stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block slab, boolean top) {
		return slab.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState log(Block log, Direction.Axis axis) {
		return log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	/** Picks one of {@code options} by the canvas noise at (x, y, z, salt). */
	static BlockState pick(HideoutCanvas c, int x, int y, int z, int salt, BlockState... options) {
		int i = (int) (c.noise(x, y, z, salt) * options.length);
		return options[Math.min(options.length - 1, i)];
	}

	/** Weathered stone brick: plain / mossy / cracked by noise. */
	static BlockState weathered(HideoutCanvas c, int x, int y, int z, int salt) {
		double n = c.noise(x, y, z, salt);
		if (n < 0.22) {
			return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		}
		if (n < 0.4) {
			return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		}
		return Blocks.STONE_BRICKS.defaultBlockState();
	}
}
