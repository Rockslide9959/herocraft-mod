package com.projecthero.mod.galactus.orbit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: the built parts of the orbit that are not islands -- the <b>hull</b> (Stage 2 and 3: the
 * islands pulled into the Worldship's deck, a 34-block disc of deepslate tiles ringed in blackstone with amethyst spokes
 * and a jagged rock keel under it), the Stage 3 <b>cover pillars</b> and the <b>gravity pads</b>. Every shape is a pure
 * function of the instance origin, like the islands.
 */
public final class OrbitArena {
	public static final int HULL_R = 34;
	public static final int PILLAR_RING = 17;
	public static final int PILLARS = 6;
	public static final int PILLAR_H = 5;

	private OrbitArena() {
	}

	private static double hash(int a, int b, int c) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Every block of the hull round {@code o}. */
	public static void hull(BlockPos o, BiConsumer<BlockPos, BlockState> out) {
		int top = GalactusOrbit.PLANE_Y;
		for (int dx = -HULL_R - 1; dx <= HULL_R + 1; dx++) {
			for (int dz = -HULL_R - 1; dz <= HULL_R + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > HULL_R + 0.5) {
					continue;
				}
				int x = o.getX() + dx, z = o.getZ() + dz;
				double ang = Math.atan2(dz, dx);
				double t = d / HULL_R;
				int depth = 2 + (int) ((1 - t * t) * 12 + hash(dx, dz, 3) * 3);
				for (int y = top - depth; y <= top; y++) {
					BlockState s;
					if (y == top) {
						s = surface(d, ang, dx, dz);
					} else if (y >= top - 1) {
						s = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
					} else {
						double n = hash(dx * 7 + y, dz * 3 - y, 9);
						s = n < 0.05 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
								: n < 0.5 ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.8 ? Blocks.BASALT.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
					}
					out.accept(new BlockPos(x, y, z), s);
				}
				// the low rim: a wall every few blocks, open between (so you can still fall)
				if (d > HULL_R - 0.7 && ((int) Math.floor((ang + Math.PI) / (Math.PI * 2) * 48)) % 3 != 0) {
					out.accept(new BlockPos(x, top + 1, z), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
				}
			}
		}
	}

	private static BlockState surface(double d, double ang, int dx, int dz) {
		if (d < 1.5) {
			return Blocks.LODESTONE.defaultBlockState();
		}
		if (d < 3.5) {
			return Blocks.CRYING_OBSIDIAN.defaultBlockState();
		}
		for (int ring : new int[] { 9, 17, 25, 32 }) {
			if (Math.abs(d - ring) < 0.6) {
				return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			}
		}
		double spokes = (ang + Math.PI) / (Math.PI * 2) * 8;
		double frac = spokes - Math.floor(spokes);
		if (d > 4 && (frac < 0.5 / (d * 0.35) || frac > 1 - 0.5 / (d * 0.35))) {
			return Blocks.AMETHYST_BLOCK.defaultBlockState();
		}
		double n = hash(dx, dz, 1);
		if (n < 0.08) {
			return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
		}
		return Blocks.DEEPSLATE_TILES.defaultBlockState();
	}

	public static int buildHull(ServerLevel level, BlockPos o) {
		int[] n = { 0 };
		hull(o, (p, s) -> {
			level.setBlock(p, s, 2 | 16);
			n[0]++;
		});
		return n[0];
	}

	/** The hull's top positions (for the islands' rock to land on), sampled. */
	public static List<Vec3> hullTops(BlockPos o, int step) {
		List<Vec3> out = new ArrayList<>();
		for (int dx = -HULL_R; dx <= HULL_R; dx += step) {
			for (int dz = -HULL_R; dz <= HULL_R; dz += step) {
				if (dx * dx + dz * dz <= HULL_R * HULL_R) {
					out.add(new Vec3(o.getX() + dx, GalactusOrbit.PLANE_Y, o.getZ() + dz));
				}
			}
		}
		return out;
	}

	public static boolean onHull(BlockPos o, Vec3 at) {
		double dx = at.x - (o.getX() + 0.5), dz = at.z - (o.getZ() + 0.5);
		return dx * dx + dz * dz <= (HULL_R + 0.5) * (HULL_R + 0.5);
	}

	/** A safe standing spot on the hull, {@code r} out at angle {@code a}. */
	public static Vec3 hullSpot(BlockPos o, double r, double a) {
		return new Vec3(o.getX() + 0.5 + Math.cos(a) * r, GalactusOrbit.PLANE_Y + 1, o.getZ() + 0.5 + Math.sin(a) * r);
	}

	// ---------------------------------------------------------------- pillars (Stage 3 cover)

	/** Pillar {@code i}'s corner (2x2). */
	public static BlockPos pillar(BlockPos o, int i) {
		double a = i * Math.PI * 2 / PILLARS + Math.PI / PILLARS;
		return new BlockPos(o.getX() + Mth.floor(Math.cos(a) * PILLAR_RING), GalactusOrbit.PLANE_Y + 1, o.getZ() + Mth.floor(Math.sin(a) * PILLAR_RING));
	}

	public static void pillarBlocks(BlockPos base, BiConsumer<BlockPos, BlockState> out) {
		for (int y = 0; y < PILLAR_H; y++) {
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					BlockState s = y == PILLAR_H - 1 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
							: y == 2 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
					out.accept(base.offset(dx, y, dz), s);
				}
			}
		}
	}

	public static void buildPillar(ServerLevel level, BlockPos base) {
		pillarBlocks(base, (p, s) -> level.setBlock(p, s, 2 | 16));
	}

	/** The pillar breaks: its blocks fly apart. */
	public static void collapsePillar(ServerLevel level, BlockPos base) {
		var rnd = level.getRandom();
		pillarBlocks(base, (p, s) -> {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			Vec3 from = Vec3.atLowerCornerOf(p);
			Vec3 to = from.add((rnd.nextDouble() - 0.5) * 10, -2 - rnd.nextDouble() * 4, (rnd.nextDouble() - 0.5) * 10);
			OrbitDebris.fly(level, s, from, to, 1f, new org.joml.Quaternionf(),
					new org.joml.Quaternionf().rotateXYZ(rnd.nextFloat() * 4, rnd.nextFloat() * 4, rnd.nextFloat() * 4), 1, 24, 0);
		});
	}

	// ---------------------------------------------------------------- gravity pads

	/** A pad: where it is (the middle block of its 3x3 top), and where it throws you. */
	public record Pad(BlockPos at, Vec3 to) {
	}

	/** The pad's look: lodestone in the middle, amethyst round it, set into whatever surface is there. */
	public static void buildPad(ServerLevel level, BlockPos at) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(at.offset(dx, 0, dz), dx == 0 && dz == 0 ? Blocks.LODESTONE.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState(),
						2 | 16);
			}
		}
	}

	/** Velocity that lands a player at {@code to} from {@code from} after about {@code ticks} ticks (vanilla gravity and drag). */
	public static Vec3 launch(Vec3 from, Vec3 to, int ticks) {
		double g = 0.08, drag = 0.98;
		// horizontal: sum of drag^i for i < ticks (players' horizontal drag in air is ~0.91, but launches keep it short)
		double hd = 0;
		double f = 1;
		for (int i = 0; i < ticks; i++) {
			hd += f;
			f *= 0.91;
		}
		double vx = (to.x - from.x) / hd, vz = (to.z - from.z) / hd;
		// vertical with drag 0.98 and gravity: y(t) = sum of v_i, v_{i+1} = (v_i - g) * drag
		double lo = -2, hi = 6;
		for (int it = 0; it < 40; it++) {
			double mid = (lo + hi) / 2;
			double v = mid, y = 0;
			for (int i = 0; i < ticks; i++) {
				y += v;
				v = (v - g) * drag;
			}
			if (y < to.y - from.y) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return new Vec3(vx, (lo + hi) / 2, vz);
	}
}
