package com.projecthero.mod.doctorfate.worldgen;

import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.DoctorFateItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * The Tomb of Nabu (v0.17.3 Doctor Fate), built block by block and clipped to the chunk being generated. Entrance to the south (+z).
 * <ul>
 *   <li><b>Pyramid</b>: a weathered, stepped sandstone pyramid (15 x 15, 8 high) with a gold capstone, a doorway in its south face and
 *   a corridor to its heart.</li>
 *   <li><b>Stair</b>: from the heart a sandstone stair runs down eight blocks to the north, walled and roofed.</li>
 *   <li><b>Burial chamber</b> (9 x 9, 5 high): gold-and-lapis floor inlay, a gold ankh on the north wall, soul lanterns, a desert
 *   pyramid loot chest, four Tomb Guardians, and at the back the <b>Helmet Pedestal</b> with the Helmet of Fate on it.</li>
 * </ul>
 * Every level is fixed at construction from the generator's estimated surface height; all "randomness" is a stable hash of the
 * piece seed and the block position, so the tomb is identical across the chunks it spans.
 */
public class TombOfNabuPiece extends StructurePiece {
	static final int HALF = 7;          // pyramid base half-width
	static final int DEPTH = 8;         // chamber floor = ground - 8
	static final int ROOM = 4;          // chamber interior half-width
	static final int ROOM_H = 5;

	private final int cx;
	private final int cz;
	private final int groundY;
	private final long seed;

	public TombOfNabuPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(TombOfNabuStructure.PIECE, 0, new BoundingBox(centerX - HALF - 2, estimatedSurfaceY - DEPTH - 3, centerZ - 21,
				centerX + HALF + 2, estimatedSurfaceY + 10, centerZ + HALF + 3));
		this.cx = centerX;
		this.cz = centerZ;
		this.groundY = estimatedSurfaceY - 1;
		this.seed = ((long) centerX * 341873128712L) ^ ((long) centerZ * 132897987541L) ^ 0x4E4142554E414255L;
	}

	public TombOfNabuPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(TombOfNabuStructure.PIECE, tag);
		this.cx = tag.getInt("CX");
		this.cz = tag.getInt("CZ");
		this.groundY = tag.getInt("GroundY");
		this.seed = tag.getLong("Seed");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("CX", cx);
		tag.putInt("CZ", cz);
		tag.putInt("GroundY", groundY);
		tag.putLong("Seed", seed);
	}

	/** The chamber's floor block level. */
	int floorY() {
		return groundY - DEPTH;
	}

	/** The chamber's centre (x, z). */
	int roomZ() {
		return cz - 13;
	}

	public BlockPos pedestalPos() {
		return new BlockPos(cx, floorY() + 1, roomZ() - ROOM + 1);
	}

	private double noise(int x, int y, int z, int salt) {
		long h = seed ^ (x * 73856093L) ^ (y * 50331653L) ^ (z * 19349663L) ^ (salt * 83492791L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}

	/** Weathered sandstone: mostly plain, some cut, smooth and sand-blown. */
	private BlockState stone(int x, int y, int z) {
		double n = noise(x, y, z, 1);
		if (n < 0.08) {
			return Blocks.SAND.defaultBlockState();
		}
		if (n < 0.3) {
			return Blocks.CUT_SANDSTONE.defaultBlockState();
		}
		if (n < 0.42) {
			return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		}
		return Blocks.SANDSTONE.defaultBlockState();
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
		final int g = groundY;
		final int f = floorY();
		final int rz = roomZ();
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState();
		BlockState smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		BlockState chiseled = Blocks.CHISELED_SANDSTONE.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState lapis = Blocks.LAPIS_BLOCK.defaultBlockState();

		// ---- 1. the pyramid: footing, then stepped layers, then the capstone
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int x = cx + dx, z = cz + dz;
				if (!inChunk(box, x, z)) {
					continue;
				}
				for (int y = g; y >= g - 4; y--) { // a footing so it never floats
					if (level.getBlockState(c.set(x, y, z)).isAir() || !level.getFluidState(c).isEmpty()) {
						level.setBlock(c, Blocks.SANDSTONE.defaultBlockState(), 2);
					}
				}
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				int top = HALF + 1 - m; // layers g+1 .. g+top
				for (int k = 1; k <= top; k++) {
					BlockState b = k == top && m > 0 && noise(x, k, z, 2) < 0.3 ? Blocks.SANDSTONE_SLAB.defaultBlockState() : stone(x, g + k, z);
					if (k == 3 && m == HALF - 2) {
						b = chiseled; // a carved band
					}
					level.setBlock(c.set(x, g + k, z), b, 2);
				}
				for (int y = g + top + 1; y <= g + 10; y++) {
					level.setBlock(c.set(x, y, z), air, 2);
				}
			}
		}
		place(level, box, c, cx, g + HALF + 1, cz, gold); // the gold capstone
		place(level, box, c, cx, g + HALF + 2, cz, Blocks.AIR.defaultBlockState());

		// ---- 2. the doorway and corridor to the heart (3 wide, 3 high)
		for (int dz = 0; dz <= HALF + 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int y = 1; y <= 3; y++) {
					place(level, box, c, cx + dx, g + y, cz + dz, air);
				}
				place(level, box, c, cx + dx, g, cz + dz, smooth);
			}
		}
		// the lintel over the door
		for (int dx = -2; dx <= 2; dx++) {
			place(level, box, c, cx + dx, g + 4, cz + HALF - 2, chiseled);
		}
		place(level, box, c, cx - 2, g + 1, cz + HALF + 1, chiseled);
		place(level, box, c, cx + 2, g + 1, cz + HALF + 1, chiseled);

		// ---- 3. the stair down to the north, walled and roofed so the sand stays put
		for (int i = 0; i <= DEPTH - 1; i++) {
			int z = cz - 1 - i;
			int y = g - i;
			for (int dx = -2; dx <= 2; dx++) {
				for (int yy = y - 1; yy <= y + 4; yy++) {
					boolean wall = Math.abs(dx) == 2 || yy == y + 4 || yy == y - 1;
					place(level, box, c, cx + dx, yy, z, wall ? stone(cx + dx, yy, z) : air);
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				place(level, box, c, cx + dx, y, z, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			}
			if (i % 3 == 1) {
				place(level, box, c, cx - 1, y + 3, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
			}
		}

		// ---- 4. the burial chamber
		int x0 = cx - ROOM - 1, x1 = cx + ROOM + 1, z0 = rz - ROOM - 1, z1 = rz + ROOM + 1;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!inChunk(box, x, z)) {
					continue;
				}
				for (int y = f - 1; y <= f + ROOM_H + 1; y++) {
					boolean shell = x == x0 || x == x1 || z == z0 || z == z1 || y == f - 1 || y == f + ROOM_H + 1;
					BlockState b;
					if (shell) {
						b = (y == f + 1 || y == f + ROOM_H) && !(y == f - 1 || y == f + ROOM_H + 1) ? cut : stone(x, y, z);
						if (y == f + ROOM_H + 1) {
							b = smooth;
						}
					} else if (y == f) {
						// the floor: smooth sandstone with a gold-and-lapis cross inlay pointing at the pedestal
						int ax = Math.abs(x - cx), az = Math.abs(z - rz);
						b = (ax == 0 && az <= ROOM - 1) ? (az % 2 == 0 ? gold : lapis) : (az == 0 && ax <= 2 ? lapis : smooth);
					} else {
						b = air;
					}
					level.setBlock(c.set(x, y, z), b, 2);
				}
			}
		}
		// the doorway from the stair
		for (int dx = -1; dx <= 1; dx++) {
			place(level, box, c, cx + dx, f + 1, z1, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			for (int y = 2; y <= 4; y++) {
				place(level, box, c, cx + dx, f + y, z1, air);
			}
		}
		// the gold ankh on the north wall (inside face z0)
		String[] ankh = {
				".XXX.",
				"X...X",
				".XXX.",
				"XXXXX",
				"..X..",
		};
		for (int r = 0; r < ankh.length; r++) {
			for (int k = 0; k < 5; k++) {
				if (ankh[r].charAt(k) == 'X') {
					place(level, box, c, cx - 2 + k, f + ROOM_H - r, z0, gold);
				}
			}
		}
		// lapis pillars in the corners, soul lanterns on them
		for (int sx : new int[] { -ROOM, ROOM }) {
			for (int sz : new int[] { -ROOM, ROOM }) {
				for (int y = 1; y <= ROOM_H; y++) {
					place(level, box, c, cx + sx, f + y, rz + sz, y == 1 || y == ROOM_H ? chiseled : lapis);
				}
				place(level, box, c, cx + sx + (sx > 0 ? -1 : 1), f + 1, rz + sz, Blocks.SOUL_LANTERN.defaultBlockState());
			}
		}
		// the pedestal with the Helmet of Fate, on a gold dais
		BlockPos ped = pedestalPos();
		for (int dx = -1; dx <= 1; dx++) {
			place(level, box, c, cx + dx, f, ped.getZ(), gold);
			place(level, box, c, cx + dx, f, ped.getZ() + 1, gold);
		}
		place(level, box, c, ped.getX(), ped.getY(), ped.getZ(),
				DoctorFateItems.HELMET_PEDESTAL.defaultBlockState().setValue(HelmetPedestalBlock.HAS_HELMET, true));
		// a sarcophagus and urns
		for (int dz = 0; dz <= 2; dz++) {
			place(level, box, c, cx - 3, f + 1, rz + dz - 1, Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState());
		}
		place(level, box, c, cx + 3, f + 1, rz + 2, Blocks.DECORATED_POT.defaultBlockState());
		place(level, box, c, cx - 3, f + 1, rz + 3, Blocks.DECORATED_POT.defaultBlockState());
		// a chest of tomb loot
		if (inChunk(box, cx + 3, rz - 2)) {
			c.set(cx + 3, f + 1, rz - 2);
			level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
			if (level.getBlockEntity(c) instanceof ChestBlockEntity be) {
				be.setLootTable(BuiltInLootTables.DESERT_PYRAMID, seed ^ 0x51L);
			}
		}

		// ---- 5. the guards: four Tomb Guardians (spawned once, by the chunk they stand in)
		int[][] guards = { { -2, 1 }, { 2, 1 }, { -2, -2 }, { 2, -2 } };
		for (int k = 0; k < Math.min(DoctorFateConfig.GUARDS, guards.length); k++) {
			int x = cx + guards[k][0], z = rz + guards[k][1];
			if (!inChunk(box, x, z) || DoctorFateItems.TOMB_GUARDIAN == null) {
				continue;
			}
			TombGuardian guard = DoctorFateItems.TOMB_GUARDIAN.create(level.getLevel());
			if (guard == null) {
				continue;
			}
			guard.moveTo(x + 0.5, f + 1, z + 0.5, 180f, 0f);
			guard.finalizeSpawn(level, level.getCurrentDifficultyAt(c.set(x, f + 1, z)), MobSpawnType.STRUCTURE, null);
			guard.setPersistenceRequired();
			level.addFreshEntityWithPassengers(guard);
		}
	}

	private static void place(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int x, int y, int z, BlockState state) {
		if (!inChunk(box, x, z)) {
			return;
		}
		level.setBlock(c.set(x, y, z), state, 2);
	}

	private static boolean inChunk(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}
}
