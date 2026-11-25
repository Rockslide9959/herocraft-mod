package com.projecthero.mod.worldgen;

import java.util.function.IntBinaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * <b>Mjolnir's Crater</b> (v0.17.7 look): a scorched impact bowl carved into whatever terrain is really there -- a blackened
 * core of blackstone, basalt and coal with a few still-glowing magma cracks, dark lightning scars running out across the
 * floor and over the rim, a raised, broken lip -- and at its heart a <b>cracked stone plinth</b> (a stepped base of cracked
 * stone bricks round a chiseled pillar) with Mjolnir standing on it. The hammer itself is placed once by
 * {@link CraterAmbience}; while it is there the crater is protected ({@code thoracquire.CraterGuard}) and a storm cloud
 * hangs over it (client).
 *
 * <h2>Seamless across chunks</h2>
 * The piece is post-processed once per chunk it touches, each with its own random. So every shape decision (the rim wobble,
 * the scars, the materials) comes from a hash of the world position and the crater centre, never from the chunk's random --
 * the four quarters of the bowl always meet.
 *
 * <h2>No stored state</h2>
 * Only the bounding box (which {@link StructurePiece} saves) is needed: the centre is its middle, and
 * {@link #hammerSpawnPos} rereads the world's heightmap at that column (the plinth top). Craters generated before v0.17.7 keep
 * their old look; their hammers work exactly the same.
 */
public class MjolnirCraterPiece extends StructurePiece {
	/** Bowl radius before the rim wobble (~17 blocks across). */
	private static final int RADIUS = 8;
	/** Deepest point of the bowl below the surface. */
	private static final int MAX_DEPTH = 4;
	/** Rim wobble (blocks). */
	private static final double EDGE_JITTER = 1.4;
	/** Lightning scars running out from the plinth. */
	private static final int SCARS = 6;

	public MjolnirCraterPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.MJOLNIR_CRATER, 0, new BoundingBox(
				centerX - RADIUS - 4, estimatedSurfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 4,
				centerX + RADIUS + 4, estimatedSurfaceY + 10, centerZ + RADIUS + 4));
	}

	public MjolnirCraterPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.MJOLNIR_CRATER, tag);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		// Nothing beyond the bounding box the base class already saves -- see the class javadoc.
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		carve(level, chunkBox, centerX, centerZ,
				(x, z) -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1);
	}

	/**
	 * Builds a whole crater round ({@code cx}, {@code cz}) on flat ground whose top block is at {@code surfaceY} (gametests and
	 * the screenshot harness). Returns the hammer spot: the block above the plinth.
	 */
	public static BlockPos buildForTest(WorldGenLevel level, int cx, int surfaceY, int cz) {
		BoundingBox all = new BoundingBox(cx - RADIUS - 4, surfaceY - 20, cz - RADIUS - 4, cx + RADIUS + 4, surfaceY + 20, cz + RADIUS + 4);
		carve(level, all, cx, cz, (x, z) -> surfaceY);
		return new BlockPos(cx, plinthTop(surfaceY) + 1, cz);
	}

	/**
	 * Builds a whole crater round ({@code cx}, {@code cz}) following the real terrain (the screenshot harness; an op tool later).
	 * Returns the hammer spot above the plinth.
	 */
	public static BlockPos buildOnTerrain(WorldGenLevel level, int cx, int cz) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
		BoundingBox all = new BoundingBox(cx - RADIUS - 4, surface - 24, cz - RADIUS - 4, cx + RADIUS + 4, surface + 24, cz + RADIUS + 4);
		carve(level, all, cx, cz, (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1);
		return new BlockPos(cx, plinthTop(surface) + 1, cz);
	}

	/** Y of the plinth's top block for a crater whose centre column surface is {@code surfaceY}. */
	public static int plinthTop(int surfaceY) {
		return surfaceY - (MAX_DEPTH + 1) + 2;
	}

	/** A stable 0..1 hash of a position and the crater centre. */
	private static double hash(int x, int z, int cx, int cz, int salt) {
		long h = Mth.getSeed(x * 31 + cx, salt, z * 17 + cz);
		return ((h >>> 11) & 0xFFFFFF) / (double) 0x1000000;
	}

	private static void carve(WorldGenLevel level, BoundingBox chunkBox, int cx, int cz, IntBinaryOperator surface) {
		BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
		int centerSurface = surface.applyAsInt(cx, cz); // read before the centre column is dug
		double[] scar = new double[SCARS];
		for (int i = 0; i < SCARS; i++) {
			scar[i] = (i + hash(i, 0, cx, cz, 91) * 0.7) * Math.PI * 2 / SCARS;
		}
		int outer = RADIUS + 4;
		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
					continue;
				}
				double raw = Math.sqrt((double) dx * dx + (double) dz * dz);
				double dist = raw + (hash(x, z, cx, cz, 1) - 0.5) * EDGE_JITTER;
				boolean onScar = onScar(dx, dz, raw, scar);
				int sy = surface.applyAsInt(x, z);
				if (dist <= RADIUS) {
					// the bowl is cut to ONE level (the centre column's surface), so it stays a bowl on a slope: higher ground is cut
					// away into a scorched wall, lower ground is built up to the floor
					double bowl = 1.0 - (dist / RADIUS) * (dist / RADIUS);
					int depth = 1 + (int) Math.round(bowl * MAX_DEPTH);
					int floorY = centerSurface - depth;
					for (int y = Math.max(sy, centerSurface) + 2; y > floorY; y--) {
						cur.set(x, y, z);
						BlockState here = level.getBlockState(cur);
						if (y <= sy || here.canBeReplaced() || here.is(net.minecraft.tags.BlockTags.FLOWERS) || here.is(net.minecraft.tags.BlockTags.LEAVES)) {
							set(level, cur, Blocks.AIR.defaultBlockState());
						}
					}
					for (int y = sy + 1; y < floorY; y++) {
						set(level, cur.set(x, y, z), Blocks.TUFF.defaultBlockState());
					}
					BlockState floor = onScar ? scarMaterial(x, z, cx, cz) : floorMaterial(x, z, cx, cz, dist);
					set(level, cur.set(x, floorY, z), floor);
					// the struck ground goes deep: one more scorched layer under the core
					if (dist < 4.0) {
						set(level, cur.set(x, floorY - 1, z), hash(x, z, cx, cz, 5) < 0.5 ? Blocks.BLACKSTONE.defaultBlockState()
								: Blocks.BASALT.defaultBlockState());
					}
					// the broken lip: a sparse ring of debris on the rim
					if (dist > RADIUS - 1.0 && hash(x, z, cx, cz, 2) < 0.3) {
						set(level, cur.set(x, floorY + 1, z), rimMaterial(x, z, cx, cz));
					}
				} else if (dist <= RADIUS + 1.6) {
					// thrown-out debris heaped just outside the rim
					if (hash(x, z, cx, cz, 3) < 0.2) {
						set(level, cur.set(x, sy + 1, z), rimMaterial(x, z, cx, cz));
					}
					scorchSurface(level, cur, x, sy, z, cx, cz, onScar);
				} else if (raw <= RADIUS + 4) {
					// the scars and scorch run out over the grass beyond the rim
					if (onScar || hash(x, z, cx, cz, 4) < 0.18 * (RADIUS + 4 - raw)) {
						scorchSurface(level, cur, x, sy, z, cx, cz, onScar);
					}
				}
			}
		}
		buildPlinth(level, chunkBox, cx, centerSurface, cz);
	}

	private static boolean onScar(int dx, int dz, double raw, double[] scar) {
		if (raw < 2.2) {
			return false;
		}
		double a = Math.atan2(dz, dx);
		for (double s : scar) {
			double d = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(a - s)));
			// a scar is about a block wide all the way out (narrower in degrees further out), with a zig-zag
			double width = Math.toDegrees(Math.atan2(0.7, raw)) + 2.0;
			double zig = Math.sin(raw * 1.3 + s * 5) * Math.toDegrees(Math.atan2(0.8, raw));
			if (Math.abs(d - zig) < width) {
				return true;
			}
		}
		return false;
	}

	private static void scorchSurface(WorldGenLevel level, BlockPos.MutableBlockPos cur, int x, int sy, int z, int cx, int cz, boolean scar) {
		cur.set(x, sy, z);
		BlockState s = level.getBlockState(cur);
		if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
				|| s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.STONE)) {
			set(level, cur, scar ? scarMaterial(x, z, cx, cz) : hash(x, z, cx, cz, 6) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState()
					: Blocks.ROOTED_DIRT.defaultBlockState());
			BlockState above = level.getBlockState(cur.set(x, sy + 1, z));
			if (!above.isAir() && above.canBeReplaced()) {
				set(level, cur, Blocks.AIR.defaultBlockState());
			}
		}
	}

	private static BlockState floorMaterial(int x, int z, int cx, int cz, double dist) {
		double r = hash(x, z, cx, cz, 7);
		if (dist < 3.2) {
			// the blackened core, still smouldering in places
			if (r < 0.06) {
				return Blocks.MAGMA_BLOCK.defaultBlockState();
			}
			if (r < 0.36) {
				return Blocks.BLACKSTONE.defaultBlockState();
			}
			if (r < 0.6) {
				return Blocks.BASALT.defaultBlockState();
			}
			if (r < 0.78) {
				return Blocks.COAL_BLOCK.defaultBlockState();
			}
			return Blocks.POLISHED_BASALT.defaultBlockState();
		}
		if (dist < 5.8) {
			if (r < 0.25) {
				return Blocks.TUFF.defaultBlockState();
			}
			if (r < 0.5) {
				return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
			}
			if (r < 0.7) {
				return Blocks.BLACKSTONE.defaultBlockState();
			}
			if (r < 0.88) {
				return Blocks.COARSE_DIRT.defaultBlockState();
			}
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (r < 0.35) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.6) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (r < 0.82) {
			return Blocks.TUFF.defaultBlockState();
		}
		return Blocks.COBBLESTONE.defaultBlockState();
	}

	private static BlockState scarMaterial(int x, int z, int cx, int cz) {
		double r = hash(x, z, cx, cz, 8);
		if (r < 0.45) {
			return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
		}
		if (r < 0.8) {
			return Blocks.BASALT.defaultBlockState();
		}
		return Blocks.BLACKSTONE.defaultBlockState();
	}

	private static BlockState rimMaterial(int x, int z, int cx, int cz) {
		double r = hash(x, z, cx, cz, 9);
		if (r < 0.35) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.6) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}
		if (r < 0.8) {
			return Blocks.TUFF.defaultBlockState();
		}
		return Blocks.GRAVEL.defaultBlockState();
	}

	/**
	 * The cracked stone plinth: a 3x3 stepped base (cracked stone brick corners, stone-brick stairs facing out) on the crater
	 * floor, a chiseled pillar block on top, and the hammer on that. A few broken bricks lie around it.
	 */
	private static void buildPlinth(WorldGenLevel level, BoundingBox chunkBox, int cx, int surfaceCenter, int cz) {
		int floorY = surfaceCenter - (MAX_DEPTH + 1);
		int baseY = floorY + 1;
		BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!chunkBox.isInside(cx + dx, baseY, cz + dz)) {
					continue;
				}
				BlockState s;
				if (dx == 0 && dz == 0) {
					s = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
				} else if (dx != 0 && dz != 0) {
					s = (dx + dz) % 4 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
				} else {
					Direction out = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
					// stairs: "facing" is the side the tall half is on, so face back toward the pillar
					s = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, out.getOpposite())
							.setValue(StairBlock.HALF, Half.BOTTOM);
				}
				set(level, cur.set(cx + dx, baseY, cz + dz), s);
				set(level, cur.set(cx + dx, floorY, cz + dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				for (int y = baseY + 1; y <= baseY + 4; y++) {
					if (!(dx == 0 && dz == 0 && y == baseY + 1)) {
						set(level, cur.set(cx + dx, y, cz + dz), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		if (chunkBox.isInside(cx, baseY + 1, cz)) {
			set(level, cur.set(cx, baseY + 1, cz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		}
		// broken bricks thrown off the plinth
		int[][] bits = { { 2, -2 }, { -3, 1 }, { 1, 3 }, { -2, -3 } };
		for (int[] b : bits) {
			int x = cx + b[0], z = cz + b[1];
			if (chunkBox.isInside(x, floorY + 1, z) && level.getBlockState(cur.set(x, floorY + 1, z)).isAir()) {
				set(level, cur, (b[0] + b[1]) % 2 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
						: Blocks.STONE_BRICK_SLAB.defaultBlockState());
			}
		}
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2);
	}

	/**
	 * Where the natural Mjolnir should rest: dead centre, on top of the plinth (or, for a pre-v0.17.7 crater, the bowl floor).
	 * Recomputed from the piece's persisted bounding box plus the world's persisted heightmap.
	 *
	 * <p><b>Never call this from inside a chunk-load callback.</b> {@code getHeight} resolves through
	 * {@code getChunk(FULL)}, which re-enters {@code ServerChunkCache}'s {@code managedBlock} and
	 * deadlocks the server thread permanently if the chunk system is already pumping tasks further up
	 * the same stack. That was a real shipped freeze; see {@code CraterAmbience}'s class javadoc for
	 * the full cycle and the deferral that fixes it.
	 */
	public BlockPos hammerSpawnPos(LevelReader level) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
		return new BlockPos(centerX, y, centerZ);
	}
}
