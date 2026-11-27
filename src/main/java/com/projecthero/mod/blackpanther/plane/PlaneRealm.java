package com.projecthero.mod.blackpanther.plane;

import static com.projecthero.mod.blackpanther.plane.PlaneGeometry.PATH_BASE;
import static com.projecthero.mod.blackpanther.plane.PlaneGeometry.PATH_X;
import static com.projecthero.mod.blackpanther.plane.PlaneGeometry.PEDESTAL_DX;
import static com.projecthero.mod.blackpanther.plane.PlaneGeometry.SHRINE_X;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther Ancestral Plane: <b>the plane itself</b> -- {@code projecthero:ancestral_plane}, a datapack dimension
 * (an endless flat savanna of golden grass under a fixed sky; the sunset, the purple streaks and the haze are drawn by the
 * client, {@code client.blackpanther.plane.AncestralSky}). Each visitor gets a <b>slot</b> ({@link #SPACING} blocks apart along
 * Z, so two dreamers never meet), dressed once in slices over a few seconds and then reused (a bedrock marker under the spawn):
 * <ul>
 * <li>tufts of short and tall grass everywhere, a few lilacs and alliums near the ancestors, flat-topped acacias standing dark
 * against the sky round the horizon, and the <b>great acacia</b> the ancestors wait under;</li>
 * <li>the <b>Panther Shrine</b>: a round two-step floor of polished blackstone with an amethyst ring and a gold claw sigil,
 * eight pillars with gilded capitals and candles, a crescent crown of stone open behind the statue, braziers at the entrance,
 * the altar, and the <b>seated black panther statue</b> on its pedestal (a gold necklace on its chest, its eyes obsidian until
 * Bast wakes them);</li>
 * <li>the <b>spirit path</b> ({@link ParkourCourse}) behind it, built only when the trial is won ({@link #buildPath}) and cleared
 * again for the next visit ({@link #resetForVisit}).</li>
 * </ul>
 * <b>Fallback:</b> without the dimension (the GameTest server) the same slot is built high in the overworld sky at
 * {@link #FALLBACK_X} on a grass floor of its own.
 */
public final class PlaneRealm {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectHeroMod.id("ancestral_plane"));
	public static final int SPACING = 1000;
	public static final int BASE_Y = 64;
	public static final int FALLBACK_X = 1_250_000;
	public static final int FALLBACK_Y = 150;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final TicketType<ChunkPos> TICKET = TicketType.create("projecthero_ancestral_plane", Comparator.comparingLong(ChunkPos::toLong), 60);
	/** The dressed area (offsets): grass tufts out to here. */
	private static final int VEG_MIN_X = -56, VEG_MAX_X = 268, VEG_HALF_Z = 84;
	/** The fallback's grass floor (offsets). */
	private static final int FLOOR_MIN_X = -24, FLOOR_MAX_X = 252, FLOOR_HALF_Z = 40;
	private static final int SLICE = 4;

	/** Builds in progress, (dimension, slot) -> pieces. No worlds held; cleared by {@code ServerStateReset}. */
	private static final Map<String, Build> BUILDS = new ConcurrentHashMap<>();

	private static final class Build {
		final ResourceKey<Level> dimension;
		final List<Runnable> pieces = new ArrayList<>();
		int next;

		Build(ResourceKey<Level> dimension) {
			this.dimension = dimension;
		}
	}

	private PlaneRealm() {
	}

	public static void clearSessionState() {
		BUILDS.clear();
	}

	// ---------------------------------------------------------------- where

	public static ServerLevel level(MinecraftServer server) {
		ServerLevel l = server.getLevel(DIMENSION);
		return l != null ? l : server.overworld();
	}

	public static boolean isFallback(ServerLevel level) {
		return level.dimension() != DIMENSION;
	}

	public static BlockPos origin(ServerLevel level, int slot) {
		return isFallback(level) ? new BlockPos(FALLBACK_X, FALLBACK_Y, slot * SPACING) : new BlockPos(0, BASE_Y, slot * SPACING);
	}

	/** Is {@code pos} in the plane at all: the dimension, or one of the fallback slots in the overworld sky? */
	public static boolean inPlane(ServerLevel level, Vec3 pos) {
		if (!isFallback(level)) {
			return true;
		}
		int slot = (int) Math.round(pos.z / SPACING);
		return slot >= 0 && PlaneGeometry.inBox(origin(level, slot), pos);
	}

	private static BlockPos marker(BlockPos o) {
		return o.offset(0, -12, 0);
	}

	public static boolean isBuilt(ServerLevel level, int slot) {
		return level.getBlockState(marker(origin(level, slot))).is(Blocks.BEDROCK);
	}

	private static String key(ResourceKey<Level> dim, int slot) {
		return dim.location() + "#" + slot;
	}

	/** Keeps the slot's route (spawn to the end of the path) loaded and entity-ticking for a few seconds (re-issued every second). */
	public static void hold(ServerLevel level, int slot) {
		BlockPos o = origin(level, slot);
		for (int x = -16; x <= PATH_X + ParkourCourse.length() + 40; x += 32) {
			ChunkPos c = new ChunkPos(o.offset(x, 0, 0));
			level.getChunkSource().addRegionTicket(TICKET, c, 3, c);
		}
	}

	public static boolean live(ServerLevel level, Vec3 at) {
		return level.isPositionEntityTicking(BlockPos.containing(at));
	}

	// ---------------------------------------------------------------- building

	/** Starts dressing slot {@code slot} (if it isn't done or under way). The spawn patch is built at once. */
	public static void queue(ServerLevel level, int slot) {
		BlockPos o = origin(level, slot);
		if (isBuilt(level, slot) || BUILDS.containsKey(key(level.dimension(), slot))) {
			return;
		}
		boolean fallback = isFallback(level);
		// the ground under the spawn first, right now (they land there)
		for (int x = -12; x <= 12; x++) {
			for (int z = -12; z <= 12; z++) {
				column(level, o, x, z, fallback);
			}
		}
		Build b = new Build(level.dimension());
		// outward from the spawn along the walk, then the shrine and the great tree, then the far trees
		for (int x = 13; x <= VEG_MAX_X; x += SLICE) {
			final int x0 = x;
			b.pieces.add(() -> strip(level, o, x0, Math.min(VEG_MAX_X, x0 + SLICE - 1), fallback, -12, 12));
			if (x0 > 40 && x0 < 54) {
				b.pieces.add(() -> greatTree(level, o));
			}
			if (x0 > 84 && x0 < 98) {
				b.pieces.add(() -> shrine(level, o));
			}
		}
		for (int x = VEG_MIN_X; x <= 12; x += SLICE) {
			final int x0 = x;
			b.pieces.add(() -> strip(level, o, x0, Math.min(12, x0 + SLICE - 1), fallback, -12, 12));
		}
		b.pieces.add(() -> grove(level, o));
		b.pieces.add(() -> trees(level, o, fallback));
		b.pieces.add(() -> level.setBlock(marker(o), Blocks.BEDROCK.defaultBlockState(), FLAGS));
		BUILDS.put(key(level.dimension(), slot), b);
	}

	/** Dresses slot {@code slot} right now, whole (tests, commands). */
	public static void buildNow(ServerLevel level, int slot) {
		queue(level, slot);
		Build b = BUILDS.remove(key(level.dimension(), slot));
		if (b != null) {
			while (b.next < b.pieces.size()) {
				b.pieces.get(b.next++).run();
			}
		}
	}

	/** True once the shrine stands (the story needs it before the procession). */
	public static boolean shrineReady(ServerLevel level, int slot) {
		return isBuilt(level, slot) || level.getBlockState(origin(level, slot).offset(SHRINE_X, 1, 0)).is(Blocks.GOLD_BLOCK);
	}

	/** Every tick: a few pieces of each build in progress (within about 12 ms). */
	public static void tick(MinecraftServer server) {
		if (BUILDS.isEmpty()) {
			return;
		}
		Iterator<Build> it = BUILDS.values().iterator();
		while (it.hasNext()) {
			Build b = it.next();
			ServerLevel level = server.getLevel(b.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			long until = System.nanoTime() + 12_000_000L;
			for (int i = 0; i < 6 && b.next < b.pieces.size() && (i == 0 || System.nanoTime() < until); i++) {
				b.pieces.get(b.next++).run();
			}
			if (b.next >= b.pieces.size()) {
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private static void set(ServerLevel level, BlockPos o, int x, int dy, int z, BlockState s) {
		level.setBlock(o.offset(x, dy, z), s, FLAGS);
	}

	/** A stable 0..1 hash of a position (the same every build). */
	static double hash(int x, int y, int z, int salt) {
		long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ salt * 2654435761L;
		h ^= h >>> 13;
		h *= 0x5bd1e995L;
		h ^= h >>> 15;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	private static boolean nearShrine(int x, int z, double extra) {
		return Math.hypot(x - SHRINE_X, z) <= PlaneGeometry.SHRINE_STEP_R + extra;
	}

	// ---------------------------------------------------------------- the savanna

	private static void strip(ServerLevel level, BlockPos o, int x0, int x1, boolean fallback, int skipZ0, int skipZ1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = -VEG_HALF_Z; z <= VEG_HALF_Z; z++) {
				if (x >= -12 && x <= 12 && z >= skipZ0 && z <= skipZ1) {
					continue; // the spawn patch, done already
				}
				column(level, o, x, z, fallback);
			}
		}
	}

	/** One column of savanna: the fallback's floor, then a tuft of grass (or nothing). */
	private static void column(ServerLevel level, BlockPos o, int x, int z, boolean fallback) {
		if (fallback) {
			if (x < FLOOR_MIN_X || x > FLOOR_MAX_X || Math.abs(z) > FLOOR_HALF_Z) {
				return;
			}
			set(level, o, x, -1, z, Blocks.GRASS_BLOCK.defaultBlockState());
			set(level, o, x, -2, z, Blocks.DIRT.defaultBlockState());
		}
		if (nearShrine(x, z, 1.5) || (Math.abs(x) <= 2 && Math.abs(z) <= 2)) {
			return;
		}
		double h = hash(x, 0, z, 11);
		// a gentle patchiness: thicker in drifts, thin in others
		double drift = 0.5 + 0.5 * Math.sin(x * 0.13 + Math.cos(z * 0.09) * 2.0) * Math.cos(z * 0.11 - x * 0.03);
		double grass = 0.16 + 0.32 * drift;
		if (h < grass * 0.18) {
			DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), o.offset(x, 0, z), FLAGS);
		} else if (h < grass) {
			set(level, o, x, 0, z, Blocks.SHORT_GRASS.defaultBlockState());
		} else if (h > 0.997) {
			set(level, o, x, 0, z, Blocks.DEAD_BUSH.defaultBlockState());
		}
	}

	/** Flowers round the ancestors' meeting place: purple accents in the gold. */
	private static void grove(ServerLevel level, BlockPos o) {
		for (int i = 0; i < 26; i++) {
			int x = 62 + (int) (hash(i, 1, 0, 21) * 24);
			int z = -10 + (int) (hash(i, 2, 0, 21) * 24);
			if (Math.abs(z) < 6) {
				continue; // keep the eldest's approach clear
			}
			set(level, o, x, 0, z, Blocks.AIR.defaultBlockState());
			if (hash(i, 3, 0, 21) < 0.45) {
				DoublePlantBlock.placeAt(level, Blocks.LILAC.defaultBlockState(), o.offset(x, 0, z), FLAGS);
			} else {
				set(level, o, x, 0, z, Blocks.ALLIUM.defaultBlockState());
			}
		}
	}

	// ---------------------------------------------------------------- trees

	private static final BlockState LOG = Blocks.ACACIA_LOG.defaultBlockState();
	private static final BlockState WOOD = Blocks.ACACIA_WOOD.defaultBlockState();
	private static final BlockState LEAVES = Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

	/** Acacias round the horizon, never on the walk or near the shrine. */
	private static void trees(ServerLevel level, BlockPos o, boolean fallback) {
		for (int i = 0; i < 46; i++) {
			int x = VEG_MIN_X + 6 + (int) (hash(i, 7, 0, 31) * (VEG_MAX_X - VEG_MIN_X - 12));
			int side = hash(i, 8, 0, 31) < 0.5 ? -1 : 1;
			int z = side * (18 + (int) (hash(i, 9, 0, 31) * (VEG_HALF_Z - 26)));
			if (fallback && (x < FLOOR_MIN_X + 4 || x > FLOOR_MAX_X - 4 || Math.abs(z) > FLOOR_HALF_Z - 4)) {
				continue;
			}
			if (nearShrine(x, z, 10) || Math.hypot(x - PlaneGeometry.GREAT_TREE_X, z - PlaneGeometry.GREAT_TREE_Z) < 18 || (x > PATH_X - 4 && Math.abs(z) < 22)) {
				continue;
			}
			int h = 5 + (int) (hash(i, 10, 0, 31) * 4);
			Direction lean = Direction.from2DDataValue((int) (hash(i, 11, 0, 31) * 4));
			acacia(level, o, x, z, h, lean, 2.6 + hash(i, 12, 0, 31) * 1.6, hash(i, 13, 0, 31) < 0.4);
		}
	}

	/** A flat-topped acacia: a trunk that leans one way, a wide flat crown, and sometimes a second, lower branch. */
	private static void acacia(ServerLevel level, BlockPos o, int x, int z, int h, Direction lean, double r, boolean branch) {
		int straight = Math.max(2, h / 2);
		int cx = x, cz = z;
		for (int y = 0; y < straight; y++) {
			set(level, o, cx, y, cz, LOG);
		}
		int y = straight;
		for (; y < h; y++) {
			cx += lean.getStepX();
			cz += lean.getStepZ();
			set(level, o, cx, y, cz, WOOD);
		}
		crown(level, o, cx, y, cz, r);
		if (branch) {
			Direction other = lean.getOpposite();
			int bx = x, bz = z, by = straight - 1;
			for (int k = 0; k < 3; k++) {
				bx += other.getStepX();
				bz += other.getStepZ();
				by++;
				set(level, o, bx, by, bz, WOOD);
			}
			crown(level, o, bx, by + 1, bz, Math.max(1.8, r - 1.0));
		}
	}

	/** A flat acacia crown: a wide disc with a smaller one on top, edges ragged. */
	private static void crown(ServerLevel level, BlockPos o, int cx, int y, int cz, double r) {
		int ri = (int) Math.ceil(r);
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				double d = Math.hypot(dx, dz);
				double edge = r - 0.4 * hash(cx + dx, y, cz + dz, 41);
				if (d <= edge) {
					set(level, o, cx + dx, y, cz + dz, LEAVES);
				}
				if (d <= edge - 1.5) {
					set(level, o, cx + dx, y + 1, cz + dz, LEAVES);
				}
			}
		}
		set(level, o, cx, y, cz, WOOD);
	}

	/** The great acacia: a thick trunk, three long branches, three broad crowns -- the ancestors stand in its shade. */
	private static void greatTree(ServerLevel level, BlockPos o) {
		int tx = PlaneGeometry.GREAT_TREE_X, tz = PlaneGeometry.GREAT_TREE_Z;
		for (int y = -1; y < 9; y++) {
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					set(level, o, tx + dx, y, tz + dz, y < 0 ? Blocks.DIRT.defaultBlockState() : LOG);
				}
			}
		}
		// roots flaring out at the foot
		int[][] roots = { { -1, 0 }, { 2, 1 }, { 0, -1 }, { 1, 2 }, { -1, 1 }, { 2, 0 } };
		for (int[] rt : roots) {
			set(level, o, tx + rt[0], 0, tz + rt[1], WOOD);
		}
		int[][] branches = { { 1, 0, 7, 6, 5 }, { -1, -1, 6, 5, 4 }, { 0, 1, 5, 5, 3 }, { -1, 1, 4, 4, 6 } };
		for (int[] br : branches) {
			int bx = tx + (br[0] > 0 ? 1 : 0), bz = tz + (br[1] > 0 ? 1 : 0), by = 8;
			for (int k = 0; k < br[2]; k++) {
				bx += br[0];
				bz += br[1];
				by += k % 3 == 2 ? 0 : 1;
				set(level, o, bx, by, bz, WOOD);
			}
			crown(level, o, bx, by + 1, bz, br[3] + 0.8);
		}
	}

	// ---------------------------------------------------------------- the shrine

	private static BlockState slab(Block b, SlabType t) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, t);
	}

	private static void shrine(ServerLevel level, BlockPos o) {
		int c = SHRINE_X;
		BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState polished = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
		BlockState chiseled = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		// clear the grass over the whole footprint (and a little headroom)
		for (int x = -16; x <= 16; x++) {
			for (int z = -16; z <= 16; z++) {
				if (Math.hypot(x, z) <= 15.5) {
					for (int y = 0; y < 3; y++) {
						set(level, o, c + x, y, z, air);
					}
				}
			}
		}
		// the two steps and the floor
		for (int x = -15; x <= 15; x++) {
			for (int z = -15; z <= 15; z++) {
				double r = Math.hypot(x, z);
				if (r <= 13.5) {
					set(level, o, c + x, 0, z, bricks);
				} else if (r <= 14.5) {
					set(level, o, c + x, 0, z, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, SlabType.BOTTOM));
				}
				if (r <= 12.5) {
					BlockState f;
					if (r > 11.4) {
						f = polished;
					} else if (r >= 5.6 && r <= 6.4) {
						f = amethyst;
					} else if (r <= 1.5) {
						f = Math.abs(x) + Math.abs(z) == 0 ? gold : chiseled;
					} else {
						f = hash(x, 1, z, 51) < 0.12 ? cracked : bricks;
					}
					set(level, o, c + x, 1, z, f);
				} else if (r <= 13.5) {
					set(level, o, c + x, 1, z, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, SlabType.BOTTOM));
				}
			}
		}
		// the claw sigil: three gold slashes across the centre, pointing at the statue
		for (int k = -3; k <= 3; k++) {
			for (int line = -1; line <= 1; line++) {
				int x = k, z = line * 2 + (int) Math.round(k * 0.25 * line);
				if (Math.abs(k) >= 2 || line != 0 || k != 0) {
					set(level, o, c + x - 1, 1, z, Math.abs(k) == 3 ? gilded : gold);
				}
			}
		}
		set(level, o, c, 1, 0, gold); // (the marker the build checks)
		// eight pillars, none on the east-west axis (the entrance and the statue)
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(22.5 + 45 * i);
			int px = (int) Math.round(Math.cos(a) * PlaneGeometry.PILLAR_R);
			int pz = (int) Math.round(Math.sin(a) * PlaneGeometry.PILLAR_R);
			pillar(level, o, c + px, pz);
		}
		// the crescent crown over the pillars, open behind the statue
		int top = 2 + PlaneGeometry.PILLAR_H;
		for (int x = -13; x <= 13; x++) {
			for (int z = -13; z <= 13; z++) {
				double r = Math.hypot(x, z);
				double ang = Math.toDegrees(Math.atan2(z, x));
				if (r >= 9.8 && r <= 12.3 && Math.abs(ang) > 38) {
					set(level, o, c + x, top, z, r > 11.8 ? gilded : bricks);
					if (r > 11.4) {
						set(level, o, c + x, top + 1, z, slab(Blocks.POLISHED_BLACKSTONE_SLAB, SlabType.BOTTOM));
					}
				}
			}
		}
		// braziers either side of the entrance (west)
		for (int side = -1; side <= 1; side += 2) {
			int bx = c - 13, bz = side * 4;
			set(level, o, bx, 1, bz, bricks);
			set(level, o, bx, 2, bz, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
			set(level, o, bx, 3, bz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}
		// the altar before the statue
		int ax = c + PlaneGeometry.ALTAR_DX;
		for (int z = -1; z <= 1; z++) {
			set(level, o, ax, 2, z, chiseled);
		}
		set(level, o, ax, 3, 0, Blocks.PURPLE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
		set(level, o, ax, 3, -1, Blocks.AMETHYST_CLUSTER.defaultBlockState());
		set(level, o, ax, 3, 1, Blocks.AMETHYST_CLUSTER.defaultBlockState());
		// the pedestal and the statue
		int px0 = c + PEDESTAL_DX - 6, px1 = c + PEDESTAL_DX + 6;
		for (int x = px0; x <= px1; x++) {
			for (int z = -5; z <= 5; z++) {
				set(level, o, x, 2, z, bricks);
				boolean edge = x == px0 || x == px1 || Math.abs(z) == 5;
				set(level, o, x, 3, z, edge ? gilded : polished);
			}
		}
		set(level, o, px0, 2, 0, chiseled);
		set(level, o, px0, 2, -2, gold);
		set(level, o, px0, 2, 2, gold);
		statue(level, o);
		eyes(level, o, false);
	}

	private static void pillar(ServerLevel level, BlockPos o, int x, int z) {
		int h = PlaneGeometry.PILLAR_H;
		BlockState shaft = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState base = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		for (int y = 2; y < 2 + h; y++) {
			int k = y - 2;
			set(level, o, x, y, z, k == 4 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : shaft);
			if (k <= 1 || k >= h - 2) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockState arm = k <= 1 ? base : Blocks.GILDED_BLACKSTONE.defaultBlockState();
					set(level, o, x + d.getStepX(), y, z + d.getStepZ(), arm);
				}
			}
		}
		set(level, o, x, 2 + h - 1, z, Blocks.GOLD_BLOCK.defaultBlockState());
		set(level, o, x, 2 + h, z, Blocks.PURPLE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
		// amethyst growing at the foot, toward the centre
		int ix = x - SHRINE_X;
		int sx = -Integer.signum(ix), sz = -Integer.signum(z);
		if (sx != 0 && sz != 0) {
			set(level, o, x + sx, 2, z + sz, Blocks.AMETHYST_CLUSTER.defaultBlockState());
		}
	}

	/**
	 * The seated black panther, facing west over the altar: haunches, chest, neck, head with muzzle and ears, straight front
	 * legs and paws, hind feet tucked, the tail curled round the front, a gold necklace across the chest. Built voxel by voxel
	 * from ellipsoids in the statue's own frame (a = forward/west of the pedestal centre, h = up from the pedestal top, s = z).
	 */
	private static void statue(ServerLevel level, BlockPos o) {
		int tx = SHRINE_X + PEDESTAL_DX;
		int ty = 4;
		double k = PlaneGeometry.STATUE_SCALE;
		BlockState body = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState dark = Blocks.BLACKSTONE.defaultBlockState();
		BlockState sleek = Blocks.BLACK_CONCRETE.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		for (int a = -9; a <= 8; a++) {
			for (int hh = 0; hh <= 15; hh++) {
				for (int s = -6; s <= 6; s++) {
					double la = a / k, h = (hh + 0.5) / k, ls = s / k;
					if (!statueVoxel(la, h, ls)) {
						continue;
					}
					BlockState st = body;
					boolean front = !statueVoxel((a + 1) / k, h, ls);
					boolean top = !statueVoxel(la, (hh + 1.5) / k, ls);
					if (h > 4.7 && h < 5.5 && la >= 0 && front) {
						st = gold; // the necklace, across the front of the chest
					} else if (top && h > 3.0) {
						st = sleek; // the top of the head, the back and the haunches: smooth black
					} else if (h < 0.9) {
						st = dark;
					}
					set(level, o, tx - a, ty + hh, s, st);
				}
			}
		}
		// the pendant: on the front of the chest under the necklace
		int ph = (int) Math.round(4.2 * k - 0.5);
		for (int a = 8; a >= -2; a--) {
			if (statueVoxel(a / k, (ph + 0.5) / k, 0)) {
				set(level, o, tx - a - 1, ty + ph, 0, gold);
				break;
			}
		}
	}

	private static boolean ell(double a, double h, double s, double ca, double ch, double cs, double ra, double rh, double rs) {
		double x = (a - ca) / ra, y = (h - ch) / rh, z = (s - cs) / rs;
		return x * x + y * y + z * z <= 1.0;
	}

	static boolean statueVoxel(double a, double h, double s) {
		if (ell(a, h, s, -1.6, 1.9, 0, 2.8, 2.2, 2.4)) {
			return true; // haunches
		}
		if (ell(a, h, s, 0.3, 3.8, 0, 1.8, 2.7, 1.85)) {
			return true; // chest and belly
		}
		if (ell(a, h, s, 0.9, 5.8, 0, 1.35, 1.7, 1.3)) {
			return true; // neck
		}
		if (ell(a, h, s, 1.6, 7.6, 0, 1.75, 1.45, 1.6)) {
			return true; // head
		}
		if (a >= 2.6 && a <= 4.0 && h >= 6.3 && h <= 7.5 && Math.abs(s) <= 0.85) {
			return true; // muzzle
		}
		if ((a >= 0.6 && a <= 1.7) && h >= 8.6 && h <= 10.3 - Math.abs(Math.abs(s) - 1.1) * 1.6 && Math.abs(Math.abs(s) - 1.1) <= 0.5) {
			return true; // ears
		}
		if (Math.hypot(a - 1.8, Math.abs(s) - 1.0) <= 0.75 && h <= 4.4) {
			return true; // front legs
		}
		if (a >= 1.5 && a <= 3.0 && h <= 1.0 && Math.abs(s) >= 0.3 && Math.abs(s) <= 1.7) {
			return true; // front paws
		}
		if (ell(a, h, Math.abs(s), -0.5, 0.5, 2.0, 1.5, 0.7, 0.75)) {
			return true; // hind feet
		}
		// the tail, curled round the right side to the front paws
		for (double t = 0; t <= 1.0; t += 0.05) {
			double ta = -4.2 + 5.6 * t, ts = 1.4 + 1.3 * Math.sin(t * Math.PI * 0.5), th = 0.5 + 0.9 * t * t * t;
			if (Math.abs(a - ta) <= 0.55 && Math.abs(s - ts) <= 0.55 && Math.abs(h - th) <= 0.55) {
				return true;
			}
		}
		return false;
	}

	/** The statue's eyes: obsidian while she sleeps, crying obsidian once Bast wakes them. */
	public static void eyes(ServerLevel level, BlockPos o, boolean lit) {
		BlockState s = lit ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
		for (int side = -1; side <= 1; side += 2) {
			BlockPos e = eyePos(o, side);
			level.setBlock(e, s, FLAGS);
		}
	}

	/** Where the statue's eye on {@code side} (-1 / 1) is: the front surface of the head, above the muzzle. */
	public static BlockPos eyePos(BlockPos o, int side) {
		double k = PlaneGeometry.STATUE_SCALE;
		int tx = SHRINE_X + PEDESTAL_DX, ty = 4;
		int hh = (int) Math.round(8.0 * k - 0.5);
		int s = side * (int) Math.round(0.85 * k);
		for (int a = 8; a >= -2; a--) {
			if (statueVoxel(a / k, (hh + 0.5) / k, s / k)) {
				return o.offset(tx - a, ty + hh, s);
			}
		}
		return o.offset(tx - 2, ty + hh, s);
	}

	// ---------------------------------------------------------------- the spirit path

	/** Builds the path's platforms whose start is at or before course x {@code upTo} (idempotent). */
	public static void buildPath(ServerLevel level, BlockPos o, double upTo) {
		List<ParkourCourse.Platform> all = ParkourCourse.platforms();
		for (int i = 0; i < all.size(); i++) {
			ParkourCourse.Platform p = all.get(i);
			if (p.x0() > upTo) {
				break;
			}
			if (!level.getBlockState(o.offset(PATH_X + p.x0(), PATH_BASE + p.y(), p.z0())).isAir()) {
				continue; // built already
			}
			platform(level, o, p, i);
		}
	}

	/** Takes the path away (a new visit starts without it) and puts the statue's eyes back to sleep. */
	public static void resetForVisit(ServerLevel level, BlockPos o) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (ParkourCourse.Platform p : ParkourCourse.platforms()) {
			for (int x = p.x0() - 1; x < p.x0() + p.sx() + 1; x++) {
				for (int z = p.z0() - 1; z < p.z0() + p.sz() + 1; z++) {
					for (int y = p.y() - 4; y <= p.y() + 3; y++) {
						set(level, o, PATH_X + x, PATH_BASE + y, z, air);
					}
				}
			}
		}
		eyes(level, o, false);
	}

	private static void platform(ServerLevel level, BlockPos o, ParkourCourse.Platform p, int index) {
		int bx = PATH_X, by = PATH_BASE;
		BlockState top;
		BlockState under = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		switch (p.kind()) {
			case ACACIA -> top = Blocks.ACACIA_PLANKS.defaultBlockState();
			case AMETHYST -> top = Blocks.AMETHYST_BLOCK.defaultBlockState();
			case BEAM -> top = Blocks.STRIPPED_ACACIA_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
			case TOTEM -> top = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
			default -> top = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		for (int x = p.x0(); x < p.x0() + p.sx(); x++) {
			for (int z = p.z0(); z < p.z0() + p.sz(); z++) {
				BlockState t = top;
				if (p.kind() == ParkourCourse.Kind.CHECKPOINT || p.kind() == ParkourCourse.Kind.FINISH) {
					boolean centre = x == p.x0() + p.sx() / 2 && z == p.z0() + p.sz() / 2;
					boolean ring = x == p.x0() || z == p.z0() || x == p.x0() + p.sx() - 1 || z == p.z0() + p.sz() - 1;
					t = centre ? Blocks.GOLD_BLOCK.defaultBlockState() : ring ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : top;
				}
				set(level, o, bx + x, by + p.y(), z, t);
				// a tapering underside, so they read as floating stones rather than slabs
				if (p.kind() != ParkourCourse.Kind.BEAM) {
					boolean inner = x > p.x0() && x < p.x0() + p.sx() - 1 && z > p.z0() && z < p.z0() + p.sz() - 1;
					if (inner || p.sx() * p.sz() <= 4) {
						set(level, o, bx + x, by + p.y() - 1, z, under);
					}
				}
			}
		}
		if (p.kind() == ParkourCourse.Kind.TOTEM) {
			// a short panther totem hanging under the stepping stone: carved stone, gold band, amethyst at the tip
			set(level, o, bx + p.x0(), by + p.y() - 1, p.z0(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
			set(level, o, bx + p.x0(), by + p.y() - 2, p.z0(), Blocks.GILDED_BLACKSTONE.defaultBlockState());
			set(level, o, bx + p.x0(), by + p.y() - 3, p.z0(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		}
		if (p.kind() == ParkourCourse.Kind.CHECKPOINT || p.kind() == ParkourCourse.Kind.FINISH) {
			// the checkpoint's marker: purple candles on the far corners, an amethyst crystal hanging below
			int cx = p.x0() + p.sx() / 2, cz = p.z0() + p.sz() / 2;
			set(level, o, bx + cx, by + p.y() - 2, cz, Blocks.AMETHYST_BLOCK.defaultBlockState());
			set(level, o, bx + cx, by + p.y() - 3, cz, Blocks.AMETHYST_CLUSTER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.DOWN));
			BlockState candle = Blocks.PURPLE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true);
			set(level, o, bx + p.x0() + p.sx() - 1, by + p.y() + 1, p.z0(), candle);
			set(level, o, bx + p.x0() + p.sx() - 1, by + p.y() + 1, p.z0() + p.sz() - 1, candle);
			if (p.kind() == ParkourCourse.Kind.FINISH) {
				// a panther head of gold and stone at the far edge, gazing at the sun
				int fx = bx + p.x0() + p.sx() - 1, fy = by + p.y() + 1;
				for (int z = cz - 1; z <= cz + 1; z++) {
					set(level, o, fx, fy, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
					set(level, o, fx, fy + 1, z, z == cz ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState());
				}
				set(level, o, fx, fy + 2, cz - 1, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
				set(level, o, fx, fy + 2, cz + 1, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
			}
		} else if (p.kind() == ParkourCourse.Kind.AMETHYST && index % 2 == 0) {
			set(level, o, bx + p.x0(), by + p.y() - 1, p.z0(), Blocks.AMETHYST_CLUSTER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.DOWN));
		}
	}
}
