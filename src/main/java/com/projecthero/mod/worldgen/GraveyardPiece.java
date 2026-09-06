package com.projecthero.mod.worldgen;

import com.projecthero.mod.grave.CursedGraveBlock;
import com.projecthero.mod.grave.item.GraveItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A graveyard carved onto real terrain: a low wall, gravel paths, rows of gravestones on raised
 * mounds, dead bushes and cobwebs, scattered bone blocks and skulls, soul lanterns, and a stone crypt
 * at the centre housing the {@link CursedGraveBlock} and a loot chest.
 *
 * <h2>Procedural rather than a template</h2>
 * Same reasoning as {@link ResearchSitePiece} and {@link MjolnirCraterPiece}: the structure has to sit
 * on uneven terrain that does not exist when placement is decided, and there is no structure-block
 * session available in this project to author an NBT template with. Every decoration reads the real
 * surface height of its own column, so nothing floats and nothing is buried.
 *
 * <h2>Bounded work</h2>
 * The whole piece is two fixed nested loops over a {@value #HALF}-radius square plus a fixed crypt
 * build, each iteration clipped to the chunk currently being generated. There is no recursion, no
 * search, and no scanning of neighbouring structures.
 */
public class GraveyardPiece extends StructurePiece {
	private static final int HALF = 9; // 19x19 footprint
	private static final int CRYPT_HALF = 3; // 7x7 crypt
	private static final int CRYPT_HEIGHT = 4;

	public static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(Registries.LOOT_TABLE,
			ResourceLocation.fromNamespaceAndPath(com.projecthero.mod.ProjectHeroMod.MOD_ID, "chests/graveyard"));

	public GraveyardPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.GRAVEYARD, 0, new BoundingBox(
				centerX - HALF - 1, estimatedSurfaceY - 8, centerZ - HALF - 1,
				centerX + HALF + 1, estimatedSurfaceY + 12, centerZ + HALF + 1));
	}

	public GraveyardPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.GRAVEYARD, tag);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		int centerY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int wx = centerX + dx;
				int wz = centerZ + dz;
				if (!inChunk(chunkBox, wx, wz)) {
					continue;
				}
				boolean inCrypt = Math.abs(dx) <= CRYPT_HALF && Math.abs(dz) <= CRYPT_HALF;
				if (inCrypt) {
					continue; // the crypt lays its own ground
				}
				int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz);
				decorateColumn(level, cursor, random, dx, dz, wx, wz, surface);
			}
		}

		buildCrypt(level, chunkBox, random, centerX, centerY, centerZ);
	}

	/** One column of the graveyard grounds: ground cover, then at most one decoration on top of it. */
	private void decorateColumn(WorldGenLevel level, BlockPos.MutableBlockPos cursor, RandomSource random,
			int dx, int dz, int wx, int wz, int surface) {
		// Clear a little headroom so grass and trees do not grow through the graveyard.
		for (int y = surface; y < surface + 3; y++) {
			cursor.set(wx, y, wz);
			level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
		}

		boolean onWall = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
		boolean onPath = dx == 0 || dz == 0;

		cursor.set(wx, surface - 1, wz);
		if (onPath) {
			level.setBlock(cursor, Blocks.GRAVEL.defaultBlockState(), 2);
		} else {
			level.setBlock(cursor, random.nextFloat() < 0.65f
					? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PODZOL.defaultBlockState(), 2);
		}

		if (onWall) {
			// A broken perimeter wall -- gaps are deliberate, so it reads as long abandoned.
			if (random.nextFloat() < 0.7f) {
				cursor.set(wx, surface, wz);
				level.setBlock(cursor, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), 2);
			}
			return;
		}
		if (onPath) {
			// Keep the paths walkable; only the occasional lantern post beside them.
			if (Math.abs(dx) + Math.abs(dz) == HALF - 1 && random.nextFloat() < 0.5f) {
				cursor.set(wx, surface, wz);
				level.setBlock(cursor, Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
				cursor.set(wx, surface + 1, wz);
				level.setBlock(cursor, Blocks.SOUL_LANTERN.defaultBlockState(), 2);
			}
			return;
		}

		// Gravestones sit in rows: every other column, offset, so they read as plots rather than noise.
		boolean gravePlot = (Math.floorMod(dx, 3) == 1) && (Math.floorMod(dz, 3) == 1);
		if (gravePlot && random.nextFloat() < 0.8f) {
			placeGravestone(level, cursor, random, wx, surface, wz);
			return;
		}

		float roll = random.nextFloat();
		cursor.set(wx, surface, wz);
		if (roll < 0.06f) {
			level.setBlock(cursor, Blocks.DEAD_BUSH.defaultBlockState(), 2);
		} else if (roll < 0.10f) {
			level.setBlock(cursor, Blocks.COBWEB.defaultBlockState(), 2);
		} else if (roll < 0.13f) {
			level.setBlock(cursor, Blocks.BONE_BLOCK.defaultBlockState(), 2);
		} else if (roll < 0.15f) {
			level.setBlock(cursor, Blocks.SKELETON_SKULL.defaultBlockState()
					.setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)), 2);
		} else if (roll < 0.17f) {
			level.setBlock(cursor, Blocks.SOUL_FIRE.defaultBlockState(), 2);
			cursor.set(wx, surface - 1, wz);
			level.setBlock(cursor, Blocks.SOUL_SOIL.defaultBlockState(), 2);
		}
	}

	/** A raised mound with a headstone, and sometimes a skull or cobweb on it. */
	private void placeGravestone(WorldGenLevel level, BlockPos.MutableBlockPos cursor, RandomSource random,
			int wx, int surface, int wz) {
		cursor.set(wx, surface, wz);
		level.setBlock(cursor, random.nextBoolean()
				? Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState()
				: Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
		cursor.set(wx, surface + 1, wz);
		if (random.nextFloat() < 0.4f) {
			level.setBlock(cursor, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);
		} else if (random.nextFloat() < 0.25f) {
			level.setBlock(cursor, Blocks.COBWEB.defaultBlockState(), 2);
		}
	}

	/**
	 * The crypt: a flat stone-brick chamber with a doorway, a soul-lantern lit interior, the Cursed
	 * Grave on a plinth at its centre and a loot chest beside it. Its floor is laid at one fixed height
	 * with a foundation carried down to the terrain, which is what stops it hanging in the air on a
	 * slope.
	 */
	private void buildCrypt(WorldGenLevel level, BoundingBox chunkBox, RandomSource random,
			int centerX, int centerY, int centerZ) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();

		for (int dx = -CRYPT_HALF; dx <= CRYPT_HALF; dx++) {
			for (int dz = -CRYPT_HALF; dz <= CRYPT_HALF; dz++) {
				int wx = centerX + dx;
				int wz = centerZ + dz;
				if (!inChunk(chunkBox, wx, wz)) {
					continue;
				}
				boolean wall = Math.abs(dx) == CRYPT_HALF || Math.abs(dz) == CRYPT_HALF;
				// Doorway: a two-high gap in the middle of the north wall.
				boolean doorway = dz == -CRYPT_HALF && Math.abs(dx) <= 1;

				// Foundation down to the terrain so nothing floats over a slope.
				int terrain = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz);
				for (int y = Math.min(terrain, centerY) - 1; y < centerY; y++) {
					cursor.set(wx, y, wz);
					level.setBlock(cursor, brick, 2);
				}

				cursor.set(wx, centerY - 1, wz);
				level.setBlock(cursor, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);

				for (int h = 0; h < CRYPT_HEIGHT; h++) {
					cursor.set(wx, centerY + h, wz);
					if (h == CRYPT_HEIGHT - 1) {
						level.setBlock(cursor, random.nextFloat() < 0.25f ? cracked : brick, 2);
					} else if (wall && !(doorway && h < 2)) {
						level.setBlock(cursor, random.nextFloat() < 0.35f ? mossy : brick, 2);
					} else {
						level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}

		// Interior fittings.
		if (inChunk(chunkBox, centerX, centerZ)) {
			cursor.set(centerX, centerY - 1, centerZ);
			level.setBlock(cursor, Blocks.SOUL_SOIL.defaultBlockState(), 2);
			cursor.set(centerX, centerY, centerZ);
			level.setBlock(cursor, GraveItems.CURSED_GRAVE.defaultBlockState()
					.setValue(CursedGraveBlock.FACING, Direction.NORTH), 2);
		}
		lantern(level, chunkBox, cursor, centerX - 2, centerY + 2, centerZ + 2);
		lantern(level, chunkBox, cursor, centerX + 2, centerY + 2, centerZ + 2);
		cobweb(level, chunkBox, cursor, random, centerX - 2, centerY + 2, centerZ - 2);
		cobweb(level, chunkBox, cursor, random, centerX + 2, centerY + 2, centerZ - 2);

		if (inChunk(chunkBox, centerX + 2, centerZ)) {
			BlockPos chestPos = new BlockPos(centerX + 2, centerY, centerZ);
			level.setBlock(chestPos, Blocks.CHEST.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
			if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
				chest.setLootTable(LOOT_TABLE, random.nextLong());
			}
		}
	}

	private void lantern(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos cursor,
			int x, int y, int z) {
		if (!inChunk(chunkBox, x, z)) {
			return;
		}
		cursor.set(x, y, z);
		level.setBlock(cursor, Blocks.SOUL_LANTERN.defaultBlockState()
				.setValue(BlockStateProperties.HANGING, true), 2);
	}

	private void cobweb(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos cursor,
			RandomSource random, int x, int y, int z) {
		if (!inChunk(chunkBox, x, z) || random.nextFloat() < 0.35f) {
			return;
		}
		cursor.set(x, y, z);
		level.setBlock(cursor, Blocks.COBWEB.defaultBlockState(), 2);
	}

	private static boolean inChunk(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}
}
