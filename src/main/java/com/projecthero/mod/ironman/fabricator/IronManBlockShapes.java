package com.projecthero.mod.ironman.fabricator;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.14.21: shared helpers for the facing Iron Man blocks (Stark Fabricator, Suit Platform). Their models are authored
 * with the front on the north side (block-pixel boxes, 0..16); the blockstate turns them clockwise (seen from above)
 * by 90 degrees per step north -> east -> south -> west, and these helpers turn the matching hit boxes and points the
 * same way so the outline, collision and particles always line up with the rendered model.
 */
public final class IronManBlockShapes {
	private IronManBlockShapes() {
	}

	/** Build one shape per horizontal facing from north-facing boxes given as {x0, y0, z0, x1, y1, z1} in pixels. */
	public static Map<Direction, VoxelShape> byFacing(double[]... boxes) {
		Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			VoxelShape shape = Shapes.empty();
			for (double[] b : boxes) {
				double[] p = rotate(b[0], b[2], facing);
				double[] q = rotate(b[3], b[5], facing);
				shape = Shapes.or(shape, Block.box(Math.min(p[0], q[0]), b[1], Math.min(p[1], q[1]),
						Math.max(p[0], q[0]), b[4], Math.max(p[1], q[1])));
			}
			out.put(facing, shape.optimize());
		}
		return out;
	}

	/** A model-space point (pixels, north-facing) turned to {@code facing}, as a block-relative position in blocks. */
	public static Vec3 point(double x, double y, double z, Direction facing) {
		double[] r = rotate(x, z, facing);
		return new Vec3(r[0] / 16.0, y / 16.0, r[1] / 16.0);
	}

	/** Turn (x, z) clockwise about the block centre, one quarter per step from north (same as the blockstate y). */
	private static double[] rotate(double x, double z, Direction facing) {
		return switch (facing) {
			case EAST -> new double[] {16.0 - z, x};
			case SOUTH -> new double[] {16.0 - x, 16.0 - z};
			case WEST -> new double[] {z, 16.0 - x};
			default -> new double[] {x, z};
		};
	}
}
