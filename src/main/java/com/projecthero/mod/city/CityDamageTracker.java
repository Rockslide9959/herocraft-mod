package com.projecthero.mod.city;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * v0.16.2 City part B: finds City damage.
 *
 * <ol>
 * <li><b>Block-change hook</b> ({@code CityLevelChunkMixin} on {@code LevelChunk.setBlockState}): in a City chunk (one
 * hash lookup anywhere else) the change is queued and looked at at the end of the tick. A change where the old block
 * matched the blueprint and the new one is air, fluid or fire is damage (mining, explosions, fire, smashes...). Fires
 * are also handed to the firefighters.</li>
 * <li><b>Player renovations.</b> A block a player places ({@code CityBlockItemMixin} marks the placement) in a queued
 * slot, or in a 4x4x4 cell that took blueprint damage within {@code renovationWindowTicks}, opts that cell out: it is
 * dropped from the queue and never repaired. Placing the blueprint's own block just counts as fixed.</li>
 * <li><b>Audit sweep</b> ({@link #sweep}): one loaded City chunk every {@code sweepIntervalTicks}, each at most once per
 * {@code sweepChunkEveryTicks}, is diffed against the blueprint, to catch damage done while this was off. Pieces with
 * structure processors are skipped (a processor may leave a template slot empty on purpose).</li>
 * </ol>
 */
public final class CityDamageTracker {
	private record Change(long pos, BlockState old, BlockState neu, boolean player) {
	}

	private static final int MAX_PENDING = 65536;
	private static final int SWEEP_MAX_POSITIONS = 4096;

	private static final Map<ResourceKey<Level>, ArrayDeque<Change>> PENDING = new ConcurrentHashMap<>();
	private static final Map<ResourceKey<Level>, LongOpenHashSet> FIRES = new ConcurrentHashMap<>();
	private static int suppress;
	private static int playerPlacing;

	private CityDamageTracker() {
	}

	/** True for what a repair may fill: air, a fluid block, fire. */
	public static boolean airish(BlockState s) {
		return s.isAir() || s.getBlock() instanceof LiquidBlock || s.getBlock() instanceof BaseFireBlock;
	}

	/** Runs {@code r} without recording its block changes (repairs, scaffolding). */
	public static void suppressed(Runnable r) {
		suppress++;
		try {
			r.run();
		} finally {
			suppress--;
		}
	}

	/** {@code CityBlockItemMixin}: a player's block placement starts / ends. */
	public static void playerPlacing(boolean start) {
		playerPlacing += start ? 1 : -1;
		if (playerPlacing < 0) {
			playerPlacing = 0;
		}
	}

	/** {@code CityLevelChunkMixin}: a block changed in a loaded chunk. Must stay cheap outside Cities. */
	public static void onBlockChanged(Level level, BlockPos pos, BlockState old, BlockState neu) {
		if (suppress > 0 || level.isClientSide() || old == neu || !CityLocator.isCityChunk(level, ChunkPos.asLong(pos))) {
			return;
		}
		ArrayDeque<Change> q = PENDING.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
		if (q.size() < MAX_PENDING) {
			q.add(new Change(pos.asLong(), old, neu, playerPlacing > 0));
		}
	}

	/** End of every server tick: look at the queued changes. */
	static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			ArrayDeque<Change> q = PENDING.get(level.dimension());
			if (q != null && !q.isEmpty()) {
				process(level, q);
			}
			int every = Math.max(1, CityConfig.repair().sweepIntervalTicks);
			if (server.getTickCount() % every == 0 && City.repairActive(level)) {
				sweep(level);
			}
		}
	}

	/** Tests: process this level's queued changes now. */
	public static void flush(ServerLevel level) {
		ArrayDeque<Change> q = PENDING.get(level.dimension());
		if (q != null) {
			process(level, q);
		}
	}

	private static void process(ServerLevel level, ArrayDeque<Change> q) {
		CitySavedData data = CitySavedData.get(level);
		long now = level.getGameTime();
		boolean repair = City.repairActive(level);
		long window = CityConfig.repair().renovationWindowTicks;
		Change c;
		while ((c = q.poll()) != null) {
			BlockPos pos = BlockPos.of(c.pos);
			if (c.neu.getBlock() instanceof BaseFireBlock && CityLocator.cityAround(level, pos) != null) {
				LongOpenHashSet fires = FIRES.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
				if (fires.size() < 8192) {
					fires.add(c.pos);
				}
			}
			if (data.hasNoDropEntries() && !c.old.is(c.neu.getBlock())) {
				data.consumeRepaired(pos, now);
			}
			if (!repair) {
				continue;
			}
			CityArea city = CityLocator.cityAt(level, pos);
			if (city == null) {
				continue;
			}
			BlockState expected = CityBlueprint.expectedAt(level, city, pos);
			if (c.player && !airish(c.neu)) {
				if (expected != null && c.neu.is(expected.getBlock())) {
					data.removeDamage(city.id(), pos); // the player put the blueprint block back
				} else if (data.isQueued(city.id(), pos) || now - data.lastCellDamage(city.id(), pos) <= window) {
					data.optOut(city.id(), pos); // a renovation: this cell is the player's now
				}
				continue;
			}
			if (expected == null) {
				continue;
			}
			if (c.neu.is(expected.getBlock())) {
				data.removeDamage(city.id(), pos);
			} else if (c.old.is(expected.getBlock()) && airish(c.neu)) {
				data.addDamage(city.id(), pos, now);
			}
		}
	}

	// ---------------------------------------------------------------- audit sweep


	/** Diffs the next due loaded City chunk against the blueprint. Returns how many positions were queued. */
	public static int sweep(ServerLevel level) {
		LongOpenHashSet chunks = new LongOpenHashSet(CityLocator.loadedCityChunks(level));
		if (chunks.isEmpty()) {
			return 0;
		}
		long now = level.getGameTime();
		long every = CityConfig.repair().sweepChunkEveryTicks;
		CitySavedData data = CitySavedData.get(level);
		LongIterator it = chunks.iterator();
		while (it.hasNext()) {
			long chunk = it.nextLong();
			ChunkPos cp = new ChunkPos(chunk);
			if (!level.hasChunk(cp.x, cp.z)) {
				continue;
			}
			for (CityArea city : CityLocator.citiesInChunk(level, cp)) {
				CitySavedData.CityRecord rec = data.city(city.id());
				long last = rec.swept.getOrDefault(chunk, Long.MIN_VALUE);
				if (last != Long.MIN_VALUE && now - last < every) {
					continue;
				}
				rec.swept.put(chunk, now);
				return sweepChunk(level, city, cp, data, now);
			}
		}
		return 0;
	}

	/** Diffs one chunk of one City against the blueprint; queues every expected block that is now air/fluid/fire. */
	public static int sweepChunk(ServerLevel level, CityArea city, ChunkPos cp, CitySavedData data, long now) {
		BoundingBox clip = new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(),
				level.getMaxBuildHeight() - 1, cp.getMaxBlockZ());
		int[] queued = {0};
		for (StructurePiece piece : city.pieces()) {
			if (!piece.getBoundingBox().intersects(clip) || CityBlueprint.hasProcessors(piece)) {
				continue;
			}
			CityBlueprint.forEachExpected(level, piece, clip, (pos, state) -> {
				if (queued[0] >= SWEEP_MAX_POSITIONS || state.getBlock() instanceof LiquidBlock) {
					return;
				}
				if (airish(level.getBlockState(pos)) && !data.isQueued(city.id(), pos) && data.addDamage(city.id(), pos, now)) {
					queued[0]++;
				}
			});
		}
		return queued[0];
	}

	// ---------------------------------------------------------------- fires (for the firefighters)

	/** Fires recorded inside Cities of {@code level} (the live set; drop out-of-date entries as you read). */
	public static LongOpenHashSet fires(ServerLevel level) {
		return FIRES.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
	}

	public static void clearSessionState() {
		PENDING.clear();
		FIRES.clear();
		suppress = 0;
		playerPlacing = 0;
	}
}
