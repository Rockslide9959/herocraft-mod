package com.projecthero.mod.symbiote.worldgen;

import com.projecthero.mod.worldgen.ModStructurePieceTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The impact crater: a shallow, imperfect bowl carved into whatever terrain is really there, scorched
 * black at the centre with a knot of obsidian / crying obsidian where the alien rock struck, and a
 * scatter of blackstone / basalt / sculk in the debris. The Symbiote itself is spawned by
 * {@link SymbioteWorldgen}, not here.
 *
 * <p>Modelled on {@code SteelCrashSitePiece}: no stored state beyond the base-class bounding box;
 * carving at {@link #postProcess} time (real terrain exists then).
 */
public class SymbioteMeteorPiece extends StructurePiece implements SymbioteSpawnPiece {
	private static final int RADIUS = 7;
	private static final int MAX_DEPTH = 4;
	private static final double EDGE_JITTER = 1.9;

	public SymbioteMeteorPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.SYMBIOTE_METEOR, 0, new BoundingBox(
				centerX - RADIUS - 2, estimatedSurfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 2,
				centerX + RADIUS + 2, estimatedSurfaceY + 10, centerZ + RADIUS + 2));
	}

	public SymbioteMeteorPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.SYMBIOTE_METEOR, tag);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int outer = RADIUS + 1;
		// Captured from the main loop's own pass over the exact centre column -- see below for why the
		// core placement must reuse this rather than asking the heightmap again afterwards.
		int centerFloorY = Integer.MIN_VALUE;

		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				int worldX = centerX + dx;
				int worldZ = centerZ + dz;
				if (worldX < chunkBox.minX() || worldX > chunkBox.maxX()
						|| worldZ < chunkBox.minZ() || worldZ > chunkBox.maxZ()) {
					continue;
				}
				double jitter = (random.nextDouble() - 0.5) * EDGE_JITTER;
				double dist = Math.sqrt((double) dx * dx + (double) dz * dz) + jitter;
				if (dist > RADIUS) {
					continue;
				}
				int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ) - 1;
				double bowl = 1.0 - (dist / RADIUS) * (dist / RADIUS);
				int depth = 1 + (int) Math.round(bowl * MAX_DEPTH);
				int floorY = surfaceY - depth;

				for (int y = surfaceY; y > floorY; y--) {
					cursor.set(worldX, y, worldZ);
					level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
				}
				cursor.set(worldX, floorY, worldZ);
				level.setBlock(cursor, floorMaterial(random, dist), 2);

				if (dist > RADIUS - 1.8 && random.nextFloat() < 0.4f) {
					cursor.set(worldX, surfaceY, worldZ);
					level.setBlock(cursor, rimMaterial(random), 2);
				}

				if (dx == 0 && dz == 0) {
					centerFloorY = floorY;
				}
			}
		}

		// The meteor core: a small obsidian knot at the very centre of the floor.
		//
		// This MUST reuse centerFloorY captured above rather than re-querying
		// level.getHeight(WORLD_SURFACE_WG, centerX, centerZ) here -- a second, independent query after
		// the carve (on possibly bumpy natural terrain, where the 8 neighbouring core columns can each
		// have sampled a slightly different pre-carve surfaceY than the centre one) is exactly what
		// made the core -- and the Symbiote spawned on top of it -- generate floating above the actual
		// carved crater floor instead of sitting in it. Reusing the one live value the loop itself just
		// carved the centre column to keeps the platform consistent with what is really there.
		if (centerFloorY != Integer.MIN_VALUE) {
			int floorY = centerFloorY;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					cursor.set(centerX + dx, floorY, centerZ + dz);
					level.setBlock(cursor, (dx == 0 && dz == 0)
							? Blocks.CRYING_OBSIDIAN.defaultBlockState()
							: Blocks.OBSIDIAN.defaultBlockState(), 2);
				}
			}
			cursor.set(centerX, floorY + 1, centerZ);
			level.setBlock(cursor, Blocks.SCULK_SHRIEKER.defaultBlockState(), 2);
		}
	}

	private static BlockState floorMaterial(RandomSource random, double distanceFromCenter) {
		float r = random.nextFloat();
		if (distanceFromCenter < 2.5) {
			if (r < 0.45f) {
				return Blocks.BLACKSTONE.defaultBlockState();
			}
			if (r < 0.7f) {
				return Blocks.BASALT.defaultBlockState();
			}
			if (r < 0.85f) {
				return Blocks.SCULK.defaultBlockState();
			}
			return Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		if (r < 0.3f) {
			return Blocks.BLACKSTONE.defaultBlockState();
		}
		if (r < 0.45f) {
			return Blocks.BASALT.defaultBlockState();
		}
		if (r < 0.6f) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.8f) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
	}

	private static BlockState rimMaterial(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.45f) {
			return Blocks.BLACKSTONE.defaultBlockState();
		}
		if (r < 0.7f) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.88f) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		return Blocks.BASALT.defaultBlockState();
	}

	@Override
	public BlockPos symbiotePos(LevelReader level) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
		return new BlockPos(centerX, y + 1, centerZ);
	}
}
