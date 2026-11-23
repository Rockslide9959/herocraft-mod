package com.projecthero.mod.loot.finds;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.17.7 Rooftop Vigilante Stash: a street-level hero's hidey-hole on a City roof. Only ever generated as part of a City
 * ({@link #addTo}, called by {@code CityStructure} after the jigsaw has laid the town out): one or two flat-roofed
 * buildings get one. The piece covers one chunk of the building ({@link #forBuilding}), so the building under it is
 * always placed first; when it builds it searches that chunk for a flat patch of roof (at least {@link #MIN_FLAT} of the
 * 49 columns of a 7 x 7 patch at the same top height, well above the building's foot, the highest such patch) and puts the stash
 * there -- a building box also holds yards and gardens, which must never get it. It only ever writes into air above
 * the roof, so the City's repair blueprint never sees it. No flat patch: no stash.
 *
 * <p>Layouts: 0 a tarp lean-to with a bedroll, 1 a radio post (dish, antenna, a desk with a laptop), 2 a little plank
 * shack with a door. Each holds a Tier 1 Hero Vault and the vigilante's barrel ({@code chests/finds/rooftop_stash}).
 * Variants: the tarp / trim colour (0 black, 1 purple, 2 red).
 */
public class RooftopStashPiece extends FindPiece {
	static final int HALF = 3;
	/** The piece box: one chunk (centred on the chunk middle, +-7). */
	static final int BOX_HALF = 7;
	/** How many of the 49 footprint columns must share the roof height. */
	public static final int MIN_FLAT = 30;
	public static final ResourceKey<LootTable> STASH = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/finds/rooftop_stash"));
	/** Building templates with flat, walkable roofs (substrings of the City template ids). */
	public static final String[] ROOFS = { "building/apartment_tower", "building/brownstone_walkup", "building/fire_station",
			"building/police_precinct", "building/hospital", "building/daily_bulletin" };

	/** The roof's top block, found on the first build. -1 = not searched yet, -2 = no flat roof here. */
	private int roofY = -1;
	/** The footprint centre's offset from the piece centre, chosen by the roof search. */
	private int ox, oz;
	/** The building's area (world x0, z0, x1, z1) the search stays inside. */
	private final int[] area;

	/** A stash piece over the chunk of {@code area} whose middle is (centerX, centerZ); topY / minY: the building's box. */
	public RooftopStashPiece(int centerX, int topY, int centerZ, int minY, int[] area) {
		super(FindsMod.ROOFTOP_STASH_PIECE, centerX, topY, centerZ, BOX_HALF, topY - minY, 6, 3, 3);
		this.area = area.clone();
	}

	/** Tests: a piece whose search area is its whole box. */
	public RooftopStashPiece(int centerX, int topY, int centerZ, int minY) {
		this(centerX, topY, centerZ, minY, new int[] { centerX - BOX_HALF, centerZ - BOX_HALF, centerX + BOX_HALF, centerZ + BOX_HALF });
	}

	public RooftopStashPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.ROOFTOP_STASH_PIECE, tag);
		this.roofY = tag.contains("RoofY") ? tag.getInt("RoofY") : -1;
		this.ox = tag.getInt("OffX");
		this.oz = tag.getInt("OffZ");
		int[] a = tag.getIntArray("Area");
		this.area = a.length == 4 ? a : new int[] { cx - BOX_HALF, cz - BOX_HALF, cx + BOX_HALF, cz + BOX_HALF };
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		super.addAdditionalSaveData(context, tag);
		tag.putInt("RoofY", roofY);
		tag.putInt("OffX", ox);
		tag.putInt("OffZ", oz);
		tag.putIntArray("Area", area);
	}

	@Override
	protected int wx(int dx, int dz) {
		return super.wx(dx, dz) + ox;
	}

	@Override
	protected int wz(int dx, int dz) {
		return super.wz(dx, dz) + oz;
	}

	/**
	 * Adds stashes to a freshly assembled City: up to two of its flat-roofed buildings (chosen by the start's seed) get
	 * one. Returns how many were added.
	 */
	public static int addTo(StructurePiecesBuilder builder, long seed) {
		List<BoundingBox> roofs = new ArrayList<>();
		for (StructurePiece piece : builder.build().pieces()) {
			if (piece instanceof PoolElementStructurePiece pe) {
				String s = pe.getElement().toString();
				for (String r : ROOFS) {
					if (s.contains(r)) {
						roofs.add(pe.getBoundingBox());
						break;
					}
				}
			}
		}
		int added = 0;
		long h = seed;
		for (int tries = 0; tries < 6 && added < 2 && !roofs.isEmpty(); tries++) {
			h = h * 6364136223846793005L + 1442695040888963407L;
			RooftopStashPiece piece = forBuilding(roofs.remove((int) Math.floorMod(h >>> 33, (long) roofs.size())));
			if (piece != null) {
				builder.addPiece(piece);
				added++;
			}
		}
		return added;
	}

	/**
	 * A stash piece over the chunk holding the most of building box {@code b} (2 in from its edges), or null if no chunk
	 * holds a 7 x 7 patch of it.
	 */
	public static RooftopStashPiece forBuilding(BoundingBox b) {
		int x0 = b.minX() + 2, z0 = b.minZ() + 2, x1 = b.maxX() - 2, z1 = b.maxZ() - 2;
		int best = 0, bx = 0, bz = 0;
		for (int chx = x0 >> 4; chx <= x1 >> 4; chx++) {
			for (int chz = z0 >> 4; chz <= z1 >> 4; chz++) {
				int ix0 = Math.max(x0, (chx << 4) + 1), ix1 = Math.min(x1, (chx << 4) + 15);
				int iz0 = Math.max(z0, (chz << 4) + 1), iz1 = Math.min(z1, (chz << 4) + 15);
				int w = ix1 - ix0 + 1, d = iz1 - iz0 + 1;
				if (w >= 7 && d >= 7 && w * d > best) {
					best = w * d;
					bx = chx;
					bz = chz;
				}
			}
		}
		if (best == 0) {
			return null;
		}
		return new RooftopStashPiece((bx << 4) + 8, b.maxY(), (bz << 4) + 8, b.minY(), new int[] { x0, z0, x1, z1 });
	}

	/** The roof level once built (-1 not yet, -2 none found). */
	public int roofY() {
		return roofY;
	}

	@Override
	public int groundY() {
		return roofY >= 0 ? roofY : groundY;
	}

	@Override
	public BlockPos vaultPos() {
		int r = groundY();
		return switch (layout) {
			case 1 -> localToWorld(-1, r + 1, -1);
			case 2 -> localToWorld(1, r + 1, -1);
			default -> localToWorld(1, r + 1, -2);
		};
	}

	@Override
	public BlockPos stashPos() {
		int r = groundY();
		return switch (layout) {
			case 1 -> localToWorld(1, r + 1, -2);
			case 2 -> localToWorld(-1, r + 1, -1);
			default -> localToWorld(-2, r + 1, -2);
		};
	}

	private BlockState carpet() {
		return (switch (variant) {
			case 1 -> Blocks.PURPLE_CARPET;
			case 2 -> Blocks.RED_CARPET;
			default -> Blocks.BLACK_CARPET;
		}).defaultBlockState();
	}

	private BlockState wool() {
		return (switch (variant) {
			case 1 -> Blocks.PURPLE_WOOL;
			case 2 -> Blocks.RED_WOOL;
			default -> Blocks.BLACK_WOOL;
		}).defaultBlockState();
	}

	/** The top solid block of a world column, scanning down from the building's top. */
	private int columnTop(WorldGenLevel level, int x, int z) {
		BoundingBox bb = getBoundingBox();
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos(x, bb.maxY() - 6, z);
		for (int y = bb.maxY() - 6; y >= bb.minY(); y--) {
			c.setY(y);
			BlockState s = level.getBlockState(c);
			if (!s.isAir() && !s.canBeReplaced()) {
				return y;
			}
		}
		return bb.minY() - 1;
	}

	/** Finds the highest flat roof patch in the piece's chunk and the building's area; false if there is none. */
	private boolean searchRoof(WorldGenLevel level) {
		BoundingBox bb = getBoundingBox();
		int x0 = Math.max(bb.minX(), area[0]), z0 = Math.max(bb.minZ(), area[1]);
		int x1 = Math.min(bb.maxX(), area[2]), z1 = Math.min(bb.maxZ(), area[3]);
		if (x1 - x0 < 6 || z1 - z0 < 6) {
			return false;
		}
		int w = x1 - x0 + 1, d = z1 - z0 + 1;
		int[][] tops = new int[w][d];
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < d; j++) {
				tops[i][j] = columnTop(level, x0 + i, z0 + j);
			}
		}
		int floor = bb.minY() + 5;
		int bestY = Integer.MIN_VALUE, bestN = 0, bestI = 0, bestJ = 0;
		for (int i = HALF; i < w - HALF; i++) {
			for (int j = HALF; j < d - HALF; j++) {
				java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
				for (int a = -HALF; a <= HALF; a++) {
					for (int b = -HALF; b <= HALF; b++) {
						counts.merge(tops[i + a][j + b], 1, Integer::sum);
					}
				}
				for (var e : counts.entrySet()) {
					int y = e.getKey(), n = e.getValue();
					if (n >= MIN_FLAT && y >= floor && (y > bestY || y == bestY && n > bestN)) {
						bestY = y;
						bestN = n;
						bestI = i;
						bestJ = j;
					}
				}
			}
		}
		if (bestN == 0) {
			return false;
		}
		roofY = bestY;
		ox = x0 + bestI - cx;
		oz = z0 + bestJ - cz;
		return true;
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		if (roofY == -2) {
			return;
		}
		if (roofY < 0 && !searchRoof(level)) {
			roofY = -2; // no flat roof in this chunk of the building: no stash
			return;
		}
		int r = roofY;
		// only on columns that are flat with the centre, and only into air
		switch (layout) {
			case 1 -> radio(level, box, r);
			case 2 -> shack(level, box, r);
			default -> leanTo(level, box, r);
		}
	}

	/** Writes {@code state} at local (dx, y, dz) only if the roof under that column is at the centre's height and the cell is air. */
	private void roofPut(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, BlockState state) {
		if (topSolid(level, dx, dz, roofY, roofY - 2) != roofY && y > roofY) {
			// the column isn't flat roof here; still allow things that stack on our own blocks
			BlockState below = get(level, dx, y - 1, dz);
			if (below.isAir()) {
				return;
			}
		}
		if (get(level, dx, y, dz).isAir()) {
			put(level, box, dx, y, dz, state);
		}
	}

	private void leanTo(WorldGenLevel level, BoundingBox box, int r) {
		// fence posts, a sloping tarp, a bedroll under it
		for (int dx = -2; dx <= 2; dx += 4) {
			roofPut(level, box, dx, r + 1, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
			roofPut(level, box, dx, r + 1, -3, Blocks.SPRUCE_FENCE.defaultBlockState());
			roofPut(level, box, dx, r + 2, -3, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		for (int dx = -2; dx <= 2; dx++) {
			roofPut(level, box, dx, r + 2, 0, carpet());
			roofPut(level, box, dx, r + 3, -3, carpet());
			roofPut(level, box, dx, r + 2, -1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			roofPut(level, box, dx, r + 3, -2, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		}
		roofPut(level, box, -1, r + 1, -1, Blocks.GRAY_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.FOOT));
		roofPut(level, box, -2, r + 1, -1, Blocks.GRAY_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST).setValue(BedBlock.PART, BedPart.HEAD));
		roofPut(level, box, 0, r + 1, -2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		roofPut(level, box, 2, r + 1, 1, Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.X));
		roofPut(level, box, 0, r + 1, 2, carpet());
		roofPut(level, box, -1, r + 1, 2, carpet());
		// the tarp's hanging sides, a crate stack with a spyglass-and-map table, a grapple line coiled by the edge
		roofPut(level, box, -3, r + 2, -1, wool());
		roofPut(level, box, -3, r + 1, -1, wool());
		roofPut(level, box, 3, r + 2, -1, wool());
		roofPut(level, box, 2, r + 1, -3, Blocks.BARREL.defaultBlockState());
		roofPut(level, box, 3, r + 1, -3, Blocks.SPRUCE_PLANKS.defaultBlockState());
		roofPut(level, box, 3, r + 2, -3, Blocks.BARREL.defaultBlockState());
		roofPut(level, box, 2, r + 2, -3, Blocks.CANDLE.defaultBlockState());
		roofPut(level, box, -2, r + 1, 2, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		roofPut(level, box, -3, r + 1, 3, Blocks.IRON_BARS.defaultBlockState());
		roofPut(level, box, -3, r + 2, 3, Blocks.TRIPWIRE_HOOK.defaultBlockState());
		stashAndVault(level, box, r);
	}

	private void radio(WorldGenLevel level, BoundingBox box, int r) {
		// the dish on a post, an antenna mast, a desk with a laptop, crates round the vault
		roofPut(level, box, 2, r + 1, -2, Blocks.IRON_BARS.defaultBlockState());
		roofPut(level, box, 2, r + 2, -2, Blocks.IRON_BARS.defaultBlockState());
		roofPut(level, box, 2, r + 3, -2, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
		roofPut(level, box, -2, r + 1, 1, Blocks.ANDESITE_WALL.defaultBlockState());
		roofPut(level, box, -2, r + 2, 1, Blocks.IRON_BARS.defaultBlockState());
		roofPut(level, box, -2, r + 3, 1, Blocks.IRON_BARS.defaultBlockState());
		roofPut(level, box, -2, r + 4, 1, Blocks.LIGHTNING_ROD.defaultBlockState());
		roofPut(level, box, -2, r + 5, 1, Blocks.REDSTONE_TORCH.defaultBlockState());
		roofPut(level, box, 0, r + 1, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
		roofPut(level, box, 1, r + 1, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
		roofPut(level, box, 0, r + 2, 0, Blocks.BLACK_CARPET.defaultBlockState());
		roofPut(level, box, 1, r + 2, 0, Blocks.CANDLE.defaultBlockState());
		roofPut(level, box, 0, r + 1, 1, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
		roofPut(level, box, -2, r + 1, -2, Blocks.BARREL.defaultBlockState());
		roofPut(level, box, -2, r + 2, -2, wool());
		roofPut(level, box, 0, r + 1, -2, Blocks.BARREL.defaultBlockState());
		stashAndVault(level, box, r);
	}

	private void shack(WorldGenLevel level, BoundingBox box, int r) {
		// a 5 x 4 plank shack: door on the south side, a window, a slab roof, a pot of herbs outside
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 1; dz++) {
				boolean edge = Math.abs(dx) == 2 || dz == -2 || dz == 1;
				for (int y = r + 1; y <= r + 3; y++) {
					if (edge) {
						boolean door = dz == 1 && dx == 0 && y <= r + 2;
						boolean window = y == r + 2 && (Math.abs(dx) == 2 && dz == -1);
						roofPut(level, box, dx, y, dz, door ? Blocks.AIR.defaultBlockState() : window ? Blocks.GLASS_PANE.defaultBlockState()
								: Math.abs(dx) == 2 && (dz == -2 || dz == 1) ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
					}
				}
				roofPut(level, box, dx, r + 4, dz, Blocks.SPRUCE_SLAB.defaultBlockState());
			}
		}
		roofPut(level, box, 0, r + 1, 1, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		roofPut(level, box, 0, r + 2, 1, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		roofPut(level, box, 0, r + 3, -1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		roofPut(level, box, 0, r + 1, -1, carpet());
		roofPut(level, box, 2, r + 1, 2, Blocks.POTTED_FERN.defaultBlockState());
		roofPut(level, box, -2, r + 1, 2, Blocks.COMPOSTER.defaultBlockState());
		roofPut(level, box, 0, r + 4, 2, wool());
		stashAndVault(level, box, r);
	}

	private void stashAndVault(WorldGenLevel level, BoundingBox box, int r) {
		BlockPos v = vaultPos();
		BlockPos s = stashPos();
		int[] vl = local(v), sl = local(s);
		Direction face = layout == 1 ? Direction.SOUTH : layout == 2 ? Direction.EAST : Direction.SOUTH;
		if (get(level, vl[0], r + 1, vl[1]).isAir() && topSolid(level, vl[0], vl[1], r, r - 2) == r) {
			vault(level, box, vl[0], r + 1, vl[1], layout == 2 ? Direction.WEST : face);
		}
		if (get(level, sl[0], r + 1, sl[1]).isAir() && topSolid(level, sl[0], sl[1], r, r - 2) == r) {
			lootContainer(level, box, sl[0], r + 1, sl[1], facing(Blocks.BARREL, layout == 2 ? Direction.EAST : Direction.SOUTH), STASH);
		}
	}

	private int[] local(BlockPos world) {
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				if (wx(dx, dz) == world.getX() && wz(dx, dz) == world.getZ()) {
					return new int[] { dx, dz };
				}
			}
		}
		return new int[] { 0, 0 };
	}
}
