package com.projecthero.mod.syndicate;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.EventManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: the Syndicate's warehouse -- the hideout a {@link com.projecthero.mod.syndicate.item.PoliceScannerItem}
 * tracks down and the arena of a {@link SyndicateBust}.
 *
 * <p>Everything is laid out in <em>local</em> coordinates round an origin on the warehouse floor: {@code lx} runs to the
 * right, {@code lz} runs from the roller door ({@code lz = -12}) to the back wall ({@code lz = 12}), {@code ly} is height
 * above the floor. {@link #at} turns a local point into the world for a given {@code forward} direction, so the same
 * layout serves all four rotations.
 *
 * <pre>
 *   back wall (lz 12)  [ office | Kingpin's desk, the stash ]   &lt;- mezzanine, ly 3 floor, lz 6..11
 *                      [ catwalk  ---- balcony gap ----  catwalk ]  railing on lz 6, ladders at lx +-9
 *   ground floor       crate cover, lanterns overhead, the back room under the mezzanine
 *   front wall (lz -12)          [ roller door, lx -3..3 ]         side doors at lx +-12, lz -2..-1
 * </pre>
 */
public final class SyndicateHideout {
	public static final int HALF = 12;
	public static final int WALL_TOP = 7;
	public static final int ROOF = 8;
	public static final int MEZZ_FLOOR = 3;
	public static final int MEZZ_FRONT = 6;
	/** The stash sits on the mezzanine, against the back wall, inside the office. */
	public static final int STASH_LX = 0, STASH_LY = MEZZ_FLOOR + 1, STASH_LZ = 11;

	private SyndicateHideout() {
	}

	/** A place to build: the floor origin and which way the warehouse faces (its back is {@code forward}). */
	public record Site(BlockPos origin, Direction forward) {
		public BlockPos stash() {
			return at(origin, forward, STASH_LX, STASH_LY, STASH_LZ);
		}
	}

	// ---------------------------------------------------------------- geometry

	public static BlockPos at(BlockPos origin, Direction forward, int lx, int ly, int lz) {
		Direction right = forward.getClockWise();
		return origin.offset(right.getStepX() * lx + forward.getStepX() * lz, ly, right.getStepZ() * lx + forward.getStepZ() * lz);
	}

	public static Vec3 point(BlockPos origin, Direction forward, double lx, double ly, double lz) {
		Direction right = forward.getClockWise();
		return new Vec3(origin.getX() + 0.5 + right.getStepX() * lx + forward.getStepX() * lz, origin.getY() + ly,
				origin.getZ() + 0.5 + right.getStepZ() * lx + forward.getStepZ() * lz);
	}

	/** The origin back from a stash position (the inverse of {@link Site#stash}). */
	public static BlockPos originFromStash(BlockPos stash, Direction forward) {
		return at(stash, forward, -STASH_LX, -STASH_LY, -STASH_LZ);
	}

	/** True when {@code pos} is inside the warehouse's walls (a little slack), at any height up to the roof. */
	public static boolean inside(BlockPos origin, Direction forward, Vec3 pos) {
		Direction right = forward.getClockWise();
		double dx = pos.x - (origin.getX() + 0.5), dz = pos.z - (origin.getZ() + 0.5);
		double lx = dx * right.getStepX() + dz * right.getStepZ();
		double lz = dx * forward.getStepX() + dz * forward.getStepZ();
		return Math.abs(lx) <= HALF + 0.5 && Math.abs(lz) <= HALF + 0.5 && pos.y >= origin.getY() - 1 && pos.y <= origin.getY() + ROOF + 1;
	}

	// ---------------------------------------------------------------- spawn points

	/** Where a wave walks in from: the roller door and the two side doors, outside on the ground. */
	public static List<Vec3> doorSpawns(ServerLevel level, BlockPos origin, Direction forward) {
		List<Vec3> out = new ArrayList<>();
		int[][] spots = { { -2, -15 }, { 0, -16 }, { 2, -15 }, { -15, -1 }, { 15, -1 }, { -16, -2 }, { 16, -2 } };
		for (int[] s : spots) {
			out.add(ground(level, point(origin, forward, s[0], 0, s[1]), origin.getY()));
		}
		return out;
	}

	/** The back room under the mezzanine: where the crew was already hiding. */
	public static List<Vec3> backRoomSpawns(BlockPos origin, Direction forward) {
		List<Vec3> out = new ArrayList<>();
		for (int lx = -7; lx <= 7; lx += 2) {
			out.add(point(origin, forward, lx, 0, 9));
		}
		return out;
	}

	/** The catwalk either side of the office: the snipers' perch. */
	public static List<Vec3> catwalkSpawns(BlockPos origin, Direction forward) {
		List<Vec3> out = new ArrayList<>();
		for (int lx : new int[] { -10, -7, 7, 10 }) {
			out.add(point(origin, forward, lx, MEZZ_FLOOR + 1, 7));
		}
		return out;
	}

	/** The Kingpin's spot: behind his desk, in the office. */
	public static Vec3 officeSpot(BlockPos origin, Direction forward) {
		return point(origin, forward, 0, MEZZ_FLOOR + 1, 9.5);
	}

	/** A point on the ground floor in front of the balcony, for the Kingpin to look at as he arrives. */
	public static Vec3 floorCentre(BlockPos origin, Direction forward) {
		return point(origin, forward, 0, 0, -2);
	}

	private static Vec3 ground(ServerLevel level, Vec3 p, int floorY) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
		// never drop a crook into a hole far below the warehouse or onto a tree top far above it
		if (Math.abs(y - floorY) > 6) {
			y = floorY;
		}
		return new Vec3(p.x, y, p.z);
	}

	// ---------------------------------------------------------------- finding a site

	/**
	 * Looks for open, fairly flat dry ground {@code minDist}-{@code maxDist} blocks from {@code near}: every chunk
	 * loaded, no water, no player-built block entities in the footprint, and nowhere near another event.
	 */
	public static Site findSite(ServerLevel level, BlockPos near, int minDist, int maxDist, RandomSource random) {
		for (int attempt = 0; attempt < 40; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = minDist + random.nextDouble() * (maxDist - minDist);
			int x = Mth.floor(near.getX() + Math.cos(angle) * dist);
			int z = Mth.floor(near.getZ() + Math.sin(angle) * dist);
			// the roller door faces back toward whoever is looking for it
			Direction forward = Direction.getNearest(Math.cos(angle), 0, Math.sin(angle));
			Site site = check(level, x, z, forward);
			if (site != null) {
				return site;
			}
		}
		return null;
	}

	/** {@link #findSite}'s per-candidate test, public for the command that builds one where you stand. */
	public static Site check(ServerLevel level, int x, int z, Direction forward) {
		int reach = HALF + 5;
		if (!level.isLoaded(new BlockPos(x - reach, 0, z - reach)) || !level.isLoaded(new BlockPos(x + reach, 0, z + reach))
				|| !level.isLoaded(new BlockPos(x - reach, 0, z + reach)) || !level.isLoaded(new BlockPos(x + reach, 0, z - reach))) {
			return null;
		}
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		List<Integer> heights = new ArrayList<>();
		for (int dx = -HALF; dx <= HALF; dx += 4) {
			for (int dz = -HALF; dz <= HALF; dz += 4) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x + dx, 0, z + dz));
				BlockPos below = top.below();
				if (!level.getFluidState(below).isEmpty() || !level.getFluidState(top).isEmpty()) {
					return null; // no warehouses on lakes or rivers
				}
				heights.add(top.getY());
				min = Math.min(min, top.getY());
				max = Math.max(max, top.getY());
			}
		}
		if (max - min > 6) {
			return null;
		}
		heights.sort(Integer::compare);
		int floor = heights.get(heights.size() / 2);
		if (floor <= level.getMinBuildHeight() + 1 || floor + ROOF + 8 >= level.getMaxBuildHeight()) {
			return null;
		}
		BlockPos origin = new BlockPos(x, floor, z);
		// someone's base: leave it alone
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				for (int dy = -3; dy <= ROOF + 3; dy++) {
					if (level.getBlockEntity(origin.offset(dx, dy, dz)) != null) {
						return null;
					}
				}
			}
		}
		if (EventManager.at(level, origin) != null) {
			return null;
		}
		return new Site(origin, forward);
	}

	// ---------------------------------------------------------------- building

	/** Builds the whole warehouse and returns the stash position (the block is placed by the caller). */
	public static BlockPos build(ServerLevel level, Site site, RandomSource random) {
		BlockPos o = site.origin();
		Direction f = site.forward();
		List<BlockPos> connectors = new ArrayList<>();

		// clear the volume (a little above the roof, to cut through trees) and lay a foundation down to the ground
		for (int lx = -HALF - 1; lx <= HALF + 1; lx++) {
			for (int lz = -HALF - 1; lz <= HALF + 1; lz++) {
				for (int ly = 0; ly <= ROOF + 6; ly++) {
					set(level, at(o, f, lx, ly, lz), Blocks.AIR.defaultBlockState());
				}
				boolean apron = Math.abs(lx) > HALF || Math.abs(lz) > HALF;
				for (int ly = -1; ly >= -8; ly--) {
					BlockPos p = at(o, f, lx, ly, lz);
					BlockState here = level.getBlockState(p);
					if (ly < -1 && !here.isAir() && here.getFluidState().isEmpty() && !here.canBeReplaced()) {
						break;
					}
					set(level, p, ly == -1 ? (apron ? Blocks.GRAVEL.defaultBlockState() : floor(random)) : Blocks.COBBLESTONE.defaultBlockState());
				}
			}
		}

		// walls, pillars and windows
		for (int lx = -HALF; lx <= HALF; lx++) {
			for (int lz = -HALF; lz <= HALF; lz++) {
				boolean edge = Math.abs(lx) == HALF || Math.abs(lz) == HALF;
				if (!edge) {
					continue;
				}
				boolean pillar = (Math.abs(lx) == HALF && lz % 6 == 0) || (Math.abs(lz) == HALF && lx % 6 == 0);
				for (int ly = 0; ly <= WALL_TOP; ly++) {
					BlockPos p = at(o, f, lx, ly, lz);
					if (doorway(lx, ly, lz)) {
						continue;
					}
					if (pillar) {
						set(level, p, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
					} else if ((ly == 5 || ly == 6) && windowColumn(lx, lz)) {
						set(level, p, Blocks.IRON_BARS.defaultBlockState());
						connectors.add(p);
					} else if (ly <= 1) {
						set(level, p, random.nextInt(5) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
					} else {
						set(level, p, random.nextInt(7) == 0 ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
					}
				}
			}
		}
		// the roller door: a lintel of chains and an iron header
		for (int lx = -3; lx <= 3; lx++) {
			set(level, at(o, f, lx, 5, -HALF), Blocks.IRON_BLOCK.defaultBlockState());
		}

		// roof, with a skylight strip down the middle
		for (int lx = -HALF; lx <= HALF; lx++) {
			for (int lz = -HALF; lz <= HALF; lz++) {
				boolean sky = Math.abs(lx) <= 1 && Math.abs(lz) <= HALF - 3 && lz % 4 != 0;
				set(level, at(o, f, lx, ROOF, lz), sky ? Blocks.TINTED_GLASS.defaultBlockState()
						: (Math.abs(lx) == HALF || Math.abs(lz) == HALF ? Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState()));
			}
		}

		// the mezzanine: floor, support pillars with ladders, railing with a balcony gap in the middle
		for (int lx = -HALF + 1; lx <= HALF - 1; lx++) {
			for (int lz = MEZZ_FRONT; lz <= HALF - 1; lz++) {
				set(level, at(o, f, lx, MEZZ_FLOOR, lz), Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
			if (Math.abs(lx) > 2 && Math.abs(lx) != 9) {
				BlockPos rail = at(o, f, lx, MEZZ_FLOOR + 1, MEZZ_FRONT);
				set(level, rail, Blocks.IRON_BARS.defaultBlockState());
				connectors.add(rail);
			}
		}
		for (int side : new int[] { -9, 9 }) {
			for (int ly = 0; ly < MEZZ_FLOOR; ly++) {
				set(level, at(o, f, side, ly, MEZZ_FRONT), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
			}
			for (int ly = 0; ly <= MEZZ_FLOOR; ly++) {
				set(level, at(o, f, side, ly, MEZZ_FRONT - 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.getOpposite()));
			}
		}
		for (int side : new int[] { -4, 4 }) {
			for (int ly = 0; ly < MEZZ_FLOOR; ly++) {
				set(level, at(o, f, side, ly, MEZZ_FRONT), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
			}
		}

		// the office on the mezzanine: dark oak walls, glass front, a 3-wide, 3-high door, a desk
		for (int lz = 8; lz <= HALF - 1; lz++) {
			for (int ly = MEZZ_FLOOR + 1; ly <= WALL_TOP; ly++) {
				for (int side : new int[] { -5, 5 }) {
					set(level, at(o, f, side, ly, lz), Blocks.DARK_OAK_PLANKS.defaultBlockState());
				}
			}
		}
		for (int lx = -5; lx <= 5; lx++) {
			for (int ly = MEZZ_FLOOR + 1; ly <= WALL_TOP; ly++) {
				BlockPos p = at(o, f, lx, ly, 8);
				if (Math.abs(lx) <= 1 && ly <= MEZZ_FLOOR + 3) {
					set(level, p, Blocks.AIR.defaultBlockState());
				} else if (Math.abs(lx) == 5 || ly == WALL_TOP || ly == MEZZ_FLOOR + 1) {
					set(level, p, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				} else {
					set(level, p, Blocks.GLASS_PANE.defaultBlockState());
					connectors.add(p);
				}
			}
			for (int lz = 9; lz <= HALF - 1; lz++) {
				set(level, at(o, f, lx, MEZZ_FLOOR, lz), Blocks.DARK_OAK_PLANKS.defaultBlockState());
				if (Math.abs(lx) < 5) {
					set(level, at(o, f, lx, MEZZ_FLOOR + 1, lz), Blocks.RED_CARPET.defaultBlockState());
				}
			}
		}
		// the desk (behind which he waits), two bookshelves either side of the stash
		for (int lx = -2; lx <= 2; lx++) {
			if (lx == 0) {
				continue;
			}
			set(level, at(o, f, lx, MEZZ_FLOOR + 1, 10), Blocks.DARK_OAK_SLAB.defaultBlockState()
					.setValue(BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
		}
		for (int side : new int[] { -3, 3 }) {
			for (int ly = MEZZ_FLOOR + 1; ly <= MEZZ_FLOOR + 2; ly++) {
				set(level, at(o, f, side, ly, HALF - 1), Blocks.BOOKSHELF.defaultBlockState());
			}
		}

		// crates and barrels on the ground floor -- cover, laid out so every lane has something to duck behind
		int[][] crates = {
				{ -8, -8, 2 }, { -7, -8, 1 }, { -8, -7, 1 },
				{ 7, -8, 2 }, { 8, -8, 1 }, { 8, -7, 2 },
				{ -3, -4, 1 }, { -2, -4, 2 }, { 3, -4, 1 }, { 2, -4, 2 },
				{ -9, -1, 2 }, { -9, 0, 1 }, { 9, -1, 2 }, { 9, 0, 1 },
				{ -5, 2, 2 }, { -4, 2, 1 }, { 5, 2, 2 }, { 4, 2, 1 },
				{ 0, 0, 1 }, { -1, 0, 1 }, { 1, 0, 2 } };
		for (int[] c : crates) {
			for (int ly = 0; ly < c[2]; ly++) {
				BlockState s = switch (random.nextInt(4)) {
					case 0 -> Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
					case 1 -> Blocks.HAY_BLOCK.defaultBlockState();
					default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
				};
				// barrels are block entities: keep them empty-and-decorative by using composters instead
				if (s.is(Blocks.BARREL)) {
					s = Blocks.COMPOSTER.defaultBlockState();
				}
				set(level, at(o, f, c[0], ly, c[1]), s);
			}
		}

		// lanterns hanging from the roof and over the doors
		for (int lx = -8; lx <= 8; lx += 8) {
			for (int lz = -8; lz <= 4; lz += 6) {
				set(level, at(o, f, lx, WALL_TOP, lz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			}
		}
		set(level, at(o, f, 0, WALL_TOP, 10), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		for (int side : new int[] { -5, 5 }) {
			set(level, at(o, f, side, 6, -HALF - 1), Blocks.LANTERN.defaultBlockState());
		}

		// panes and bars only join up once their neighbours exist
		for (BlockPos p : connectors) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof IronBarsBlock) {
				level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), Block.UPDATE_CLIENTS);
			}
		}
		return site.stash();
	}

	private static boolean doorway(int lx, int ly, int lz) {
		if (lz == -HALF && Math.abs(lx) <= 3 && ly <= 4) {
			return true; // the roller door
		}
		return Math.abs(lx) == HALF && (lz == -2 || lz == -1) && ly <= 2; // the side doors
	}

	private static boolean windowColumn(int lx, int lz) {
		int along = Math.abs(lx) == HALF ? lz : lx;
		return Math.floorMod(along, 6) == 2 || Math.floorMod(along, 6) == 4;
	}

	private static BlockState floor(RandomSource random) {
		return switch (random.nextInt(6)) {
			case 0 -> Blocks.STONE.defaultBlockState();
			case 1 -> Blocks.ANDESITE.defaultBlockState();
			default -> Blocks.POLISHED_ANDESITE.defaultBlockState();
		};
	}

	private static void set(ServerLevel level, BlockPos p, BlockState s) {
		level.setBlock(p, s, Block.UPDATE_CLIENTS);
	}
}
