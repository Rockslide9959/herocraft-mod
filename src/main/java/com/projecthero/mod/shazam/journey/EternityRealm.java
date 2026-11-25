package com.projecthero.mod.shazam.journey;

import static com.projecthero.mod.shazam.journey.JourneyGeometry.CAVE_H;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.CAVE_RX;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.CAVE_RZ;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.CAVE_WEST;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.CAVE_X;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.DAIS_X;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.MAGIC_X;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.ROCK_X;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.STATION_HALF;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.THRONE_X;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.TRACK_END;
import static com.projecthero.mod.shazam.journey.JourneyGeometry.WEST_END;

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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: <b>the pocket realm</b> -- {@code projecthero:rock_of_eternity}, a void datapack dimension (no
 * sky light, a stone ceiling, fixed night, no weather, no spawns) holding, per journey <b>slot</b> ({@link #SPACING} blocks
 * apart along Z, so two journeys never meet), the whole route built out of ordinary blocks:
 * <ul>
 * <li><b>Fawcett Central</b>, a dim old station: grimy white tiles over a brown wainscot, a cyan mosaic band, oxidised copper
 * columns, ceiling lamps (two of them flicker, {@link #flicker}), an empty platform with a yellow edge, benches, the
 * timetable and the station's name on signs, and the stairs the player "came down";</li>
 * <li>the west tunnel the train arrives from, and the east tunnel it leaves by: tiled subway tunnel with a lamp every five
 * blocks on both walls (the streaks), then glyph-carved blackstone, then raw rock;</li>
 * <li><b>the Rock of Eternity</b>: a vast domed cavern (80 x 44, 28 high) with a paved floor, the end of the line, a red
 * carpet to a three-step dais and a stone throne, the Seven Deadly Sins along the walls on plinths with glowing eyes and their
 * names, braziers, glowing runes round the walls and a great lightning-bolt rune behind the throne.</li>
 * </ul>
 * Built once per slot, in slices spread over a few seconds ({@link #queue}, {@link #tick}), then reused (a marker block under
 * the station). Nothing in the realm can be broken or placed ({@code ShazamJourney} guards it). <b>Fallback:</b> when the
 * dimension is missing (the GameTest server never loads datapack dimensions) the same build goes high in the overworld sky at
 * {@link #FALLBACK_X}, so the journey still works end to end.
 */
public final class EternityRealm {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectHeroMod.id("rock_of_eternity"));
	public static final int SPACING = 1000;
	public static final int BASE_Y = 64;
	public static final int FALLBACK_X = 1_200_000;
	public static final int FALLBACK_Y = 200;
	/** Slices of this many x columns are built per piece; at most {@link #PIECES_PER_TICK} pieces per tick. */
	private static final int SLICE = 3;
	private static final int PIECES_PER_TICK = 4;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final TicketType<ChunkPos> TICKET = TicketType.create("projecthero_rock_of_eternity", Comparator.comparingLong(ChunkPos::toLong), 60);

	/** Builds in progress: (dimension, slot) -> next piece. No worlds held; cleared by {@code ServerStateReset}. */
	private static final Map<String, Build> BUILDS = new ConcurrentHashMap<>();

	private static final class Build {
		final ResourceKey<Level> dimension;
		final int slot;
		final List<Runnable> pieces = new ArrayList<>();
		int next;

		Build(ResourceKey<Level> dimension, int slot) {
			this.dimension = dimension;
			this.slot = slot;
		}
	}

	private EternityRealm() {
	}

	public static void clearSessionState() {
		BUILDS.clear();
	}

	// ---------------------------------------------------------------- where

	/** The realm's level: the dimension, or the overworld when the dimension isn't loaded (the GameTest server). */
	public static ServerLevel level(MinecraftServer server) {
		ServerLevel l = server.getLevel(DIMENSION);
		return l != null ? l : server.overworld();
	}

	public static boolean isFallback(ServerLevel level) {
		return level.dimension() != DIMENSION;
	}

	/** Slot {@code slot}'s origin (the station's stop point on the rail layer) in {@code level}. */
	public static BlockPos origin(ServerLevel level, int slot) {
		return isFallback(level) ? new BlockPos(FALLBACK_X, FALLBACK_Y, slot * SPACING) : new BlockPos(0, BASE_Y, slot * SPACING);
	}

	/** Is {@code pos} inside slot {@code slot}'s build box (the realm proper, or its fallback box in the overworld)? */
	public static boolean inBox(ServerLevel level, int slot, Vec3 pos) {
		BlockPos o = origin(level, slot);
		return pos.x >= o.getX() + WEST_END - 4 && pos.x <= o.getX() + CAVE_X + CAVE_RX + 6 && Math.abs(pos.z - o.getZ()) <= CAVE_RZ + 6
				&& pos.y >= o.getY() - 12 && pos.y <= o.getY() + CAVE_H + 10;
	}

	/** Any slot's box (used by the block guard in the fallback overworld). */
	public static boolean inAnyBox(ServerLevel level, Vec3 pos) {
		if (!isFallback(level)) {
			return true;
		}
		int slot = (int) Math.round(pos.z / SPACING);
		return slot >= 0 && Math.abs(pos.x - FALLBACK_X) < 1000 && inBox(level, slot, pos);
	}

	public static int slotOf(Vec3 pos) {
		return Math.max(0, (int) Math.round(pos.z / SPACING));
	}

	private static BlockPos marker(BlockPos o) {
		return o.offset(0, -8, 0);
	}

	public static boolean isBuilt(ServerLevel level, int slot) {
		return level.getBlockState(marker(origin(level, slot))).is(Blocks.BEDROCK);
	}

	public static boolean isBuilding(ServerLevel level, int slot) {
		return BUILDS.containsKey(key(level.dimension(), slot));
	}

	private static String key(ResourceKey<Level> dim, int slot) {
		return dim.location() + "#" + slot;
	}

	/**
	 * Keeps slot {@code slot}'s whole route loaded and <b>entity-ticking</b> for a few seconds (re-issued every second by the
	 * running journey): a ticket every two chunks along the track, each keeping the chunks either side of it ticking, so the train
	 * never runs into a frozen chunk and the Wizard's throne is always live.
	 */
	public static void hold(ServerLevel level, int slot) {
		BlockPos o = origin(level, slot);
		for (int x = WEST_END - 8; x <= CAVE_X + CAVE_RX + 16; x += 32) {
			ChunkPos c = new ChunkPos(o.offset(x, 0, 0));
			level.getChunkSource().addRegionTicket(TICKET, c, 3, c);
		}
	}

	/** Is the track at x offset {@code dx} in an entity-ticking chunk right now? */
	public static boolean live(ServerLevel level, int slot, double dx) {
		return level.isPositionEntityTicking(BlockPos.containing(JourneyGeometry.track(origin(level, slot), dx)));
	}

	// ---------------------------------------------------------------- building

	/** Starts building slot {@code slot} (if it isn't built or being built). */
	public static void queue(ServerLevel level, int slot) {
		if (isBuilt(level, slot) || isBuilding(level, slot)) {
			return;
		}
		Build b = new Build(level.dimension(), slot);
		BlockPos o = origin(level, slot);
		// the station first (they land there), then east along the ride, then the west tunnel, then the cave's decorations
		for (int x = -STATION_HALF - 2; x <= STATION_HALF + 2; x += SLICE) {
			final int x0 = x;
			b.pieces.add(() -> slice(level, o, x0, x0 + SLICE));
		}
		for (int x = STATION_HALF + 3; x <= CAVE_X + CAVE_RX + 4; x += SLICE) {
			final int x0 = x;
			b.pieces.add(() -> slice(level, o, x0, x0 + SLICE));
		}
		for (int x = -STATION_HALF - 3 - SLICE; x >= WEST_END - SLICE - 1; x -= SLICE) {
			final int x0 = x;
			b.pieces.add(() -> slice(level, o, x0 + 1, x0 + 1 + SLICE));
		}
		b.pieces.add(() -> stationDetails(level, o));
		b.pieces.add(() -> caveDetails(level, o));
		b.pieces.add(() -> wallRunes(level, o));
		b.pieces.add(() -> level.setBlock(marker(o), Blocks.BEDROCK.defaultBlockState(), FLAGS));
		BUILDS.put(key(level.dimension(), slot), b);
	}

	/** Builds slot {@code slot} right now, whole (tests, and the fallback when time is short). */
	public static void buildNow(ServerLevel level, int slot) {
		queue(level, slot);
		Build b = BUILDS.remove(key(level.dimension(), slot));
		if (b != null) {
			while (b.next < b.pieces.size()) {
				b.pieces.get(b.next++).run();
			}
		}
	}

	/** True once the station (the first pieces) is standing, so a player can land there. */
	public static boolean stationReady(ServerLevel level, int slot) {
		if (isBuilt(level, slot)) {
			return true;
		}
		Build b = BUILDS.get(key(level.dimension(), slot));
		return b != null && b.next >= (2 * STATION_HALF + 5) / SLICE + 1;
	}

	/** Every tick: a few pieces of every build in progress. */
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
			// a few pieces, within about 15 ms (at least one)
			long until = System.nanoTime() + 15_000_000L;
			for (int i = 0; i < PIECES_PER_TICK && b.next < b.pieces.size() && (i == 0 || System.nanoTime() < until); i++) {
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

	/** Smooth value noise, about -1..1, with a feature size of {@code scale} blocks. */
	static double noise(double x, double z, double scale, int salt) {
		double fx = x / scale, fz = z / scale;
		int ix = Mth.floor(fx), iz = Mth.floor(fz);
		double tx = fx - ix, tz = fz - iz;
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		double a = hash(ix, 0, iz, salt), b = hash(ix + 1, 0, iz, salt), c = hash(ix, 0, iz + 1, salt), d = hash(ix + 1, 0, iz + 1, salt);
		return (Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d)) * 2.0) - 1.0;
	}

	private static BlockState rock(int x, int y, int z) {
		double h = hash(x, y, z, 11);
		if (h < 0.42) {
			return Blocks.STONE.defaultBlockState();
		}
		if (h < 0.58) {
			return Blocks.DEEPSLATE.defaultBlockState();
		}
		if (h < 0.72) {
			return Blocks.ANDESITE.defaultBlockState();
		}
		if (h < 0.84) {
			return Blocks.TUFF.defaultBlockState();
		}
		if (h < 0.94) {
			return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	private static BlockState tunnelBrick(int x, int y, int z) {
		double h = hash(x, y, z, 5);
		return h < 0.68 ? Blocks.STONE_BRICKS.defaultBlockState()
				: h < 0.84 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
						: h < 0.95 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
	}

	private static BlockState rail() {
		return Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
	}

	private static BlockState lamp() {
		return Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
	}

	/** The trench the rails run in: gravel ballast, spruce sleepers every other block, the rail on top. */
	private static void trackBed(ServerLevel level, BlockPos o, int x, int z0, int z1) {
		for (int z = z0; z <= z1; z++) {
			boolean sleeper = (x & 1) == 0 && z >= -1 && z <= 1;
			set(level, o, x, -1, z, sleeper ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
			set(level, o, x, -2, z, Blocks.STONE.defaultBlockState());
		}
		set(level, o, x, 0, 0, rail());
	}

	// ---------------------------------------------------------------- slices

	/** Everything with an x offset in [x0, x1). */
	static void slice(ServerLevel level, BlockPos o, int x0, int x1) {
		for (int x = x0; x < x1; x++) {
			if (x < WEST_END || x > CAVE_X + CAVE_RX + 4) {
				continue;
			}
			if (Math.abs(x) <= STATION_HALF + 1) {
				station(level, o, x);
			} else if (x < 0) {
				tunnel(level, o, x, x == WEST_END);
			} else if (x < ROCK_X) {
				tunnel(level, o, x, false);
			} else if (x < CAVE_WEST + 4) {
				rockTunnel(level, o, x);
			}
			if (x >= CAVE_X - CAVE_RX - 4) {
				cave(level, o, x);
			}
		}
	}

	// ---------------------------------------------------------------- the station

	private static BlockState wallTile(int x, int dy, int z) {
		if (dy <= 0) {
			return Blocks.STONE_BRICKS.defaultBlockState();
		}
		if (dy == 1) {
			return Blocks.TERRACOTTA.defaultBlockState();
		}
		if (dy == 4) {
			return (Math.floorMod(x, 4) == 0) ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.CYAN_TERRACOTTA.defaultBlockState();
		}
		double g = hash(x, dy, z, 7);
		return g < 0.12 ? Blocks.CALCITE.defaultBlockState() : g < 0.17 ? Blocks.DIORITE.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState();
	}

	private static void station(ServerLevel level, BlockPos o, int x) {
		boolean end = Math.abs(x) == STATION_HALF + 1;
		for (int z = -5; z <= 9; z++) {
			set(level, o, x, -2, z, Blocks.STONE.defaultBlockState());
			for (int dy = -1; dy <= 7; dy++) {
				BlockState s = Blocks.AIR.defaultBlockState();
				boolean wall = z == -5 || z == 9 || dy == -1 || dy == 7;
				if (wall) {
					s = dy == 7 ? Blocks.SMOOTH_STONE.defaultBlockState() : wallTile(x, dy, z);
					if (dy == -1) {
						s = Blocks.STONE_BRICKS.defaultBlockState();
					}
				}
				if (end && !(z >= -2 && z <= 3 && dy >= 0 && dy <= 4)) {
					s = wallTile(x, dy, z); // the end wall round the tunnel mouth
				}
				set(level, o, x, dy, z, s);
			}
		}
		if (end) {
			trackBed(level, o, x, -2, 3);
			return;
		}
		// the trench (z -4..1) and the platform (z 2..8, its top at O.y + 1)
		trackBed(level, o, x, -4, 1);
		for (int z = 2; z <= 8; z++) {
			BlockState top;
			if (z == 2) {
				top = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
			} else {
				double g = hash(x, 0, z, 3);
				top = g < 0.05 ? Blocks.ANDESITE.defaultBlockState() : ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
						: Blocks.SMOOTH_STONE.defaultBlockState();
			}
			set(level, o, x, 0, z, top);
			set(level, o, x, -1, z, Blocks.STONE_BRICKS.defaultBlockState());
		}
		// the walls: the back wall behind the platform (z 9) and the wall over the trench (z -5), tiled; the ceiling with lamps
		for (int dy = 0; dy <= 6; dy++) {
			set(level, o, x, dy, 9, wallTile(x, dy, 9));
			set(level, o, x, dy, -5, wallTile(x, dy, -5));
		}
		if (Math.floorMod(x, 6) == 0) {
			set(level, o, x, 7, 5, lamp());
			set(level, o, x, 7, -1, lamp());
		}
		// oxidised copper columns along the platform
		if (Math.floorMod(x, 8) == 4 && Math.abs(x) < STATION_HALF - 1) {
			for (int dy = 1; dy <= 6; dy++) {
				set(level, o, x, dy, 4, Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
			}
		}
		// grime: a few cobwebs up in the corners
		if (hash(x, 0, 0, 21) < 0.12) {
			set(level, o, x, 6, 8, Blocks.COBWEB.defaultBlockState());
		}
		if (hash(x, 1, 0, 21) < 0.08) {
			set(level, o, x, 6, -4, Blocks.COBWEB.defaultBlockState());
		}
	}

	/** Stairs "up" at the east end of the platform (where the player lands), benches, the signs. */
	private static void stationDetails(ServerLevel level, BlockPos o) {
		// the stairwell: steps rising east over x 17..22 between walls at z 3 and 7, into the dark
		for (int x = 16; x <= 24; x++) {
			for (int z = 3; z <= 8; z++) {
				for (int dy = 1; dy <= 12; dy++) {
					boolean side = z == 3 || z == 8;
					int k = x - 17;
					BlockState s = Blocks.AIR.defaultBlockState();
					if (side && dy <= 12 && x >= 17) {
						s = wallTile(x, Math.min(dy, 6), z);
					} else if (x >= 17 && k >= 0 && dy <= k) {
						s = Blocks.STONE_BRICKS.defaultBlockState(); // under the steps
					} else if (x >= 17 && k >= 0 && dy == k + 1) {
						s = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM);
					}
					if (dy == 12 || x == 24) {
						s = Blocks.BLACK_CONCRETE.defaultBlockState();
					}
					if (x == 16 && dy >= 7) {
						s = Blocks.SMOOTH_STONE.defaultBlockState(); // the lintel over the stair mouth
					}
					if (x == 16 && dy < 7) {
						continue;
					}
					set(level, o, x, dy, z, s);
				}
			}
		}
		set(level, o, 18, 11, 5, lamp());
		// benches against the back wall
		for (int x : new int[] { -15, -14, -13, 6, 7, 8 }) {
			set(level, o, x, 1, 8, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		}
		// the timetable: a dark oak board on the back wall, three signs
		for (int x = -9; x <= -5; x++) {
			for (int dy = 2; dy <= 4; dy++) {
				set(level, o, x, dy, 9, Blocks.DARK_OAK_PLANKS.defaultBlockState());
			}
		}
		sign(level, o.offset(-8, 3, 8), Direction.NORTH, "timetable");
		sign(level, o.offset(-7, 3, 8), Direction.NORTH, "next");
		sign(level, o.offset(-6, 3, 8), Direction.NORTH, "last");
		// the station's name across the track, framed in the mosaic
		for (int x = -3; x <= 2; x++) {
			for (int dy = 2; dy <= 4; dy++) {
				set(level, o, x, dy, -5, Blocks.BLACK_TERRACOTTA.defaultBlockState());
			}
		}
		sign(level, o.offset(-1, 3, -4), Direction.SOUTH, "name_left");
		sign(level, o.offset(0, 3, -4), Direction.SOUTH, "name_right");
		sign(level, o.offset(-12, 3, 8), Direction.NORTH, "mind_gap");
	}

	/** A wall sign at {@code pos} facing {@code facing}, its four lines {@code sign.projecthero.journey.<id>.1..4}. */
	private static void sign(ServerLevel level, BlockPos pos, Direction facing, String id) {
		level.setBlock(pos, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing), FLAGS);
		if (level.getBlockEntity(pos) instanceof SignBlockEntity be) {
			SignText text = new SignText();
			for (int i = 0; i < 4; i++) {
				text = text.setMessage(i, Component.translatable("sign.projecthero.journey." + id + "." + (i + 1)));
			}
			text = text.setColor(net.minecraft.world.item.DyeColor.WHITE).setHasGlowingText(true);
			be.setText(text, true);
			be.setWaxed(true);
			be.setChanged();
			level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS);
		}
	}

	// ---------------------------------------------------------------- the tunnels

	private static void tunnel(ServerLevel level, BlockPos o, int x, boolean endWall) {
		boolean magic = x >= MAGIC_X;
		for (int z = -3; z <= 4; z++) {
			for (int dy = -2; dy <= 5; dy++) {
				boolean shell = z == -3 || z == 4 || dy == 5 || dy <= -1;
				BlockState s = Blocks.AIR.defaultBlockState();
				if (shell || endWall) {
					s = magic ? magicWall(x, dy, z) : tunnelBrick(x, dy, z);
					if (dy == 5 && !magic) {
						s = Blocks.STONE.defaultBlockState();
					}
				}
				set(level, o, x, dy, z, s);
			}
		}
		if (endWall) {
			return;
		}
		trackBed(level, o, x, -2, 3);
		if (!magic) {
			// the lamps that streak past the windows
			if (Math.floorMod(x, 5) == 0) {
				set(level, o, x, 2, 4, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
				set(level, o, x, 3, -3, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
			}
			if (Math.floorMod(x, 20) == 10) {
				set(level, o, x, 4, 4, Blocks.SEA_LANTERN.defaultBlockState()); // a signal-ish blue light now and then
			}
		} else {
			glyphs(level, o, x);
		}
	}

	private static BlockState magicWall(int x, int dy, int z) {
		double into = Mth.clamp((x - MAGIC_X) / 18.0, 0.0, 1.0);
		double h = hash(x, dy, z, 9);
		if (h > into) {
			return tunnelBrick(x, dy, z);
		}
		double g = hash(x, dy, z, 10);
		if (x > ROCK_X - 22 && g < (x - (ROCK_X - 22)) / 22.0) {
			return rock(x, dy, z); // turning to raw rock
		}
		return g < 0.08 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
				: g < 0.3 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}

	/** 3x3 glyphs (bit masks, row by row from the top). */
	private static final int[] GLYPHS = { 0b010_111_010, 0b101_010_101, 0b111_100_111, 0b110_011_110, 0b011_110_011, 0b101_111_101,
			0b100_111_001, 0b111_010_111 };

	private static void glyphs(ServerLevel level, BlockPos o, int x) {
		int period = 6;
		int col = Math.floorMod(x, period);
		if (col > 2) {
			return;
		}
		int idx = Math.floorMod(Math.floorDiv(x, period), GLYPHS.length);
		int bits = GLYPHS[idx];
		for (int row = 0; row < 3; row++) {
			boolean on = ((bits >> (8 - (row * 3 + col))) & 1) != 0;
			if (on) {
				set(level, o, x, 3 - row, 4, Blocks.GLOWSTONE.defaultBlockState());
				set(level, o, x, 3 - row, -3, Blocks.GLOWSTONE.defaultBlockState());
			}
		}
	}

	/** Air inside the raw rock tunnel: the train's clearance, plus a rough widening bore. */
	private static boolean rockInside(int x, int dy, int z) {
		if (z >= -2 && z <= 3 && dy >= 0 && dy <= 4) {
			return true;
		}
		double widen = Mth.clamp((x - ROCK_X) / 30.0, 0.0, 1.0);
		double rz = 3.0 + 1.6 * widen + 0.9 * noise(x, dy, 5.0, 31);
		double ry = 3.0 + 1.4 * widen + 0.8 * noise(x, z, 6.0, 32);
		double ez = (z - 0.5) / rz, ey = (dy - 2.0) / ry;
		return dy >= 0 && ez * ez + ey * ey <= 1.0;
	}

	private static void rockTunnel(ServerLevel level, BlockPos o, int x) {
		for (int z = -7; z <= 8; z++) {
			for (int dy = -2; dy <= 9; dy++) {
				boolean in = rockInside(x, dy, z);
				if (in) {
					set(level, o, x, dy, z, Blocks.AIR.defaultBlockState());
					continue;
				}
				boolean shell = rockInside(x, dy + 1, z) || rockInside(x, dy - 1, z) || rockInside(x, dy, z + 1) || rockInside(x, dy, z - 1)
						|| rockInside(x + 1, dy, z) || rockInside(x - 1, dy, z) || dy <= -1 && z >= -3 && z <= 4;
				if (shell) {
					set(level, o, x, dy, z, rock(o.getX() + x, dy, z));
				}
			}
		}
		trackBed(level, o, x, -2, 3);
		if (Math.floorMod(x, 11) == 3) {
			set(level, o, x, 4, hash(x, 0, 0, 40) < 0.5 ? 5 : -4, Blocks.SHROOMLIGHT.defaultBlockState());
		}
		if (Math.floorMod(x, 7) == 2 && x < CAVE_WEST - 30) {
			set(level, o, x, 2, 4, Blocks.GLOWSTONE.defaultBlockState()); // the last glyph stones, fewer and fewer
		}
	}

	// ---------------------------------------------------------------- the cave

	/** The cave's ellipse value at (x, z) (offsets), roughened; <= 1 inside. */
	static double caveE(int x, int z) {
		double ex = (x - CAVE_X) / (double) CAVE_RX, ez = z / (double) CAVE_RZ;
		return (ex * ex + ez * ez) * (1.0 + 0.07 * noise(x, z, 7.0, 51));
	}

	/** The dome's height (blocks above the floor surface) at (x, z), or -1 outside. */
	static int caveHeight(int x, int z) {
		double e = caveE(x, z);
		if (e > 1.0) {
			return -1;
		}
		return 1 + (int) Math.round(CAVE_H * Math.sqrt(1.0 - e) + 1.6 * noise(x, z, 5.0, 52));
	}

	private static boolean portal(int x, int dy, int z) {
		return x <= CAVE_X - CAVE_RX + 10 && z >= -2 && z <= 3 && dy >= 0 && dy <= 5;
	}

	private static void cave(ServerLevel level, BlockPos o, int x) {
		for (int z = -CAVE_RZ - 4; z <= CAVE_RZ + 4; z++) {
			int h = caveHeight(x, z);
			if (h < 0) {
				// just outside: a thick wall base, up to the neighbours' dome
				int nb = Math.max(Math.max(caveHeight(x + 1, z), caveHeight(x - 1, z)), Math.max(caveHeight(x, z + 1), caveHeight(x, z - 1)));
				if (nb < 0) {
					continue;
				}
				for (int dy = -1; dy <= Math.max(nb, 3) + 1; dy++) {
					if (!portal(x, dy, z) && !(x < CAVE_WEST + 4 && rockInside(x, dy, z))) {
						set(level, o, x, dy, z, rock(o.getX() + x, dy, z));
					}
				}
				continue;
			}
			// the floor
			boolean trench = x <= TRACK_END - 1 && z >= -1 && z <= 1;
			boolean paved = Math.abs(z) <= 8 && x >= CAVE_WEST + 2;
			set(level, o, x, -2, z, Blocks.STONE.defaultBlockState());
			if (trench) {
				trackBed(level, o, x, -1, 1);
			} else {
				set(level, o, x, -1, z, rock(o.getX() + x, -1, z));
				BlockState floor = paved ? (((x + z) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState())
						: hash(x, 0, z, 53) < 0.15 ? Blocks.GRAVEL.defaultBlockState() : rock(o.getX() + x, 0, z);
				set(level, o, x, 0, z, floor);
			}
			// clear the air (a re-build over an old one) and lay the dome, thick enough to close any gap to its neighbours
			int top = h;
			for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
				int nh = caveHeight(x + d[0], z + d[1]);
				top = Math.max(top, nh < 0 ? h + 3 : nh + 1);
			}
			for (int dy = 1; dy < h; dy++) {
				set(level, o, x, dy, z, Blocks.AIR.defaultBlockState());
			}
			for (int dy = h; dy <= Math.max(top, h + 2); dy++) {
				if (!portal(x, dy, z)) {
					set(level, o, x, dy, z, rock(o.getX() + x, dy, z));
				}
			}
		}
	}

	/** The dais and throne, the carpet, the buffer stop, the Seven Deadly Sins, the braziers. */
	private static void caveDetails(ServerLevel level, BlockPos o) {
		// the buffer stop at the end of the line
		for (int z = -1; z <= 1; z++) {
			set(level, o, TRACK_END, 0, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			set(level, o, TRACK_END, 1, z, z == 0 ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		}
		// the carpet to the dais
		for (int x = TRACK_END + 1; x < DAIS_X; x++) {
			for (int z = -1; z <= 1; z++) {
				set(level, o, x, 1, z, Blocks.RED_CARPET.defaultBlockState());
			}
		}
		// the dais: three steps, polished deepslate edged in gold, stairs up the middle
		for (int step = 0; step < 3; step++) {
			int x0 = DAIS_X + step;
			int half = 6 - step;
			for (int x = x0; x <= THRONE_X + 5; x++) {
				for (int z = -half; z <= half; z++) {
					boolean edge = x == x0 || Math.abs(z) == half;
					set(level, o, x, 1 + step, z, edge && step == 2 && Math.abs(z) == half ? Blocks.GOLD_BLOCK.defaultBlockState()
							: edge && x == x0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				}
			}
			for (int z = -1; z <= 1; z++) {
				set(level, o, x0, 1 + step, z, Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
			}
		}
		// the throne: a seat, arms, a tall carved back crowned in gold
		int ty = 4;
		set(level, o, THRONE_X, ty, 0, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		set(level, o, THRONE_X, ty, -1, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		set(level, o, THRONE_X, ty, 1, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		for (int dy = 0; dy <= 4; dy++) {
			for (int z = -1; z <= 1; z++) {
				set(level, o, THRONE_X + 1, ty + dy, z, dy == 4 && z != 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
						: z == 0 && dy >= 1 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			}
		}
		set(level, o, THRONE_X + 1, ty + 5, 0, Blocks.GOLD_BLOCK.defaultBlockState());
		set(level, o, THRONE_X + 1, ty + 6, 0, Blocks.GLOWSTONE.defaultBlockState());
		// braziers either side of the dais
		for (int z : new int[] { -5, 5 }) {
			set(level, o, DAIS_X - 2, 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			set(level, o, DAIS_X - 2, 2, z, Blocks.CAMPFIRE.defaultBlockState());
		}
		// a dim, warm fill light through the cavern (invisible light blocks), brighter round the throne
		for (int x = CAVE_WEST + 6; x <= CAVE_X + CAVE_RX - 6; x += 7) {
			for (int z = -18; z <= 18; z += 7) {
				int h = caveHeight(x, z);
				BlockPos p = o.offset(x, 6, z);
				if (h > 9 && level.getBlockState(p).isAir()) {
					level.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 10), FLAGS);
				}
			}
		}
		for (int[] l : new int[][] { { THRONE_X - 3, 8, 0 }, { THRONE_X - 8, 7, 4 }, { THRONE_X - 8, 7, -4 }, { CAVE_WEST + 30, 5, 3 } }) {
			BlockPos p = o.offset(l[0], l[1], l[2]);
			if (level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 13), FLAGS);
			}
		}
		// the Seven Deadly Sins: four along the north wall, three along the south, facing the aisle
		String[] sins = { "pride", "envy", "greed", "wrath", "lust", "gluttony", "sloth" };
		int[][] at = { { 283, -13 }, { 292, -13 }, { 301, -13 }, { 310, -13 }, { 301, 13 }, { 310, 13 }, { 319, 12 } };
		for (int i = 0; i < sins.length; i++) {
			statue(level, o, at[i][0], at[i][1], i, sins[i]);
		}
	}

	/**
	 * One of the Sins: a 3 x 3 plinth, a hulking figure of dark stone (each its own shape), glowing eyes, a brazier in front and
	 * its name on a sign. {@code z} < 0 faces south (+z), else north.
	 */
	private static void statue(ServerLevel level, BlockPos o, int x, int z, int kind, String name) {
		int f = z < 0 ? 1 : -1; // toward the aisle
		BlockState plinth = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(level, o, x + dx, 1, z + dz, plinth);
				set(level, o, x + dx, 2, z + dz, dx == 0 && dz == f ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : plinth);
			}
		}
		BlockState body = kind % 2 == 0 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState dark = Blocks.BLACKSTONE.defaultBlockState();
		BlockState eye = kind == 3 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState();
		int base = 3;
		// legs, body, shoulders, head -- shaped per sin
		int height = switch (kind) {
			case 0 -> 7; // Pride: tall, chest out
			case 1 -> 5; // Envy: thin and hunched
			case 2 -> 5; // Greed: fat, clutching gold
			case 3 -> 6; // Wrath: arms raised, horns
			case 4 -> 6; // Lust: sinuous, a tail
			case 5 -> 4; // Gluttony: vast belly, gaping mouth
			default -> 3; // Sloth: slumped low
		};
		int torsoW = kind == 2 || kind == 5 ? 1 : 0; // half-width beyond the centre column
		for (int dy = 0; dy < height; dy++) {
			int y = base + dy;
			boolean head = dy >= height - 2;
			if (head) {
				set(level, o, x, y, z, body);
				set(level, o, x, y, z + f, dy == height - 1 ? body : dark);
				if (dy == height - 1) {
					set(level, o, x - 1, y, z + f, eye);
					set(level, o, x + 1, y, z + f, eye);
					set(level, o, x - 1, y, z, body);
					set(level, o, x + 1, y, z, body);
				}
				continue;
			}
			for (int dx = -1 - torsoW; dx <= 1 + torsoW; dx++) {
				boolean legGap = dy == 0 && dx == 0 && kind != 5 && kind != 6;
				if (!legGap && (Math.abs(dx) <= torsoW + (dy >= 1 ? 1 : 1))) {
					set(level, o, x + dx, y, z, body);
				}
			}
			if (torsoW > 0 && dy >= 1) {
				set(level, o, x, y, z + f, body); // the belly
			}
		}
		int top = base + height;
		switch (kind) {
			case 0 -> { // Pride's horns
				set(level, o, x - 1, top, z, dark);
				set(level, o, x + 1, top, z, dark);
			}
			case 2 -> { // Greed's gold
				set(level, o, x, base + 1, z + 2 * f, Blocks.GOLD_BLOCK.defaultBlockState());
			}
			case 3 -> { // Wrath's raised fists
				set(level, o, x - 2, top - 1, z, body);
				set(level, o, x + 2, top - 1, z, body);
				set(level, o, x - 2, top, z, dark);
				set(level, o, x + 2, top, z, dark);
			}
			case 4 -> { // Lust's tail
				set(level, o, x, base, z - f, body);
				set(level, o, x + 1, base, z - f, dark);
			}
			case 5 -> { // Gluttony's open mouth
				set(level, o, x, top - 2, z + f, Blocks.AIR.defaultBlockState());
			}
			case 1 -> { // Envy's long reaching arm
				set(level, o, x + 2, base + 3, z + f, body);
			}
			default -> { // Sloth: a slumped arm on the plinth
				set(level, o, x - 2, base, z + f, body);
			}
		}
		// the brazier in front and the name
		set(level, o, x, 1, z + 3 * f, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		set(level, o, x, 2, z + 3 * f, Blocks.CAMPFIRE.defaultBlockState());
		sign(level, o.offset(x, 2, z + 2 * f), f > 0 ? Direction.SOUTH : Direction.NORTH, "sin_" + name);
	}

	/**
	 * Glowing runes round the walls and the great lightning bolt behind the throne: each rune pixel is cast outward from the
	 * cave's middle and replaces the first rock it meets.
	 */
	private static void wallRunes(ServerLevel level, BlockPos o) {
		// the ring of runes, two rows, all the way round
		for (int a = 0; a < 360; a += 15) {
			double rad = Math.toRadians(a + 7);
			double dx = Math.cos(rad), dz = Math.sin(rad);
			int bits = GLYPHS[Math.floorMod(a / 15, GLYPHS.length)];
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					if (((bits >> (8 - (row * 3 + col))) & 1) == 0) {
						continue;
					}
					double tx = -dz * (col - 1), tz = dx * (col - 1);
					paint(level, o, CAVE_X + tx, 0.5 + tz, dx, dz, 9 - row, Blocks.GLOWSTONE.defaultBlockState());
				}
			}
		}
		// the bolt: a zig-zag 16 tall on the wall behind the throne
		int[][] bolt = { { 1, 21 }, { 2, 21 }, { 0, 20 }, { 1, 20 }, { 0, 19 }, { -1, 19 }, { 0, 18 }, { -1, 18 }, { -2, 17 }, { -1, 17 },
				{ -2, 16 }, { -1, 16 }, { 0, 16 }, { 1, 16 }, { 0, 15 }, { 1, 15 }, { -1, 14 }, { 0, 14 }, { -1, 13 }, { -2, 12 }, { -1, 12 },
				{ -2, 11 }, { -1, 11 }, { 0, 11 }, { 1, 11 }, { 0, 10 }, { 1, 10 }, { -1, 9 }, { 0, 9 }, { -1, 8 }, { -2, 7 }, { -1, 7 } };
		for (int[] b : bolt) {
			int dy = b[1] - 3;
			// low down, cast from behind the throne's back; higher up the dome curves in over it, so cast from in front of it
			paint(level, o, dy <= 10 ? THRONE_X + 2 : THRONE_X - 15, 0.5 + b[0], 1, 0, dy, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
		}
	}

	/** Casts from (x, z) at height dy along (dx, dz) and replaces the first solid block within 50 with {@code s}. */
	private static void paint(ServerLevel level, BlockPos o, double x, double z, double dx, double dz, int dy, BlockState s) {
		for (double t = 0; t < 50; t += 0.5) {
			BlockPos p = BlockPos.containing(o.getX() + x + dx * t, o.getY() + dy, o.getZ() + z + dz * t);
			BlockState at = level.getBlockState(p);
			if (!at.isAir() && !at.is(Blocks.CAMPFIRE) && !at.is(Blocks.DARK_OAK_WALL_SIGN)) {
				if (at.is(Blocks.STONE) || at.is(Blocks.DEEPSLATE) || at.is(Blocks.ANDESITE) || at.is(Blocks.TUFF) || at.is(Blocks.COBBLED_DEEPSLATE)) {
					level.setBlock(p, s, FLAGS);
				}
				return;
			}
		}
	}

	// ---------------------------------------------------------------- the flickering lamps

	/** Two of the station's ceiling lamps flicker (called every tick for a slot with a player in the station). */
	public static void flicker(ServerLevel level, int slot) {
		BlockPos o = origin(level, slot);
		long t = level.getGameTime();
		for (int i = 0; i < 2; i++) {
			BlockPos p = o.offset(i == 0 ? -6 : 12, 7, i == 0 ? 5 : -1);
			BlockState s = level.getBlockState(p);
			if (!s.is(Blocks.REDSTONE_LAMP)) {
				continue;
			}
			double r = hash((int) (t / 2), i, slot, 61);
			boolean lit = r > (i == 0 ? 0.22 : 0.35);
			if (s.getValue(RedstoneLampBlock.LIT) != lit) {
				level.setBlock(p, s.setValue(RedstoneLampBlock.LIT, lit), FLAGS);
			}
		}
	}
}
