package com.herocraft.mod.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
 * The impact crater itself: a roughly circular, deliberately imperfect bowl carved into whatever
 * terrain is really there, with Mjolnir resting at the bottom.
 *
 * <h2>No stored state -- the piece needs none</h2>
 * The only thing this piece remembers across a save/load is what {@link StructurePiece} already
 * persists for free: its {@link #boundingBox}. The crater's center is the box's horizontal center,
 * and {@link #hammerSpawnPos} rereads the world's own (also persisted) heightmap at that column --
 * so there is nothing bespoke to serialize, and nothing that can desync from what actually got
 * carved.
 *
 * <h2>Carving happens at postProcess time, not at findGenerationPoint time</h2>
 * {@link MjolnirCraterStructure#findGenerationPoint} only has a pre-generation height *estimate*
 * (real terrain doesn't exist yet at structure-placement time). This piece re-samples the REAL
 * per-column surface height here, once actual terrain/carvers have run -- which is also what lets it
 * follow real, uneven ground instead of assuming the estimate was exact.
 */
public class MjolnirCraterPiece extends StructurePiece {
	/** ~14 blocks across at the rim before jitter -- within THOR_DESIGN's 9-15 block target. */
	private static final int RADIUS = 7;
	/** Deepest point of the bowl, in blocks; the rim tapers to ~1 block deep. */
	private static final int MAX_DEPTH = 3;
	/** Random per-column wobble on the effective radius, so the rim isn't a perfect circle. */
	private static final double EDGE_JITTER = 1.6;

	public MjolnirCraterPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.MJOLNIR_CRATER, 0, new BoundingBox(
				centerX - RADIUS - 2, estimatedSurfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 2,
				centerX + RADIUS + 2, estimatedSurfaceY + 10, centerZ + RADIUS + 2));
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
		carve(level, chunkBox, random, centerX, centerZ);
	}

	private void carve(WorldGenLevel level, BoundingBox chunkBox, RandomSource random, int centerX, int centerZ) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int outer = RADIUS + 1;

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

				for (int y = surfaceY; y > surfaceY - depth; y--) {
					cursor.set(worldX, y, worldZ);
					level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
				}

				cursor.set(worldX, surfaceY - depth, worldZ);
				level.setBlock(cursor, floorMaterial(random, dist), 2);

				// A scorched lip right at the rim, sparse enough that the surrounding biome still
				// reads clearly rather than being swallowed by a hard-edged circle of debris.
				if (dist > RADIUS - 1.6 && random.nextFloat() < 0.45f) {
					cursor.set(worldX, surfaceY, worldZ);
					level.setBlock(cursor, rimMaterial(random), 2);
				}
			}
		}
	}

	private static BlockState floorMaterial(RandomSource random, double distanceFromCenter) {
		float r = random.nextFloat();
		if (distanceFromCenter < 2.0) {
			// Directly under Mjolnir: the most obviously "struck" ground.
			if (r < 0.35f) {
				return Blocks.BLACKSTONE.defaultBlockState();
			}
			if (r < 0.7f) {
				return Blocks.COBBLESTONE.defaultBlockState();
			}
			return Blocks.STONE.defaultBlockState();
		}
		if (r < 0.35f) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.6f) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (r < 0.85f) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}
		return Blocks.ANDESITE.defaultBlockState();
	}

	private static BlockState rimMaterial(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.4f) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.75f) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		return Blocks.COBBLESTONE.defaultBlockState();
	}

	/**
	 * Where the natural Mjolnir should rest: dead center, sitting on the real (already carved) crater
	 * floor. Recomputed fresh from the piece's own persisted bounding box plus the world's own
	 * persisted heightmap every time it's needed -- see the class javadoc for why nothing about this
	 * needs to be stored separately.
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
