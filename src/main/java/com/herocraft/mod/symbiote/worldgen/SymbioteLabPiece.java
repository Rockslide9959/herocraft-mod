package com.herocraft.mod.symbiote.worldgen;

import com.herocraft.mod.worldgen.ModStructurePieceTypes;
import com.herocraft.mod.worldgen.SiteType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A small half-buried containment lab: an 11x11 deepslate-brick room sunk into the terrain, its roof
 * flush with the surface with a single ladder shaft up through it. Dead centre is a 3x3x3 tinted-glass
 * containment cell -- {@link SymbioteWorldgen} spawns the Symbiote inside it. Two chests carry the
 * shared {@code research_facility} loot; sculk creeps out from under the cell.
 *
 * <p>Everything is a bounded loop clipped to the current chunk box -- no scanning, no recursion, same
 * as {@code ResearchSitePiece}.
 */
public class SymbioteLabPiece extends StructurePiece implements SymbioteSpawnPiece {
	private static final int HALF = 5;         // 11x11 footprint
	private static final int FLOOR_BELOW = 6;  // floor sits this far below the surface
	private static final int HEIGHT = 6;       // floor..ceiling interior height

	/** The interior floor Y. Estimated at construction, made exact in {@link #postProcess}, persisted. */
	private int floorY;

	public SymbioteLabPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.SYMBIOTE_LAB, 0, new BoundingBox(
				centerX - HALF - 1, estimatedSurfaceY - FLOOR_BELOW - 2, centerZ - HALF - 1,
				centerX + HALF + 1, estimatedSurfaceY + 4, centerZ + HALF + 1));
		this.floorY = estimatedSurfaceY - FLOOR_BELOW;
	}

	public SymbioteLabPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.SYMBIOTE_LAB, tag);
		this.floorY = tag.getInt("FloorY");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("FloorY", floorY);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int cx = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int cz = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		int surf = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
		this.floorY = surf - FLOOR_BELOW;
		int ceilY = floorY + HEIGHT;

		BlockState brick = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
		BlockState glass = Blocks.TINTED_GLASS.defaultBlockState();
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();

		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int wx = cx + dx;
				int wz = cz + dz;
				if (wx < chunkBox.minX() || wx > chunkBox.maxX() || wz < chunkBox.minZ() || wz > chunkBox.maxZ()) {
					continue;
				}
				boolean wall = Math.abs(dx) == HALF || Math.abs(dz) == HALF;

				for (int y = floorY; y <= ceilY; y++) {
					c.set(wx, y, wz);
					if (y == floorY) {
						level.setBlock(c, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
					} else if (y == ceilY || wall) {
						level.setBlock(c, random.nextFloat() < 0.12f ? cracked : brick, 2);
					} else {
						level.setBlock(c, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				// bury the sides: fill from the roof up to the real surface with stone so there is no
				// tell-tale box poking out of a hill
				for (int y = ceilY + 1; y <= surf; y++) {
					c.set(wx, y, wz);
					level.setBlock(c, Blocks.STONE.defaultBlockState(), 2);
				}
			}
		}

		// dim lighting
		for (int[] o : new int[][] { { -3, -3 }, { 3, -3 }, { -3, 3 }, { 3, 3 } }) {
			place(level, chunkBox, c, cx + o[0], ceilY - 1, cz + o[1], Blocks.REDSTONE_LAMP.defaultBlockState());
			place(level, chunkBox, c, cx + o[0], ceilY, cz + o[1], Blocks.REDSTONE_BLOCK.defaultBlockState());
		}

		// the containment cell: 3x3 tinted-glass shell, floor..floor+3, hollow inside
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = floorY + 1; y <= floorY + 4; y++) {
					boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || y == floorY + 4;
					place(level, chunkBox, c, cx + dx, y, cz + dz,
							shell ? glass : Blocks.AIR.defaultBlockState());
				}
				// sculk seeping out under the cell
				if (random.nextFloat() < 0.6f) {
					place(level, chunkBox, c, cx + dx, floorY, cz + dz, Blocks.SCULK.defaultBlockState());
				}
			}
		}
		place(level, chunkBox, c, cx, floorY + 1, cz, Blocks.LODESTONE.defaultBlockState()); // the pedestal

		// ladder shaft up through the roof to the surface
		for (int y = floorY + 1; y <= surf; y++) {
			place(level, chunkBox, c, cx + HALF - 1, y, cz, Blocks.AIR.defaultBlockState());
			BlockState ladder = Blocks.LADDER.defaultBlockState()
					.setValue(LadderBlock.FACING, Direction.WEST);
			place(level, chunkBox, c, cx + HALF - 1, y, cz, ladder);
		}
		place(level, chunkBox, c, cx + HALF - 1, surf + 1, cz, Blocks.AIR.defaultBlockState());

		// chests
		chest(level, chunkBox, random, c, cx - 3, floorY + 1, cz + 2);
		if (random.nextBoolean()) {
			chest(level, chunkBox, random, c, cx + 2, floorY + 1, cz - 3);
		}

		// a little clutter
		place(level, chunkBox, c, cx - 2, floorY + 1, cz - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		place(level, chunkBox, c, cx + 3, floorY + 1, cz + 3, Blocks.IRON_BARS.defaultBlockState());
		place(level, chunkBox, c, cx + 3, floorY + 2, cz + 3, Blocks.IRON_BARS.defaultBlockState());
	}

	private void chest(WorldGenLevel level, BoundingBox box, RandomSource random, BlockPos.MutableBlockPos c,
			int x, int y, int z) {
		if (!inChunk(box, x, z)) {
			return;
		}
		c.set(x, y, z);
		BlockState chest = Blocks.CHEST.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
		level.setBlock(c, chest, 2);
		if (level.getBlockEntity(c) instanceof ChestBlockEntity be) {
			be.setLootTable(SiteType.RESEARCH_FACILITY.lootTable(), random.nextLong());
		}
	}

	private void place(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int x, int y, int z,
			BlockState state) {
		if (!inChunk(box, x, z)) {
			return;
		}
		c.set(x, y, z);
		level.setBlock(c, state, 2);
	}

	private static boolean inChunk(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}

	@Override
	public BlockPos symbiotePos(LevelReader level) {
		int cx = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int cz = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		return new BlockPos(cx, floorY + 2, cz);
	}
}
