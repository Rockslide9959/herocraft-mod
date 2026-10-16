package com.projecthero.mod.bluebeetle.worldgen;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.16.5 (user: "add a dig site structure that has at least 23 pieces of suspicious sand ... to make the scarab easier to
 * obtain"): an archaeologists' dig in the desert, built as one procedural piece round a single stored reference height (so
 * every chunk of it builds level, whatever order the chunks generate in).
 *
 * <pre>
 *   z -8 .. -6   a strip of sifting tables (composters, trapdoor-topped barrels) and stacked crates
 *   z -4 .. 4    the pit, 11 x 9: a one-block step two deep round a floor four deep, strata walls (sandstone / terracotta),
 *                scaffolding and a ladder down, a broken pillar and the half-uncovered beetle mosaic in the middle,
 *                and the suspicious sand -- the floor and the step in a fixed checker, a few in the walls
 *   z 7 .. 11    two canvas tents (the left one holds the finds chest), a campfire with log benches between them
 *   round it     a rope line of fences with lanterns at the corners
 * </pre>
 *
 * Every suspicious sand gets {@link #ARCHAEOLOGY} (the Scarab 2 in 37, about 5%, a block -- with 42 of them a fully brushed site gives
 * one about 90% of the time, against 1 in 9 for each of a pyramid's few sands), the finds chest {@link #CHEST}.
 */
public class DigSitePiece extends StructurePiece {
	public static final ResourceKey<LootTable> ARCHAEOLOGY = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("archaeology/dig_site"));
	public static final ResourceKey<LootTable> CHEST = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/dig_site"));

	private static final int PIT_X = 5;
	private static final int PIT_Z = 4;
	private static final int MIN_X = -10;
	private static final int MAX_X = 10;
	private static final int MIN_Z = -9;
	private static final int MAX_Z = 12;

	private final int baseY;

	public DigSitePiece(int centerX, int surfaceY, int centerZ) {
		super(ModStructurePieceTypes.DIG_SITE, 0, new BoundingBox(centerX + MIN_X, surfaceY - 9, centerZ + MIN_Z,
				centerX + MAX_X, surfaceY + 8, centerZ + MAX_Z));
		this.baseY = surfaceY;
	}

	public DigSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.DIG_SITE, tag);
		this.baseY = tag.contains("BaseY") ? tag.getInt("BaseY") : this.boundingBox.minY() + 9;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("BaseY", baseY);
	}

	/** The site's centre at ground level (the top sand block of the surrounding ground). */
	public BlockPos center() {
		return new BlockPos(this.boundingBox.minX() - MIN_X, baseY, this.boundingBox.minZ() - MIN_Z);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		build(level, chunkBox);
	}

	/** Builds a whole site centred on {@code x, z} right now (the harness, the gametests). */
	public static DigSitePiece buildAt(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		DigSitePiece piece = new DigSitePiece(x, y, z);
		piece.build(level, piece.getBoundingBox());
		return piece;
	}

	// ---------------- layout ----------------

	/** Inside the pit footprint (the step ring included). */
	static boolean inPit(int dx, int dz) {
		return Math.abs(dx) <= PIT_X && Math.abs(dz) <= PIT_Z;
	}

	/** The deep floor (inside the step ring). */
	static boolean deep(int dx, int dz) {
		return Math.abs(dx) <= PIT_X - 1 && Math.abs(dz) <= PIT_Z - 1;
	}

	/** Where the scaffolding stands (deep floor, north-east corner). */
	private static boolean scaffold(int dx, int dz) {
		return dx == PIT_X - 1 && dz == -(PIT_Z - 1);
	}

	/** The beetle mosaic (2 x 3 round the centre) and the broken pillar are never sand. */
	private static boolean feature(int dx, int dz) {
		return (Math.abs(dx) <= 1 && dz >= -1 && dz <= 1) || (dx == -3 && dz == 2) || (dx == -1 && dz == PIT_Z - 1) || scaffold(dx, dz);
	}

	/**
	 * Which pit-floor cells are suspicious sand: a fixed pattern (it must not depend on chunk order), about every other cell
	 * of the deep floor and every third of the step -- 37, plus 5 in the walls.
	 */
	public static boolean suspicious(int dx, int dz) {
		if (!inPit(dx, dz) || feature(dx, dz)) {
			return false;
		}
		if (deep(dx, dz)) {
			return Math.floorMod(dx + dz, 2) == 0;
		}
		return Math.floorMod(dx * 2 + dz, 3) == 0;
	}

	/** Suspicious sand set into the pit walls (dx, dy below ground, dz): the strata hold finds too. */
	private static final int[][] WALL_FINDS = { { -6, 2, 1 }, { 6, 2, -2 }, { 2, 2, 5 }, { -3, 1, -5 }, { 6, 1, 2 } };

	/** How many suspicious sand blocks a full site holds. */
	public static int suspiciousCount() {
		int n = WALL_FINDS.length;
		for (int dx = -PIT_X; dx <= PIT_X; dx++) {
			for (int dz = -PIT_Z; dz <= PIT_Z; dz++) {
				if (suspicious(dx, dz)) {
					n++;
				}
			}
		}
		return n;
	}

	public void build(WorldGenLevel level, BoundingBox box) {
		BlockPos c = center();
		int cx = c.getX();
		int cz = c.getZ();
		// 1. level the ground: sand at baseY over a sandstone base, air above
		for (int dx = MIN_X; dx <= MAX_X; dx++) {
			for (int dz = MIN_Z; dz <= MAX_Z; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!box.isInside(x, baseY, z)) {
					continue;
				}
				for (int y = baseY + 8; y > baseY; y--) {
					put(level, box, x, y, z, Blocks.AIR.defaultBlockState());
				}
				put(level, box, x, baseY, z, Blocks.SAND.defaultBlockState());
				for (int y = baseY - 1; y >= baseY - 8; y--) {
					BlockState below = level.getBlockState(new BlockPos(x, y, z));
					if (!below.isAir() && below.getFluidState().isEmpty() && y < baseY - 2) {
						break;
					}
					put(level, box, x, y, z, y >= baseY - 2 ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
				}
			}
		}
		// 2. the pit
		for (int dx = -PIT_X - 1; dx <= PIT_X + 1; dx++) {
			for (int dz = -PIT_Z - 1; dz <= PIT_Z + 1; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!inPit(dx, dz)) {
					// the pit walls: banded strata down to the deep floor
					for (int d = 1; d <= 5; d++) {
						put(level, box, x, baseY - d, z, stratum(d));
					}
					continue;
				}
				boolean isDeep = deep(dx, dz);
				int floor = baseY - (isDeep ? 4 : 2);
				for (int y = baseY; y > floor; y--) {
					put(level, box, x, y, z, Blocks.AIR.defaultBlockState());
				}
				for (int y = floor - 1; y >= floor - 2; y--) {
					put(level, box, x, y, z, Blocks.SANDSTONE.defaultBlockState());
				}
				if (!isDeep) {
					// the step's own face down to the deep floor shows the strata
					for (int d = 3; d <= 5; d++) {
						put(level, box, x, baseY - d, z, stratum(d));
					}
				}
				if (suspicious(dx, dz)) {
					finds(level, box, x, floor, z);
				} else {
					put(level, box, x, floor, z, Blocks.SAND.defaultBlockState());
				}
			}
		}
		for (int[] w : WALL_FINDS) {
			finds(level, box, cx + w[0], baseY - w[1], cz + w[2]);
		}
		int deepFloor = baseY - 4;
		// the beetle mosaic, half uncovered: a lapis-blue shell, cyan head, wings of blue terracotta
		put(level, box, cx, deepFloor, cz, Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.defaultBlockState());
		put(level, box, cx, deepFloor, cz + 1, Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.defaultBlockState());
		put(level, box, cx, deepFloor, cz - 1, Blocks.CYAN_TERRACOTTA.defaultBlockState());
		put(level, box, cx - 1, deepFloor, cz, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState());
		put(level, box, cx + 1, deepFloor, cz, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState());
		put(level, box, cx - 1, deepFloor, cz + 1, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState());
		put(level, box, cx + 1, deepFloor, cz + 1, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState());
		put(level, box, cx - 1, deepFloor, cz - 1, Blocks.SAND.defaultBlockState());
		put(level, box, cx + 1, deepFloor, cz - 1, Blocks.SAND.defaultBlockState());
		// a broken pillar
		put(level, box, cx - 3, deepFloor, cz + 2, Blocks.CUT_SANDSTONE.defaultBlockState());
		put(level, box, cx - 3, deepFloor + 1, cz + 2, Blocks.CHISELED_SANDSTONE.defaultBlockState());
		put(level, box, cx - 3, deepFloor + 2, cz + 2, Blocks.CUT_SANDSTONE_SLAB.defaultBlockState());
		put(level, box, cx - 2, deepFloor + 1, cz + 2, Blocks.SANDSTONE_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, Direction.WEST));
		// the way down: scaffolding in one corner, a ladder on the south wall
		for (int y = deepFloor + 1; y <= baseY + 1; y++) {
			put(level, box, cx + PIT_X - 1, y, cz - PIT_Z + 1, Blocks.SCAFFOLDING.defaultBlockState());
		}
		// (deep floor -> step against the step's face, step -> rim against the pit wall)
		for (int y = baseY - 3; y <= baseY - 2; y++) {
			put(level, box, cx - 1, y, cz + PIT_Z - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		for (int y = baseY - 1; y <= baseY; y++) {
			put(level, box, cx - 1, y, cz + PIT_Z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		// a brush-stand of tools on the step: a barrel and a lantern
		put(level, box, cx - PIT_X, baseY - 1, cz - PIT_Z, Blocks.BARREL.defaultBlockState());
		put(level, box, cx - PIT_X, baseY, cz - PIT_Z, Blocks.LANTERN.defaultBlockState());
		// 3. the rope line round the pit, lanterns on the corner posts
		int rx = PIT_X + 2;
		int rz = PIT_Z + 2;
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;
				if (!edge || (dz == rz && Math.abs(dx + 1) <= 1) || (dx == rx && Math.abs(dz) <= 1)) {
					continue; // gaps to walk in by (south, at the ladder; east, to the camp)
				}
				put(level, box, cx + dx, baseY + 1, cz + dz, Blocks.OAK_FENCE.defaultBlockState());
				if (Math.abs(dx) == rx && Math.abs(dz) == rz) {
					put(level, box, cx + dx, baseY + 2, cz + dz, Blocks.OAK_FENCE.defaultBlockState());
					put(level, box, cx + dx, baseY + 3, cz + dz, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		// 4. the sifting strip, north of the pit
		int sz = cz - PIT_Z - 4;
		put(level, box, cx - 5, baseY + 1, sz, Blocks.COMPOSTER.defaultBlockState());
		put(level, box, cx - 4, baseY + 1, sz, Blocks.COMPOSTER.defaultBlockState());
		put(level, box, cx - 2, baseY + 1, sz, Blocks.BARREL.defaultBlockState());
		put(level, box, cx - 2, baseY + 2, sz, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
		put(level, box, cx - 1, baseY + 1, sz, Blocks.BARREL.defaultBlockState());
		put(level, box, cx - 1, baseY + 2, sz, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
		put(level, box, cx + 1, baseY + 1, sz, Blocks.SAND.defaultBlockState()); // spoil heaps
		put(level, box, cx + 2, baseY + 1, sz, Blocks.SAND.defaultBlockState());
		put(level, box, cx + 2, baseY + 2, sz, Blocks.SAND.defaultBlockState());
		put(level, box, cx + 3, baseY + 1, sz, Blocks.SAND.defaultBlockState());
		put(level, box, cx + 5, baseY + 1, sz, Blocks.BARREL.defaultBlockState()); // crates
		put(level, box, cx + 6, baseY + 1, sz, Blocks.BARREL.defaultBlockState());
		put(level, box, cx + 5, baseY + 2, sz, Blocks.BARREL.defaultBlockState());
		// 5. the camp, south of the pit: two tents and a campfire between them
		int tz = cz + PIT_Z + 4;
		tent(level, box, cx - 6, tz, Blocks.WHITE_WOOL.defaultBlockState(), true);
		tent(level, box, cx + 4, tz, Blocks.GREEN_WOOL.defaultBlockState(), false);
		put(level, box, cx - 1, baseY + 1, tz + 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
		put(level, box, cx - 1, baseY + 1, tz + 4, Blocks.STRIPPED_OAK_LOG.defaultBlockState()
				.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
		put(level, box, cx + 1, baseY + 1, tz + 2, Blocks.STRIPPED_OAK_LOG.defaultBlockState()
				.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
	}

	/** An A-frame canvas tent 5 wide (x) and 4 deep (z) from {@code x0, z0}, open at the north end. */
	private void tent(WorldGenLevel level, BoundingBox box, int x0, int z0, BlockState canvas, boolean finds) {
		for (int dz = 0; dz < 4; dz++) {
			for (int i = 0; i < 5; i++) {
				int h = 3 - Math.abs(i - 2); // 1, 2, 3, 2, 1
				put(level, box, x0 + i, baseY + h, z0 + dz, canvas);
			}
		}
		// the back wall and the poles at the open end
		for (int i = 1; i <= 3; i++) {
			for (int h = 1; h < 3 - Math.abs(i - 2); h++) {
				put(level, box, x0 + i, baseY + h, z0 + 3, canvas);
			}
		}
		put(level, box, x0 + 2, baseY + 1, z0, Blocks.OAK_FENCE.defaultBlockState());
		put(level, box, x0 + 2, baseY + 2, z0, Blocks.OAK_FENCE.defaultBlockState());
		put(level, box, x0 + 2, baseY + 1, z0 - 1, Blocks.AIR.defaultBlockState());
		if (finds) {
			BlockPos chest = new BlockPos(x0 + 1, baseY + 1, z0 + 2);
			if (box.isInside(chest)) {
				level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 2);
				if (level.getBlockEntity(chest) instanceof ChestBlockEntity be) {
					be.setLootTable(CHEST, seed(chest));
				}
			}
			put(level, box, x0 + 3, baseY + 1, z0 + 2, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		} else {
			put(level, box, x0 + 1, baseY + 1, z0 + 2, Blocks.RED_BED.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
			put(level, box, x0 + 1, baseY + 1, z0 + 1, Blocks.RED_BED.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
			put(level, box, x0 + 3, baseY + 1, z0 + 2, Blocks.BARREL.defaultBlockState());
			put(level, box, x0 + 3, baseY + 2, z0 + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		}
	}

	/** The pit walls in bands: sand, sandstone, orange / yellow terracotta, smooth sandstone. */
	private static BlockState stratum(int depth) {
		return switch (depth) {
			case 1 -> Blocks.SAND.defaultBlockState();
			case 2 -> Blocks.SANDSTONE.defaultBlockState();
			case 3 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
			case 4 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
			default -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		};
	}

	private void finds(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		if (!box.isInside(p)) {
			return;
		}
		level.setBlock(p, Blocks.SUSPICIOUS_SAND.defaultBlockState(), 2);
		if (level.getBlockEntity(p) instanceof BrushableBlockEntity be) {
			be.setLootTable(ARCHAEOLOGY, seed(p));
		}
	}

	private static long seed(BlockPos p) {
		return p.asLong() * 0x9E3779B97F4A7C15L;
	}

	private static void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
		BlockPos p = new BlockPos(x, y, z);
		if (box.isInside(p)) {
			level.setBlock(p, state, 2);
		}
	}
}
