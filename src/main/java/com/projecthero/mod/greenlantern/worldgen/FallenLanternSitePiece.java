package com.projecthero.mod.greenlantern.worldgen;

import com.projecthero.mod.greenlantern.block.FallenLanternPedestalBlock;
import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A damaged alien landing crater ~23 blocks across (within the 19-27 footprint target): a scorched
 * blackstone/deepslate bowl with a central raised dais holding the Dormant Power Ring pedestal
 * ({@link FallenLanternPedestalBlock}), a couple of amethyst/emerald debris accents, and a small
 * supply chest. Modelled on {@code SteelCrashSitePiece}: no stored state beyond the bounding box,
 * carving happens at {@link #postProcess} (real terrain exists then).
 */
public class FallenLanternSitePiece extends StructurePiece {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(
			Registries.LOOT_TABLE, com.projecthero.mod.ProjectHeroMod.id("chests/fallen_lantern_site"));

	private static final int RADIUS = 11; // ~22-23 block footprint
	private static final int MAX_DEPTH = 4;
	private static final double EDGE_JITTER = 2.0;

	public FallenLanternSitePiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.FALLEN_LANTERN_SITE, 0, new BoundingBox(
				centerX - RADIUS - 2, estimatedSurfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 2,
				centerX + RADIUS + 2, estimatedSurfaceY + 6, centerZ + RADIUS + 2));
	}

	public FallenLanternSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.FALLEN_LANTERN_SITE, tag);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		java.util.Map<Long, Integer> floorHeights = carve(level, chunkBox, random, centerX, centerZ);
		buildDais(level, chunkBox, random, centerX, centerZ, floorHeights);
	}

	/**
	 * @return every carved column's actual floor Y (the block {@code floorMaterial} was just placed on,
	 *         keyed by packed world X/Z), for {@link #buildDais} to reuse directly instead of
	 *         re-deriving it. Re-querying the heightmap independently (the pre-v0.11.12 approach) was
	 *         the actual cause of the pedestal -- and potentially the accent blocks/chest -- spawning
	 *         disconnected above the rest of the structure: each of those re-derivations either raced a
	 *         heightmap that had not necessarily settled from this same pass's own edits yet, or (for
	 *         the accent columns specifically) would have needed to redraw the same random jitter carve()
	 *         already consumed for that column, which is simply not reproducible after the fact. Handing
	 *         over the exact Y that was actually carved removes the whole class of bug by construction.
	 */
	private java.util.Map<Long, Integer> carve(WorldGenLevel level, BoundingBox chunkBox, RandomSource random,
			int centerX, int centerZ) {
		java.util.Map<Long, Integer> floorHeights = new java.util.HashMap<>();
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
				int floorY = surfaceY - depth;
				cursor.set(worldX, floorY, worldZ);
				level.setBlock(cursor, floorMaterial(random, dist), 2);
				if (dist > RADIUS - 2.0 && random.nextFloat() < 0.35f) {
					cursor.set(worldX, surfaceY, worldZ);
					level.setBlock(cursor, rimMaterial(random), 2);
				}
				floorHeights.put(columnKey(worldX, worldZ), floorY);
			}
		}
		return floorHeights;
	}

	private void buildDais(WorldGenLevel level, BoundingBox chunkBox, RandomSource random, int centerX, int centerZ,
			java.util.Map<Long, Integer> floorHeights) {
		if (centerX < chunkBox.minX() || centerX > chunkBox.maxX() || centerZ < chunkBox.minZ() || centerZ > chunkBox.maxZ()) {
			return;
		}
		int floorY = columnFloor(level, floorHeights, centerX, centerZ);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				cursor.set(centerX + dx, floorY, centerZ + dz);
				level.setBlock(cursor, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
			}
		}
		cursor.set(centerX, floorY + 1, centerZ);
		level.setBlock(cursor, GreenLanternBlocks.FALLEN_LANTERN_PEDESTAL.defaultBlockState()
				.setValue(FallenLanternPedestalBlock.CLAIMED, false), 2);

		int[][] accents = { {2, 2}, {-2, 1}, {1, -2}, {-2, -2} };
		BlockState[] accentBlocks = {
				Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.EMERALD_BLOCK.defaultBlockState(),
				Blocks.BUDDING_AMETHYST.defaultBlockState(), Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState() };
		for (int i = 0; i < accents.length; i++) {
			int ax = centerX + accents[i][0];
			int az = centerZ + accents[i][1];
			if (ax < chunkBox.minX() || ax > chunkBox.maxX() || az < chunkBox.minZ() || az > chunkBox.maxZ()) {
				continue;
			}
			int ay = columnFloor(level, floorHeights, ax, az);
			cursor.set(ax, ay, az);
			level.setBlock(cursor, accentBlocks[i], 2);
		}

		int chestX = centerX + 3;
		int chestZ = centerZ;
		if (chestX >= chunkBox.minX() && chestX <= chunkBox.maxX() && chestZ >= chunkBox.minZ() && chestZ <= chunkBox.maxZ()) {
			int chestY = columnFloor(level, floorHeights, chestX, chestZ) + 1;
			BlockPos chestPos = new BlockPos(chestX, chestY, chestZ);
			level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
			if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
				chest.setLootTable(LOOT, random.nextLong());
			}
		}
	}

	private static long columnKey(int worldX, int worldZ) {
		return ((long) worldX << 32) ^ (worldZ & 0xFFFFFFFFL);
	}

	/** The floor Y {@link #carve} actually dug at this column in this same pass, or (only for a column
	 *  outside the carved footprint, which should not happen for any of the fixed offsets above) a
	 *  live heightmap query as a last-resort fallback. */
	private static int columnFloor(WorldGenLevel level, java.util.Map<Long, Integer> floorHeights, int worldX, int worldZ) {
		Integer known = floorHeights.get(columnKey(worldX, worldZ));
		return known != null ? known : level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ) - 1;
	}

	private static BlockState floorMaterial(RandomSource random, double distanceFromCenter) {
		float r = random.nextFloat();
		if (distanceFromCenter < 2.5) {
			if (r < 0.4f) {
				return Blocks.BLACKSTONE.defaultBlockState();
			}
			if (r < 0.7f) {
				return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
			}
			return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
		}
		if (r < 0.15f) {
			return Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
		}
		if (r < 0.22f) {
			return Blocks.AMETHYST_BLOCK.defaultBlockState();
		}
		if (r < 0.5f) {
			return Blocks.BLACKSTONE.defaultBlockState();
		}
		if (r < 0.75f) {
			return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
		}
		return Blocks.DEEPSLATE.defaultBlockState();
	}

	private static BlockState rimMaterial(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.4f) {
			return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
		}
		if (r < 0.7f) {
			return Blocks.DEEPSLATE.defaultBlockState();
		}
		return Blocks.BLACKSTONE.defaultBlockState();
	}

	/** Recomputed from the persisted bounding box -- never call from a chunk-load callback (deadlock risk). */
	public BlockPos pedestalPos(LevelReader level) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
		return new BlockPos(centerX, y, centerZ);
	}
}
