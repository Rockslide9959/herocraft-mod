package com.projecthero.mod.maxsteel.worldgen;

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
 * The crash crater itself: a shallow, imperfect bowl carved into whatever terrain is really there,
 * scorched at the centre, with a little exposed copper/iron in the debris. Steel itself is <em>not</em>
 * placed here -- {@link SteelCrashAmbience} spawns the entity on the first safe tick after the chunk
 * loads (spawning an entity inside {@code postProcess} is unsafe, and the entity needs to persist and
 * be deduplicated across reloads regardless).
 *
 * <p>Modelled on {@code MjolnirCraterPiece}: no stored state beyond the base-class bounding box, and
 * carving happens at {@link #postProcess} time (real terrain exists then) rather than at
 * generation-point time (only an estimate exists then).
 */
public class SteelCrashSitePiece extends StructurePiece {
	private static final int RADIUS = 6;
	private static final int MAX_DEPTH = 3;
	private static final double EDGE_JITTER = 1.7;

	public SteelCrashSitePiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.STEEL_CRASH_SITE, 0, new BoundingBox(
				centerX - RADIUS - 2, estimatedSurfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 2,
				centerX + RADIUS + 2, estimatedSurfaceY + 10, centerZ + RADIUS + 2));
	}

	public SteelCrashSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.STEEL_CRASH_SITE, tag);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		// Nothing beyond the bounding box the base class already saves.
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

				if (dist > RADIUS - 1.7 && random.nextFloat() < 0.4f) {
					cursor.set(worldX, surfaceY, worldZ);
					level.setBlock(cursor, rimMaterial(random), 2);
				}
			}
		}
	}

	private static BlockState floorMaterial(RandomSource random, double distanceFromCenter) {
		float r = random.nextFloat();
		if (distanceFromCenter < 2.0) {
			// scorched impact point
			if (r < 0.4f) {
				return Blocks.BLACKSTONE.defaultBlockState();
			}
			if (r < 0.7f) {
				return Blocks.BASALT.defaultBlockState();
			}
			return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
		}
		if (r < 0.22f) {
			return Blocks.COPPER_ORE.defaultBlockState();
		}
		if (r < 0.30f) {
			return Blocks.IRON_ORE.defaultBlockState();
		}
		if (r < 0.5f) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.72f) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (r < 0.88f) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	private static BlockState rimMaterial(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.4f) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (r < 0.7f) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (r < 0.88f) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}
		return Blocks.EXPOSED_COPPER.defaultBlockState();
	}

	/**
	 * Where Steel should hover: dead centre, ~2 blocks above the (already carved) crater floor.
	 * Recomputed fresh from the piece's own persisted bounding box + the world heightmap.
	 *
	 * <p><b>Never call this from inside a chunk-load callback</b> -- {@code getHeight} re-enters the
	 * chunk system and deadlocks the server (see {@code CraterAmbience}'s class javadoc). It is only
	 * ever called from {@link SteelCrashAmbience}'s deferred, next-tick processing.
	 */
	public BlockPos steelHoverPos(LevelReader level) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
		return new BlockPos(centerX, y + 2, centerZ);
	}
}
