package com.projecthero.mod.hulk.worldgen;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hulk.item.HulkItems;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The ruined Gamma Lab: a 15x15 concrete slab levelled into the terrain, its walls blown out to jagged stumps of
 * concrete, iron bars and green glass. Dead centre, on a raised iron pedestal ringed by four lime-glass pillars, sits
 * the still-glowing {@code gamma_reactor}; the floor around it is scorched lime. One chest (the {@code gamma_lab}
 * loot table -- always a Gamma Serum) sits against the back wall, a second (research loot) sometimes by the door.
 *
 * <p>Everything is a bounded loop clipped to the current chunk box, deterministic from the piece's own seed so the
 * ruin looks the same across chunk borders -- same discipline as {@code SymbioteLabPiece}.
 */
public class GammaLabPiece extends StructurePiece {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/gamma_lab"));
	private static final ResourceKey<LootTable> RESEARCH = ResourceKey.create(Registries.LOOT_TABLE,
			ProjectHeroMod.id("chests/research_facility"));

	private static final int HALF = 7;          // 15x15 footprint
	private static final int WALL_MAX = 5;      // the tallest a surviving wall stump gets

	/** The floor Y, fixed at construction and persisted. */
	private final int floorY;
	private final long seed;

	public GammaLabPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.GAMMA_LAB, 0, new BoundingBox(centerX - HALF - 1, estimatedSurfaceY - 6, centerZ - HALF - 1,
				centerX + HALF + 1, estimatedSurfaceY + 10, centerZ + HALF + 1));
		// the generator's own terrain height (not the live heightmap, which the ruin itself changes) keeps the floor
		// at the same level in every chunk the ruin crosses
		this.floorY = estimatedSurfaceY - 1;
		this.seed = ((long) centerX * 341873128712L) ^ ((long) centerZ * 132897987541L);
	}

	public GammaLabPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.GAMMA_LAB, tag);
		this.floorY = tag.getInt("FloorY");
		this.seed = tag.getLong("Seed");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("FloorY", floorY);
		tag.putLong("Seed", seed);
	}

	/** A stable 0..1 value per (x, z, salt) -- the ruin must look the same in every chunk it crosses. */
	private double noise(int x, int z, int salt) {
		long h = seed ^ (x * 73856093L) ^ (z * 19349663L) ^ (salt * 83492791L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int cx = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int cz = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();

		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int wx = cx + dx;
				int wz = cz + dz;
				if (!inChunk(chunkBox, wx, wz)) {
					continue;
				}
				// a solid footing down to the ground, so no corner floats over a slope
				for (int y = floorY - 1; y >= floorY - 5; y--) {
					c.set(wx, y, wz);
					if (!level.getBlockState(c).isAir() && level.getFluidState(c).isEmpty()) {
						break;
					}
					level.setBlock(c, Blocks.STONE.defaultBlockState(), 2);
				}
				// clear the air above the slab
				for (int y = floorY + 1; y <= floorY + WALL_MAX + 3; y++) {
					c.set(wx, y, wz);
					level.setBlock(c, Blocks.AIR.defaultBlockState(), 2);
				}
				// the floor: concrete, cracked and scorched lime nearer the reactor
				double dist = Math.sqrt(dx * dx + dz * dz);
				double n = noise(wx, wz, 1);
				BlockState floor;
				if (dist < 3.2 && n < 0.7) {
					floor = n < 0.35 ? Blocks.LIME_CONCRETE.defaultBlockState() : Blocks.LIME_CONCRETE_POWDER.defaultBlockState();
				} else if (n < 0.12) {
					floor = Blocks.GRAVEL.defaultBlockState();
				} else if (n < 0.24) {
					floor = Blocks.COBBLESTONE.defaultBlockState();
				} else if (n < 0.30) {
					floor = Blocks.MOSS_BLOCK.defaultBlockState();
				} else {
					floor = (Math.abs(dx) == 3 || Math.abs(dz) == 3) && n > 0.8 ? Blocks.GREEN_CONCRETE.defaultBlockState()
							: Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
				}
				c.set(wx, floorY, wz);
				level.setBlock(c, floor, 2);

				// the walls: blown-out stumps of uneven height, with a doorway on the south side
				boolean wall = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
				boolean door = dz == HALF && Math.abs(dx) <= 1;
				if (wall && !door) {
					int height = (int) Math.floor(noise(wx, wz, 2) * (WALL_MAX + 1)) - 1;
					for (int y = 1; y <= height; y++) {
						c.set(wx, floorY + y, wz);
						double m = noise(wx, wz, 10 + y);
						BlockState block = m < 0.15 ? Blocks.IRON_BARS.defaultBlockState()
								: m < 0.3 ? Blocks.LIME_STAINED_GLASS_PANE.defaultBlockState()
								: m < 0.45 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
								: Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
						level.setBlock(c, block, 2);
					}
				} else if (!wall && noise(wx, wz, 3) < 0.05 && dist > 3.5) {
					// rubble
					c.set(wx, floorY + 1, wz);
					level.setBlock(c, noise(wx, wz, 4) < 0.5 ? Blocks.COBBLESTONE.defaultBlockState()
							: Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
				}
			}
		}

		// the reactor: an iron pedestal, the core on top, four lime-glass pillars capped with sea lanterns
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				place(level, chunkBox, c, cx + dx, floorY + 1, cz + dz, Math.abs(dx) + Math.abs(dz) == 2
						? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
			}
		}
		place(level, chunkBox, c, cx, floorY + 2, cz, HulkItems.GAMMA_REACTOR.defaultBlockState());
		for (int[] o : new int[][] { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } }) {
			int h = 2 + (int) (noise(cx + o[0], cz + o[1], 5) * 3);
			for (int y = 1; y <= h; y++) {
				place(level, chunkBox, c, cx + o[0], floorY + y, cz + o[1], Blocks.LIME_STAINED_GLASS.defaultBlockState());
			}
			place(level, chunkBox, c, cx + o[0], floorY + h + 1, cz + o[1], Blocks.SEA_LANTERN.defaultBlockState());
		}
		// cables to the pedestal
		for (int d = 3; d <= 5; d++) {
			place(level, chunkBox, c, cx, floorY + 1, cz - d, Blocks.CHAIN.defaultBlockState()
					.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
		}

		// chests: the serum against the north wall, research loot by the door (sometimes)
		chest(level, chunkBox, c, cx - 3, floorY + 1, cz - HALF + 1, Direction.SOUTH, LOOT);
		if (noise(cx, cz, 6) < 0.6) {
			chest(level, chunkBox, c, cx + 4, floorY + 1, cz + HALF - 1, Direction.NORTH, RESEARCH);
		}
		// a desk and a broken terminal
		place(level, chunkBox, c, cx + 4, floorY + 1, cz - HALF + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
		place(level, chunkBox, c, cx + 5, floorY + 1, cz - HALF + 1, Blocks.OBSERVER.defaultBlockState());
	}

	private void chest(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int x, int y, int z, Direction facing,
			ResourceKey<LootTable> table) {
		if (!inChunk(box, x, z)) {
			return;
		}
		c.set(x, y, z);
		level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
		if (level.getBlockEntity(c) instanceof ChestBlockEntity be) {
			be.setLootTable(table, seed ^ (x * 31L + z));
		}
	}

	private void place(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int x, int y, int z, BlockState state) {
		if (!inChunk(box, x, z)) {
			return;
		}
		c.set(x, y, z);
		level.setBlock(c, state, 2);
	}

	private static boolean inChunk(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}
}
