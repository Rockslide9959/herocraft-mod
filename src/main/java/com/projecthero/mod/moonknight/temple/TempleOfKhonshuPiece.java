package com.projecthero.mod.moonknight.temple;

import com.projecthero.mod.ProjectHeroMod;
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
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Temple of Khonshu (Moon Knight Phase 7), built block by block and clipped to the chunk being generated.
 * Entrance to the south (+z). Seen from outside to in:
 *
 * <ul>
 *   <li><b>Forecourt</b> (31 x 31): a sand-blown smooth-sandstone apron at ground level; two obelisks topped with
 *       lanterns flank a flight of sandstone steps up to the podium.</li>
 *   <li><b>Podium</b> (25 x 25, one block up): two campfire braziers either side of the doorway, sand drifted against
 *       the walls.</li>
 *   <li><b>Temple</b> (21 x 21, walls 7 high): cut-sandstone base and cornice, a chiselled band, pilasters, window
 *       slits, a few sand-blasted gaps high in the walls, four corner towers with lanterns. Above the door a raised
 *       pediment carries a <b>silver crescent</b> in quartz.</li>
 *   <li><b>Hall</b>: eight columns, hanging lanterns, a quartz crescent inlaid in the floor with its horns toward the
 *       altar, and at the centre a raised dais with a brazier at each corner and the <b>Altar of Khonshu</b> on top --
 *       directly beneath a round <b>oculus</b> (radius 3.5) in the roof, so the altar always sees the sky.</li>
 *   <li><b>Hidden chamber</b> (7 x 7, six blocks down): reached through an iron hatch in the floor behind the dais,
 *       opened by the lever on the wall above it, and a ladder shaft. A second quartz crescent on its floor points to
 *       the one chest ({@code chests/temple_of_khonshu}: always the Scarab of Khonshu, plus tomb loot), soul
 *       lanterns, candles, urns, bones.</li>
 * </ul>
 *
 * <p>Same discipline as {@code GammaLabPiece}: every level is fixed at construction from the generator's estimated
 * surface height (never the live heightmap, which this piece itself changes), and all "randomness" is a stable hash of
 * the piece seed and the block position, so the temple is identical across every chunk it spans.
 */
public class TempleOfKhonshuPiece extends StructurePiece {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
			ProjectHeroMod.id("chests/temple_of_khonshu"));

	static final int APRON = 15;        // forecourt half-width
	static final int PODIUM = 12;       // podium half-width
	static final int WALL = 10;         // wall ring
	static final int WALL_TOP = 7;      // walls F+1 .. F+7, roof at F+8
	static final int CLEAR_ABOVE = 26;  // air cleared this far above the ground
	static final double OCULUS = 3.5;
	static final double OCULUS_RIM = 4.6;
	static final int CHAMBER_DEPTH = 6; // chamber floor = F - 6

	/** The pediment crescent, row 0 = top; 'X' = quartz. */
	private static final String[] CRESCENT = {
			"..XXX..",
			".XX....",
			"XX.....",
			"XX.....",
			"XX.....",
			".XX....",
			"..XXX..",
	};
	/** The floor crescents: a bowl, horns first. */
	private static final String[] BOWL = {
			"X.....X",
			"XX...XX",
			".XXXXX.",
			"..XXX..",
	};

	private final int cx;
	private final int cz;
	/** The ground layer (the forecourt). The podium / hall floor is one above it. */
	private final int groundY;
	private final long seed;

	public TempleOfKhonshuPiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.TEMPLE_OF_KHONSHU, 0, new BoundingBox(centerX - APRON - 1, estimatedSurfaceY - 18,
				centerZ - APRON - 1, centerX + APRON + 1, estimatedSurfaceY + CLEAR_ABOVE, centerZ + APRON + 1));
		this.cx = centerX;
		this.cz = centerZ;
		// the generator's terrain height (fixed here), NOT the live heightmap -- or floors shift between chunks
		this.groundY = estimatedSurfaceY - 1;
		this.seed = ((long) centerX * 341873128712L) ^ ((long) centerZ * 132897987541L) ^ 0x4B484F4E5348554CL;
	}

	public TempleOfKhonshuPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.TEMPLE_OF_KHONSHU, tag);
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

	/** The hall floor level (F). */
	int floorY() {
		return groundY + 1;
	}

	/** Where the Altar of Khonshu stands. */
	public BlockPos altarPos() {
		return new BlockPos(cx, floorY() + 2, cz);
	}

	/** Where the chest with the Scarab stands. */
	public BlockPos chestPos() {
		return new BlockPos(cx, floorY() - CHAMBER_DEPTH + 1, cz - 2);
	}

	/** A stable 0..1 value per (x, y, z, salt). */
	private double noise(int x, int y, int z, int salt) {
		long h = seed ^ (x * 73856093L) ^ (y * 50331653L) ^ (z * 19349663L) ^ (salt * 83492791L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		final int g = groundY;
		final int f = floorY();
		final int cf = f - CHAMBER_DEPTH;
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();

		BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
		BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState();
		BlockState smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		BlockState chiseled = Blocks.CHISELED_SANDSTONE.defaultBlockState();
		BlockState sand = Blocks.SAND.defaultBlockState();
		BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
		BlockState smoothQuartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		// ---- 1. site: clear the air, sink a footing, fill the podium's core solid (the chamber is carved later)
		for (int dx = -APRON; dx <= APRON; dx++) {
			for (int dz = -APRON; dz <= APRON; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!inChunk(chunkBox, x, z)) {
					continue;
				}
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				for (int y = g + 1; y <= g + CLEAR_ABOVE; y++) {
					level.setBlock(c.set(x, y, z), air, 2);
				}
				int bottom = g - 1;
				if (m <= PODIUM) {
					for (int y = cf - 1; y <= g; y++) {
						level.setBlock(c.set(x, y, z), sandstone, 2);
					}
					bottom = cf - 2;
				}
				for (int y = bottom; y >= bottom - 8; y--) {
					c.set(x, y, z);
					if (!level.getBlockState(c).isAir() && level.getFluidState(c).isEmpty()) {
						break;
					}
					level.setBlock(c, sandstone, 2);
				}
				// the forecourt, drifting into sand toward its rim
				if (m > PODIUM) {
					double n = noise(x, g, z, 1);
					BlockState top = n < 0.12 + (m - PODIUM) * 0.16 ? sand : (n > 0.93 ? cut : smooth);
					level.setBlock(c.set(x, g, z), top, 2);
				}
			}
		}

		// ---- 2. podium and hall floor (F)
		for (int dx = -PODIUM; dx <= PODIUM; dx++) {
			for (int dz = -PODIUM; dz <= PODIUM; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!inChunk(chunkBox, x, z)) {
					continue;
				}
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				double r = Math.sqrt(dx * dx + dz * dz);
				BlockState floor;
				if (m == PODIUM || m == WALL) {
					floor = cut;
				} else if (m == WALL + 1) {
					floor = smooth;
				} else if (r <= OCULUS) {
					floor = sandstone; // the moonlit circle
				} else if (r <= OCULUS_RIM) {
					floor = chiseled;
				} else if (m == WALL - 1 || (Math.abs(dx) <= 1 && dz >= 5)) {
					floor = cut; // border + the aisle to the door
				} else {
					floor = noise(x, f, z, 2) < 0.05 ? sand : smooth;
				}
				level.setBlock(c.set(x, f, z), floor, 2);
				// sand drifted against the outside of the walls
				if (m == WALL + 1 && noise(x, f + 1, z, 3) < 0.22 && !(dz > 0 && Math.abs(dx) <= 4)) {
					level.setBlock(c.set(x, f + 1, z), sand, 2);
				}
			}
		}
		// the floor crescent in front of the dais, horns toward the altar
		inlay(level, chunkBox, c, 5, f, true, smoothQuartz);

		// ---- 3. walls, pilasters, corner towers
		for (int dx = -WALL - 1; dx <= WALL + 1; dx++) {
			for (int dz = -WALL - 1; dz <= WALL + 1; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!inChunk(chunkBox, x, z)) {
					continue;
				}
				int ax = Math.abs(dx);
				int az = Math.abs(dz);
				int m = Math.max(ax, az);
				if (m == WALL + 1) {
					// pilasters: every four blocks, never in front of the door
					int along = ax == WALL + 1 ? dz : dx;
					boolean corner = ax == WALL + 1 && az == WALL + 1;
					if (!corner && Math.abs(along) <= WALL - 2 && along % 4 == 0 && !(dz == WALL + 1 && along == 0)) {
						for (int y = 1; y <= WALL_TOP; y++) {
							level.setBlock(c.set(x, f + y, z), y == WALL_TOP || y == 1 ? chiseled : smooth, 2);
						}
					}
					continue;
				}
				if (m != WALL) {
					continue;
				}
				if (ax == WALL && az == WALL) {
					// corner tower
					for (int y = 1; y <= WALL_TOP + 3; y++) {
						level.setBlock(c.set(x, f + y, z), y == 1 || y == 5 || y == WALL_TOP + 3 ? chiseled : cut, 2);
					}
					level.setBlock(c.set(x, f + WALL_TOP + 4, z), Blocks.LANTERN.defaultBlockState(), 2);
					continue;
				}
				int along = ax == WALL ? dz : dx;
				boolean front = dz == WALL;
				for (int y = 1; y <= WALL_TOP; y++) {
					BlockState b;
					if (front && ax <= 1 && y <= 4) {
						b = air; // the doorway
					} else if (front && ax <= 1 && y == 5) {
						b = chiseled; // lintel
					} else if (y <= 4 && y >= 3 && Math.abs(along) == 4 && !front) {
						b = air; // window slits
					} else if (y == 1 || y == WALL_TOP) {
						b = cut;
					} else if (y == 4) {
						b = Math.floorMod(along, 2) == 0 ? chiseled : sandstone;
					} else {
						b = sandstone;
						double n = noise(x, f + y, z, 4);
						boolean sturdy = Math.abs(along) >= WALL - 1 || (front && ax <= 2);
						if (y >= 5 && !sturdy && n < 0.05) {
							b = air; // sand-blasted gap
						} else if (n < 0.16) {
							b = smooth;
						}
					}
					level.setBlock(c.set(x, f + y, z), b, 2);
				}
			}
		}

		// ---- 4. roof with the oculus, parapet
		for (int dx = -WALL; dx <= WALL; dx++) {
			for (int dz = -WALL; dz <= WALL; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!inChunk(chunkBox, x, z)) {
					continue;
				}
				int ax = Math.abs(dx);
				int az = Math.abs(dz);
				if (ax == WALL && az == WALL) {
					continue; // tower
				}
				double r = Math.sqrt(dx * dx + dz * dz);
				int roof = f + WALL_TOP + 1;
				if (r <= OCULUS) {
					continue; // open to the moon
				}
				level.setBlock(c.set(x, roof, z), r <= OCULUS_RIM ? chiseled : (Math.max(ax, az) == WALL ? cut : smooth), 2);
				if (r <= OCULUS_RIM) {
					level.setBlock(c.set(x, roof + 1, z), Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState(), 2);
				} else if (Math.max(ax, az) == WALL && Math.floorMod(ax == WALL ? dz : dx, 2) == 0) {
					level.setBlock(c.set(x, roof + 1, z), cut, 2); // crenellation
				}
			}
		}

		// ---- 5. the pediment over the door, with the silver crescent
		for (int dx = -4; dx <= 4; dx++) {
			for (int y = WALL_TOP + 1; y <= WALL_TOP + 8; y++) {
				BlockState b;
				int row = WALL_TOP + 7 - y; // 0 at the top of the crescent
				if (y == WALL_TOP + 8) {
					b = chiseled;
				} else if (Math.abs(dx) == 4) {
					b = cut;
				} else if (row >= 0 && row < CRESCENT.length && CRESCENT[row].charAt(dx + 3) == 'X') {
					b = quartz;
				} else {
					b = y == WALL_TOP + 1 ? cut : sandstone;
				}
				place(level, chunkBox, c, cx + dx, f + y, cz + WALL, b);
			}
		}

		// ---- 6. the hall: columns, dais, altar, braziers, lanterns
		for (int sx : new int[] { -6, 6 }) {
			for (int dz : new int[] { -6, -2, 2, 6 }) {
				for (int y = 1; y <= WALL_TOP; y++) {
					place(level, chunkBox, c, cx + sx, f + y, cz + dz, y == 1 || y == WALL_TOP ? chiseled : cut);
				}
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockState b = dx == 0 && dz == 0 ? chiseled : cut;
				if (dx == 0 && Math.abs(dz) == 2) {
					b = stair(dz > 0 ? Direction.NORTH : Direction.SOUTH);
				} else if (dz == 0 && Math.abs(dx) == 2) {
					b = stair(dx > 0 ? Direction.WEST : Direction.EAST);
				}
				place(level, chunkBox, c, cx + dx, f + 1, cz + dz, b);
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
					place(level, chunkBox, c, cx + dx, f + 2, cz + dz, Blocks.CAMPFIRE.defaultBlockState());
				}
			}
		}
		place(level, chunkBox, c, cx, f + 2, cz, KhonshuTemple.KHONSHU_ALTAR.defaultBlockState());
		BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (int[] o : new int[][] { { -4, -7 }, { 4, -7 }, { -4, 7 }, { 4, 7 }, { -8, 0 }, { 8, 0 } }) {
			place(level, chunkBox, c, cx + o[0], f + WALL_TOP, cz + o[1], hanging);
		}

		// ---- 7. outside: steps, braziers, obelisks
		for (int dx = -3; dx <= 3; dx++) {
			place(level, chunkBox, c, cx + dx, f, cz + PODIUM + 1, stair(Direction.NORTH));
		}
		for (int sx : new int[] { -3, 3 }) {
			place(level, chunkBox, c, cx + sx, f + 1, cz + WALL + 1, Blocks.CAMPFIRE.defaultBlockState());
		}
		for (int sx : new int[] { -6, 6 }) {
			int z = cz + PODIUM + 2;
			for (int y = 0; y <= 4; y++) {
				place(level, chunkBox, c, cx + sx, f + y, z, y == 0 || y == 4 ? chiseled : cut);
			}
			place(level, chunkBox, c, cx + sx, f + 5, z, Blocks.LANTERN.defaultBlockState());
		}

		// ---- 8. the hidden way down: a lever-opened iron hatch behind the dais, a ladder shaft
		int hz = cz - WALL + 1; // -9
		place(level, chunkBox, c, cx, f, hz, Blocks.IRON_TRAPDOOR.defaultBlockState()
				.setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, false)
				.setValue(TrapDoorBlock.FACING, Direction.SOUTH));
		place(level, chunkBox, c, cx, f + 1, hz, Blocks.LEVER.defaultBlockState()
				.setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.SOUTH));
		for (int sx : new int[] { -1, 1 }) {
			place(level, chunkBox, c, cx + sx, f + 1, hz, Blocks.DECORATED_POT.defaultBlockState());
		}
		for (int y = cf + 1; y <= f - 1; y++) {
			place(level, chunkBox, c, cx, y, hz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}

		// ---- 9. the hidden chamber: interior x -3..3, z -8..-2, floor at F-6
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -9; dz <= -1; dz++) {
				boolean inside = Math.abs(dx) <= 3 && dz >= -8 && dz <= -2;
				for (int y = cf; y <= cf + 4; y++) {
					if (dx == 0 && dz == -9 && y > cf) {
						continue; // the ladder shaft
					}
					BlockState b;
					if (y == cf) {
						b = inside && Math.abs(dx) == 3 && (dz == -8 || dz == -2) ? chiseled : cut;
					} else if (y == cf + 4) {
						b = smooth;
					} else if (inside) {
						b = air;
					} else {
						b = y == cf + 2 && Math.floorMod(dx + dz, 2) == 0 ? chiseled : cut;
					}
					place(level, chunkBox, c, cx + dx, y, cz + dz, b);
				}
			}
		}
		// its crescent points at the chest
		for (int row = 0; row < BOWL.length; row++) {
			for (int col = 0; col < 7; col++) {
				if (BOWL[row].charAt(col) == 'X') {
					place(level, chunkBox, c, cx + col - 3, cf, cz - 4 - row, smoothQuartz);
				}
			}
		}
		chest(level, chunkBox, c, cx, cf + 1, cz - 2, Direction.NORTH);
		BlockState candles = Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
		place(level, chunkBox, c, cx - 1, cf + 1, cz - 2, candles);
		place(level, chunkBox, c, cx + 1, cf + 1, cz - 2, candles);
		for (int sx : new int[] { -3, 3 }) {
			place(level, chunkBox, c, cx + sx, cf + 1, cz - 2, Blocks.SOUL_LANTERN.defaultBlockState());
			place(level, chunkBox, c, cx + sx, cf + 1, cz - 8, Blocks.SOUL_LANTERN.defaultBlockState());
			place(level, chunkBox, c, cx + sx, cf + 3, cz - 8, Blocks.COBWEB.defaultBlockState());
			place(level, chunkBox, c, cx + sx, cf + 1, cz - 6, Blocks.DECORATED_POT.defaultBlockState());
		}
		place(level, chunkBox, c, cx - 3, cf + 1, cz - 4, Blocks.BONE_BLOCK.defaultBlockState());
		place(level, chunkBox, c, cx + 3, cf + 1, cz - 4, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 12));
		place(level, chunkBox, c, cx + 2, cf + 3, cz - 2, Blocks.COBWEB.defaultBlockState());
	}

	/** A bowl-shaped crescent of {@code block} in the floor at {@code y}, rows from {@code dz0} outward (south). */
	private void inlay(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int dz0, int y, boolean south,
			BlockState block) {
		for (int row = 0; row < BOWL.length; row++) {
			for (int col = 0; col < 7; col++) {
				if (BOWL[row].charAt(col) == 'X') {
					place(level, box, c, cx + col - 3, y, cz + (south ? dz0 + row : -dz0 - row), block);
				}
			}
		}
	}

	private static BlockState stair(Direction facing) {
		return Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	private void chest(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int x, int y, int z, Direction facing) {
		if (!inChunk(box, x, z)) {
			return;
		}
		c.set(x, y, z);
		level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
		if (level.getBlockEntity(c) instanceof ChestBlockEntity be) {
			be.setLootTable(LOOT, seed ^ (x * 31L + z));
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
