package com.projecthero.mod.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A small ruined research outpost carved onto real terrain: a floor pad, a broken perimeter wall,
 * a couple of pillars, 1-2 loot chests, some rubble, and (for most site types) the site's laboratory
 * device at the centre. Everything is a bounded loop clipped to the current chunk box -- no scanning,
 * no recursion.
 */
public class ResearchSitePiece extends StructurePiece {
	private static final int HALF = 5; // 11x11 footprint

	private SiteType siteType;

	public ResearchSitePiece(int centerX, int estimatedSurfaceY, int centerZ, SiteType siteType) {
		super(ModStructurePieceTypes.RESEARCH_SITE, 0, new BoundingBox(
				centerX - HALF - 1, estimatedSurfaceY - 4, centerZ - HALF - 1,
				centerX + HALF + 1, estimatedSurfaceY + 8, centerZ + HALF + 1));
		this.siteType = siteType;
	}

	public ResearchSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.RESEARCH_SITE, tag);
		this.siteType = SiteType.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("site_type"))
				.result().orElse(SiteType.RESEARCH_FACILITY);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		SiteType.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, siteType).result()
				.ifPresent(t -> tag.put("site_type", t));
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		BlockState floor = siteType.primary().defaultBlockState();
		BlockState wall = siteType.accent().defaultBlockState();
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();

		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int wx = centerX + dx;
				int wz = centerZ + dz;
				if (wx < chunkBox.minX() || wx > chunkBox.maxX() || wz < chunkBox.minZ() || wz > chunkBox.maxZ()) {
					continue;
				}
				int surf = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz);
				// clear a couple of blocks of headroom
				for (int y = surf; y < surf + 4; y++) {
					c.set(wx, y, wz);
					level.setBlock(c, Blocks.AIR.defaultBlockState(), 2);
				}
				// floor
				c.set(wx, surf - 1, wz);
				level.setBlock(c, floor, 2);

				boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
				if (edge && random.nextFloat() < 0.65f) {
					int h = 1 + random.nextInt(2);
					for (int y = 0; y < h; y++) {
						c.set(wx, surf + y, wz);
						level.setBlock(c, wall, 2);
					}
				} else if ((Math.abs(dx) == HALF - 2 && Math.abs(dz) == HALF - 2) && random.nextFloat() < 0.7f) {
					for (int y = 0; y < 3; y++) {
						c.set(wx, surf + y, wz);
						level.setBlock(c, wall, 2);
					}
				} else if (!edge && random.nextFloat() < 0.06f) {
					c.set(wx, surf, wz);
					level.setBlock(c, random.nextBoolean() ? Blocks.COBWEB.defaultBlockState()
							: siteType.primary().defaultBlockState(), 2);
				}
			}
		}

		// centre device + surrounding pad
		int surf = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
		if (inChunk(chunkBox, centerX, centerZ) && siteType.device() != null) {
			c.set(centerX, surf, centerZ);
			level.setBlock(c, siteType.device().defaultBlockState(), 2);
		}
		if (siteType == SiteType.METEOR_IMPACT && inChunk(chunkBox, centerX, centerZ)) {
			c.set(centerX, surf, centerZ);
			level.setBlock(c, Blocks.AMETHYST_CLUSTER.defaultBlockState(), 2);
			c.set(centerX, surf - 1, centerZ);
			level.setBlock(c, Blocks.BUDDING_AMETHYST.defaultBlockState(), 2);
		}

		// chests
		placeChest(level, chunkBox, random, centerX + 2, centerZ + 2);
		if (random.nextBoolean()) {
			placeChest(level, chunkBox, random, centerX - 3, centerZ - 1);
		}
	}

	private void placeChest(WorldGenLevel level, BoundingBox chunkBox, RandomSource random, int x, int z) {
		if (!inChunk(chunkBox, x, z)) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
		BlockPos chestPos = new BlockPos(x, y, z);
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
				Direction.Plane.HORIZONTAL.getRandomDirection(random));
		level.setBlock(chestPos, chest, 2);
		if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity be) {
			be.setLootTable(siteType.lootTable(), random.nextLong());
		}
	}

	private static boolean inChunk(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}
}
