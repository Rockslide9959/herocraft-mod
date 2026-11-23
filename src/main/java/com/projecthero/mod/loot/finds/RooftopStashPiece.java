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
 * buildings get one. The piece sits on the roof it finds when it builds (it scans down its own columns from the top of
 * the building's box), and only ever writes into air above that roof, so the City's repair blueprint never sees it.
 * The whole footprint is kept inside one chunk, so the building under it is always placed first.
 *
 * <p>Layouts: 0 a tarp lean-to with a bedroll, 1 a radio post (dish, antenna, a desk with a laptop), 2 a little plank
 * shack with a door. Each holds a Tier 1 Hero Vault and the vigilante's barrel ({@code chests/finds/rooftop_stash}).
 * Variants: the tarp / trim colour (0 black, 1 purple, 2 red).
 */
public class RooftopStashPiece extends FindPiece {
	static final int HALF = 3;
	public static final ResourceKey<LootTable> STASH = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/finds/rooftop_stash"));
	/** Building templates with flat, walkable roofs (substrings of the City template ids). */
	public static final String[] ROOFS = { "building/apartment_tower", "building/brownstone_walkup", "building/maintenance_depot",
			"building/warehouse_docks", "building/fire_station", "building/police_precinct", "building/hospital", "building/daily_bulletin",
			"building/townhouse_row" };

	/** The roof's top block, found on the first build (the box top is the building's). -1 = not yet. */
	private int roofY = -1;

	public RooftopStashPiece(int centerX, int topY, int centerZ, int minY) {
		super(FindsMod.ROOFTOP_STASH_PIECE, centerX, topY, centerZ, HALF, topY - minY, 6, 3, 3);
	}

	public RooftopStashPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.ROOFTOP_STASH_PIECE, tag);
		this.roofY = tag.contains("RoofY") ? tag.getInt("RoofY") : -1;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		super.addAdditionalSaveData(context, tag);
		tag.putInt("RoofY", roofY);
	}

	/**
	 * Adds stashes to a freshly assembled City: up to two of its flat-roofed buildings (chosen by the start's seed) get
	 * one, kept 3 blocks in from the building's edges and wholly inside one chunk. Returns how many were added.
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
			BoundingBox b = roofs.remove((int) Math.floorMod(h >>> 33, (long) roofs.size()));
			int[] spot = spot(b);
			if (spot == null) {
				continue;
			}
			builder.addPiece(new RooftopStashPiece(spot[0], b.maxY(), spot[1], b.minY()));
			added++;
		}
		return added;
	}

	/** A centre inside {@code b} (3 in from its edges) whose 7 x 7 footprint fits in one chunk, or null. */
	static int[] spot(BoundingBox b) {
		int x0 = b.minX() + 3 + HALF, x1 = b.maxX() - 3 - HALF, z0 = b.minZ() + 3 + HALF, z1 = b.maxZ() - 3 - HALF;
		if (x0 > x1 || z0 > z1) {
			return null;
		}
		int x = fit((x0 + x1) / 2, x0, x1);
		int z = fit((z0 + z1) / 2, z0, z1);
		return x == Integer.MIN_VALUE || z == Integer.MIN_VALUE ? null : new int[] { x, z };
	}

	/** The nearest value to {@code want} in [lo, hi] whose +-HALF stays in one chunk (chunk-local 3..12). */
	private static int fit(int want, int lo, int hi) {
		for (int d = 0; d <= 16; d++) {
			for (int s : new int[] { want - d, want + d }) {
				int local = Math.floorMod(s, 16);
				if (s >= lo && s <= hi && local >= HALF && local <= 15 - HALF) {
					return s;
				}
			}
		}
		return Integer.MIN_VALUE;
	}

	/** The roof level once built (or measured). */
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

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		if (roofY < 0) {
			// the most common top height over the footprint (a vent or a water tank under the centre must not lift us)
			java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
			int min = getBoundingBox().minY();
			for (int dx = -HALF; dx <= HALF; dx++) {
				for (int dz = -HALF; dz <= HALF; dz++) {
					int top = topSolid(level, dx, dz, getBoundingBox().maxY() - 6, min);
					if (top >= min) {
						counts.merge(top, 1, Integer::sum);
					}
				}
			}
			if (counts.isEmpty()) {
				return; // nothing under us (a broken template?): leave the roof alone
			}
			roofY = counts.entrySet().stream().max(java.util.Map.Entry.<Integer, Integer>comparingByValue()
					.thenComparing(java.util.Map.Entry.comparingByKey())).get().getKey();
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
