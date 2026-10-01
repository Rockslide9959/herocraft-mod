package com.projecthero.mod.kryptonian.worldgen;

import com.projecthero.mod.kryptonian.item.KryptonianItems;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
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
 * v0.14.13: the old Kryptonite Meteor impact, carved at world generation: a scorched bowl (blackstone, basalt, magma,
 * coarse dirt) cut into the real terrain, the Meteor Core sunk into the floor at the centre and kryptonite ore crusted
 * round it, a few crystals jutting up. Like {@code MjolnirCraterPiece} it stores nothing but its bounding box -- the
 * centre is the box centre and every column re-reads the real surface height.
 */
public class KryptoniteCraterPiece extends StructurePiece {
	private static final int RADIUS = 6;
	private static final int MAX_DEPTH = 4;
	private static final double EDGE_JITTER = 1.4;

	public KryptoniteCraterPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.KRYPTONITE_CRATER, 0, new BoundingBox(
				centerX - RADIUS - 2, estimatedSurfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 2,
				centerX + RADIUS + 2, estimatedSurfaceY + 10, centerZ + RADIUS + 2));
	}

	public KryptoniteCraterPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.KRYPTONITE_CRATER, tag);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		// nothing beyond the bounding box
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int cx = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int cz = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		// the core height comes from the WORLDGEN heightmap at the centre, which every chunk of the crater reads the same
		int coreY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz) - 2 - MAX_DEPTH;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int outer = RADIUS + 1;
		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
					continue;
				}
				double dist = Math.sqrt((double) dx * dx + (double) dz * dz) + (random.nextDouble() - 0.5) * EDGE_JITTER;
				if (dist > RADIUS) {
					continue;
				}
				int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
				double bowl = 1.0 - (dist / RADIUS) * (dist / RADIUS);
				int depth = 1 + (int) Math.round(bowl * MAX_DEPTH);
				for (int y = surfaceY + 3; y > surfaceY - depth; y--) {
					cursor.set(x, y, z);
					if (!level.getBlockState(cursor).hasBlockEntity()) {
						level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				cursor.set(x, surfaceY - depth, z);
				level.setBlock(cursor, scorch(random), 2);
				if (dist > RADIUS - 1.5 && random.nextFloat() < 0.4f) {
					cursor.set(x, surfaceY, z);
					level.setBlock(cursor, scorch(random), 2);
				}
			}
		}
		// the heart: the Meteor Core with kryptonite ore crusted round it (each block placed by the chunk that holds it)
		BlockPos core = new BlockPos(cx, coreY, cz);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos p = core.offset(dx, dy, dz);
					if (!chunkBox.isInside(p)) {
						continue;
					}
					int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
					if (manhattan == 0) {
						level.setBlock(p, KryptonianItems.METEOR_CORE.defaultBlockState(), 2);
					} else if (manhattan <= 2 && dy <= 0 && random.nextFloat() < 0.7f) {
						level.setBlock(p, KryptonianItems.KRYPTONITE_ORE.defaultBlockState(), 2);
					} else if (dy == 1 && manhattan <= 3 && random.nextFloat() < 0.3f && level.getBlockState(p).isAir()) {
						level.setBlock(p, KryptonianItems.KRYPTONITE_ORE.defaultBlockState(), 2); // a crystal jutting up
					}
				}
			}
		}
	}

	private static BlockState scorch(RandomSource random) {
		float r = random.nextFloat();
		return r < 0.12f ? Blocks.MAGMA_BLOCK.defaultBlockState()
				: r < 0.5f ? Blocks.BLACKSTONE.defaultBlockState()
				: r < 0.78f ? Blocks.BASALT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
	}
}
