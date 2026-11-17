package com.projecthero.mod.sentry;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.sentry.block.SentryBlocks;
import com.projecthero.mod.sentry.network.VoidDecayPayload;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the Void eats the world. Rework 2 (2026-11-17, user: "increase the range ... make it so that the blocks slowly fade to black
 * before dissolving quickly ... the range at which he destroys the world keeps increasing and increasing"):
 * <ul>
 *   <li><b>The disc</b> ({@link #tickRing}): a disc on the ground under the Void, {@link SentryConfig#MATTER_START_RADIUS} wide at first and
 *   growing {@link SentryConfig#MATTER_GROWTH_PER_SECOND} a second for the whole takeover up to {@link SentryConfig#MATTER_MAX_RADIUS}. Each
 *   tick it tries {@link SentryConfig#MATTER_TRIES_PER_TICK} columns (55% in the 5-block band at the edge: the spreading front; 45% anywhere
 *   inside: the bowl deepening) and starts at most {@link SentryConfig#MATTER_BLOCKS_PER_TICK} blocks. A column is eaten from the top
 *   (trees, plants, builds) down to its original ground minus a depth that grows from {@link SentryConfig#MATTER_DEPTH_EDGE} at the edge
 *   to {@link SentryConfig#MATTER_DEPTH_CENTRE} under the centre. Water stops a column (no air pockets under the sea).</li>
 *   <li><b>The decay</b> ({@link #startDecay}): a block is not replaced any more -- it is recorded, marked as decaying, and sent to nearby
 *   clients in one batch a tick ({@link VoidDecayPayload}); the client darkens it to black over {@link SentryConfig#DECAY_TICKS} (+ up to a
 *   quarter by position), then the server turns it to air and the client dissolves it in a quick dark burst. Blocks carved out of the
 *   Void's way ({@link #carve}) fade in {@link SentryConfig#CARVE_DECAY_TICKS}.</li>
 *   <li><b>The restore</b>: about a minute after control returns ({@link SentryConfig#RESTORE_DELAY_TICKS}) the world grows back,
 *   {@link SentryConfig#RESTORE_PER_TICK} blocks a tick from a due-ordered queue, into air only (a block someone has built there since is
 *   left alone); an entry in an unloaded chunk waits until it loads.</li>
 * </ul>
 *
 * <p>Never eaten: bedrock and every unbreakable block (barriers too), anything with a block entity (chests, furnaces, signs, beds -- all
 * skipped, so no contents are ever at risk), portals and their frames, fluids, anything inside spawn protection or that the body may not
 * touch ({@code mayInteract}), and unloaded chunks. The gamerule {@code projectheroVoidGriefing} (default false) makes it permanent:
 * nothing is recorded and nothing comes back. Every bit of state is per world ({@link VoidMatterData}), nothing static.
 */
public final class VoidMatter {
	public static final String RULE_NAME = "projectheroVoidGriefing";
	public static GameRules.Key<GameRules.BooleanValue> RULE;

	/** The last cost report ("[void] ..."), for the harness. */
	public static volatile String lastReport = "";

	private VoidMatter() {
	}

	public static void initialize() {
		RULE = GameRuleRegistry.register(RULE_NAME, GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
	}

	public static boolean permanent(ServerLevel level) {
		return RULE != null && level.getGameRules().getBoolean(RULE);
	}

	/** The disc's radius {@code ticks} into a takeover. */
	public static double radius(long ticks) {
		return Math.min(SentryConfig.MATTER_MAX_RADIUS, SentryConfig.MATTER_START_RADIUS + ticks / 20.0 * SentryConfig.MATTER_GROWTH_PER_SECOND);
	}

	/** How many layers under the original ground a column {@code d} from the centre of a disc of radius {@code r} is eaten. */
	public static int depthAt(double d, double r) {
		double k = Mth.clamp(1.0 - d / Math.max(1.0, r), 0.0, 1.0);
		return SentryConfig.MATTER_DEPTH_EDGE + (int) Math.round((SentryConfig.MATTER_DEPTH_CENTRE - SentryConfig.MATTER_DEPTH_EDGE) * k * k);
	}

	/** How long the block at {@code pos} takes to fade, from a base time: the base plus up to a quarter more, by position (both sides). */
	public static int decayTicks(long pos, int base) {
		long h = pos * 0x9E3779B97F4A7C15L;
		h ^= h >>> 29;
		return base + (int) Math.floorMod(h, (long) (base / 4 + 1));
	}

	/** Whether the Void may swallow the block at {@code pos} (for the body of {@code owner}). */
	public static boolean edible(ServerLevel level, ServerPlayer owner, BlockPos pos) {
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
			return false;
		}
		BlockState st = level.getBlockState(pos);
		if (!SentryMoves.unmakeable(level, pos, st)) {
			return false;
		}
		if (owner.getServer() != null && owner.getServer().isUnderSpawnProtection(level, pos, owner)) {
			return false;
		}
		return level.mayInteract(owner, pos);
	}

	/** Whether the block at {@code pos} is fading to black right now. */
	public static boolean decaying(ServerLevel level, BlockPos pos) {
		return VoidMatterData.get(level).decaying(pos);
	}

	/**
	 * Starts a block fading to black (records it first, unless the gamerule is on). {@code carved}: out of the Void's way (the fast fade).
	 * True if it started.
	 */
	public static boolean startDecay(ServerLevel level, ServerPlayer owner, BlockPos pos, boolean carved) {
		VoidMatterData data = VoidMatterData.get(level);
		long key = pos.asLong();
		if (data.decaying.contains(key) || !edible(level, owner, pos)) {
			return false;
		}
		if (!permanent(level)) {
			data.record(pos, level.getBlockState(pos), owner.getUUID());
		}
		data.decaying.add(key);
		int ticks = decayTicks(key, carved ? SentryConfig.CARVE_DECAY_TICKS : SentryConfig.DECAY_TICKS);
		data.decayDue.computeIfAbsent(level.getGameTime() + ticks, k -> new LongArrayList()).add(key);
		(carved ? data.pendingCarve : data.pendingGround).add(key);
		data.statStarted++;
		return true;
	}

	/** Swallows one block: it starts fading (records it first). True if it did. Public for the tests. */
	public static boolean eat(ServerLevel level, ServerPlayer owner, BlockPos pos) {
		return startDecay(level, owner, pos, false);
	}

	// ---------------------------------------------------------------- the disc

	private static long column(int x, int z) {
		return ((long) x & 0xFFFFFFFFL) | ((long) z << 32);
	}

	/** A column's original ground: the first solid block from the top that isn't a log, leaves or a plant (cached per world). */
	static int surface(ServerLevel level, VoidMatterData data, int x, int z) {
		long key = column(x, z);
		int known = data.columnSurface.get(key);
		if (known != Integer.MIN_VALUE) {
			return known;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		int min = level.getMinBuildHeight();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 64 && y > min; i++, y--) {
			BlockState st = level.getBlockState(pos.set(x, y, z));
			if (st.isAir() || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || (st.canBeReplaced() && st.getFluidState().isEmpty())
					|| (st.getFluidState().isEmpty() && st.getCollisionShape(level, pos).isEmpty())) {
				continue;
			}
			break;
		}
		data.columnSurface.put(key, y);
		return y;
	}

	/** One column: starts the topmost block still to go (down to its depth). True if one started. */
	static boolean tryColumn(ServerLevel level, VoidMatterData data, ServerPlayer owner, int x, int z, int depth) {
		long key = column(x, z);
		if (data.columnDone.get(key) >= depth || !level.hasChunk(x >> 4, z >> 4)) {
			return false;
		}
		int surf = surface(level, data, x, z);
		int floor = surf - depth + 1;
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int y = top;
		for (; y >= floor && y > top - 64; y--) {
			pos.set(x, y, z);
			if (data.decaying.contains(pos.asLong())) {
				continue;
			}
			BlockState st = level.getBlockState(pos);
			if (st.isAir()) {
				continue;
			}
			if (!st.getFluidState().isEmpty()) {
				break; // water / lava: the column stops here
			}
			if (startDecay(level, owner, pos, false)) {
				return true;
			}
			// a chest, bedrock, a barrier, spawn protection: left standing, the rest goes around it
		}
		if (y < floor || !level.getBlockState(pos).getFluidState().isEmpty()) {
			data.columnDone.put(key, depth);
		}
		return false;
	}

	/** One tick of the disc under the body, {@code ticks} into the takeover. Returns how many blocks started to go. */
	public static int tickRing(ServerPlayer owner, long ticks) {
		long t0 = System.nanoTime();
		ServerLevel level = owner.serverLevel();
		VoidMatterData data = VoidMatterData.get(level);
		RandomSource rnd = owner.getRandom();
		double r = radius(ticks);
		int started = 0;
		int x0 = owner.getBlockX();
		int z0 = owner.getBlockZ();
		for (int i = 0; i < SentryConfig.MATTER_TRIES_PER_TICK && started < SentryConfig.MATTER_BLOCKS_PER_TICK; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double d = rnd.nextFloat() < 0.55f ? Math.max(0.0, r - rnd.nextDouble() * 5.0) : r * Math.sqrt(rnd.nextDouble());
			int x = x0 + Mth.floor(Math.cos(a) * d);
			int z = z0 + Mth.floor(Math.sin(a) * d);
			if (tryColumn(level, data, owner, x, z, depthAt(d, r))) {
				started++;
			}
		}
		data.statNanos += System.nanoTime() - t0;
		return started;
	}

	// ---------------------------------------------------------------- out of its way

	/** Carves (fast fade) every edible block in a sphere of radius {@code r} around {@code c}, at most {@code max}. Returns how many. */
	public static int carve(ServerPlayer owner, Vec3 c, double r, int max) {
		long t0 = System.nanoTime();
		ServerLevel level = owner.serverLevel();
		int n = 0;
		int ri = Mth.ceil(r);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = ri; dy >= -ri && n < max; dy--) {
			for (int dx = -ri; dx <= ri && n < max; dx++) {
				for (int dz = -ri; dz <= ri && n < max; dz++) {
					pos.set(Mth.floor(c.x) + dx, Mth.floor(c.y) + dy, Mth.floor(c.z) + dz);
					double ddx = pos.getX() + 0.5 - c.x;
					double ddy = pos.getY() + 0.5 - c.y;
					double ddz = pos.getZ() + 0.5 - c.z;
					if (ddx * ddx + ddy * ddy + ddz * ddz <= r * r && !level.getBlockState(pos).isAir() && startDecay(level, owner, pos, true)) {
						n++;
					}
				}
			}
		}
		VoidMatterData.get(level).statNanos += System.nanoTime() - t0;
		return n;
	}

	/** Carves every edible block touching {@code box} (the body's box moved where it wants to go). Returns how many. */
	public static int carveBox(ServerPlayer owner, AABB box, int max) {
		ServerLevel level = owner.serverLevel();
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
				Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			if (n >= max) {
				break;
			}
			if (!level.getBlockState(pos).isAir() && startDecay(level, owner, pos, true)) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- restore

	/** The takeover is over: what this Sentry's Void ate comes back in about a minute (or never, under the gamerule). */
	public static int scheduleRestore(ServerLevel level, ServerPlayer owner) {
		VoidMatterData data = VoidMatterData.get(level);
		if (permanent(level)) {
			return -data.forget(owner.getUUID());
		}
		return data.schedule(owner.getUUID(), level.getGameTime() + SentryConfig.RESTORE_DELAY_TICKS);
	}

	private static boolean canRestoreInto(ServerLevel level, BlockPos pos, VoidMatterData data) {
		BlockState here = level.getBlockState(pos);
		return here.isAir() || here.is(SentryBlocks.VOID_MATTER) || here.canBeReplaced() || data.decaying.contains(pos.asLong());
	}

	/** A creature stands where a solid block would come back (it would be buried): that block waits. */
	private static boolean occupied(ServerLevel level, VoidMatterData.Entry e) {
		if (e.state.getCollisionShape(level, e.pos).isEmpty()) {
			return false;
		}
		return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(e.pos), x -> x.isAlive() && !x.isSpectator()).isEmpty();
	}

	private static void restoreOne(ServerLevel level, VoidMatterData data, VoidMatterData.Entry e) {
		data.decaying.remove(e.pos.asLong()); // a block still fading (the one-go restore) stays
		level.setBlock(e.pos, e.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** Every world, every tick: the fades that are over turn to air, batches go out, due blocks come back. */
	public static void tickLevel(ServerLevel level) {
		long t0 = System.nanoTime();
		VoidMatterData data = VoidMatterData.get(level);
		long now = level.getGameTime();
		boolean eating = false;
		for (ServerPlayer p : level.players()) {
			if (Sentry.inTakeover(p)) {
				eating = true;
				break;
			}
		}
		if (!eating && !data.columnSurface.isEmpty() && data.decaying.isEmpty()) {
			data.forgetColumns();
		}
		// 1. the fades that are over: the block goes (no neighbour updates: torches and sand stay put, water doesn't flow in)
		if (!data.decayDue.isEmpty()) {
			long from = data.decayProcessed == Long.MIN_VALUE ? now - 200 : data.decayProcessed + 1;
			for (long t = Math.max(from, now - 400); t <= now; t++) {
				LongArrayList list = data.decayDue.remove(t);
				if (list == null) {
					continue;
				}
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
				for (int i = 0; i < list.size(); i++) {
					long key = list.getLong(i);
					if (!data.decaying.remove(key)) {
						continue; // restored meanwhile
					}
					pos.set(key);
					if (!level.isLoaded(pos)) {
						continue; // its chunk went: the block stays (its record puts the same block back)
					}
					BlockState st = level.getBlockState(pos);
					if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
						continue;
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					data.statRemoved++;
				}
			}
			// anything older than the window (a long server hiccup): let it go too
			if (data.decayDue.size() > 0 && data.decayProcessed != Long.MIN_VALUE && now - data.decayProcessed > 400) {
				data.decayDue.clear();
				data.decaying.clear();
			}
		}
		data.decayProcessed = now;
		// 2. this tick's new decays, in batches, to everyone near
		flush(level, data.pendingGround, SentryConfig.DECAY_TICKS);
		flush(level, data.pendingCarve, SentryConfig.CARVE_DECAY_TICKS);
		// 3. orphans: a takeover that ended without telling us (a crash) -- give them back too
		if (now % 100 == 0 && data.size() > 0) {
			data.adoptOrphans(owner -> {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
				return p != null && Sentry.inTakeover(p);
			}, now + SentryConfig.RESTORE_DELAY_TICKS);
		}
		// 4. the restore: from the front of the due queue; an unloaded chunk's entry waits at the back
		if (data.scheduledSize() > 0) {
			int done = 0;
			int looked = 0;
			VoidMatterData.Entry e;
			while (done < SentryConfig.RESTORE_PER_TICK && looked < SentryConfig.RESTORE_PER_TICK * 4 && (e = data.pollDue(now)) != null) {
				looked++;
				if (!level.isLoaded(e.pos) || occupied(level, e)) {
					data.requeue(e); // an unloaded chunk, or someone standing in the hole: it waits
					continue;
				}
				if (canRestoreInto(level, e.pos, data)) {
					restoreOne(level, data, e);
					if (level.getRandom().nextInt(8) == 0) {
						level.sendParticles(ParticleTypes.END_ROD, e.pos.getX() + 0.5, e.pos.getY() + 0.8, e.pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
					}
				}
				data.remove(e);
				data.statRestored++;
				done++;
			}
		}
		// 5. what it all cost (the takeover's own disc / carve work is added to statNanos as it runs)
		long spent = System.nanoTime() - t0 + data.statNanos;
		data.statNanos = 0L;
		if (eating || data.statStarted + data.statRemoved + data.statRestored > 0) {
			data.statTicks++;
			data.statMaxNanos = Math.max(data.statMaxNanos, spent);
			data.statSumNanos += spent;
		}
		if (data.statTicks >= 200) {
			String msg = String.format(java.util.Locale.ROOT,
					"[void] %s decay work: avg %.3f ms, max %.3f ms a tick over %d ticks; started %d, removed %d, restored %d; recorded %d, waiting %d",
					level.dimension().location(), data.statSumNanos / 1.0e6 / data.statTicks, data.statMaxNanos / 1.0e6, data.statTicks,
					data.statStarted, data.statRemoved, data.statRestored, data.size(), data.scheduledSize());
			lastReport = msg;
			if (data.statMaxNanos > 5_000_000L) {
				ProjectHeroMod.LOGGER.warn("[watchdog] {}", msg);
			} else {
				ProjectHeroMod.LOGGER.info(msg);
			}
			data.statTicks = 0;
			data.statSumNanos = 0L;
			data.statMaxNanos = 0L;
			data.statStarted = 0;
			data.statRemoved = 0;
			data.statRestored = 0;
		}
	}

	private static void flush(ServerLevel level, LongArrayList pending, int ticks) {
		if (pending.isEmpty()) {
			return;
		}
		for (int from = 0; from < pending.size(); from += VoidDecayPayload.MAX_BATCH) {
			int to = Math.min(pending.size(), from + VoidDecayPayload.MAX_BATCH);
			long[] batch = new long[to - from];
			pending.getElements(from, batch, 0, batch.length);
			BlockPos first = BlockPos.of(batch[0]);
			double r2 = SentryConfig.DECAY_SYNC_RANGE * SentryConfig.DECAY_SYNC_RANGE;
			VoidDecayPayload payload = new VoidDecayPayload(ticks, batch);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(first.getX(), first.getY(), first.getZ()) < r2 && ServerPlayNetworking.canSend(p, VoidDecayPayload.TYPE)) {
					ServerPlayNetworking.send(p, payload);
				}
			}
		}
		pending.clear();
	}

	/** Gametests: brings back everything of {@code owner}'s that is scheduled, in one go (loaded chunks only). Returns how many. */
	public static int restoreNow(ServerLevel level, UUID owner) {
		VoidMatterData data = VoidMatterData.get(level);
		int n = 0;
		for (VoidMatterData.Entry e : data.due(Long.MAX_VALUE - 1, Integer.MAX_VALUE)) {
			if (!e.owner.equals(owner) || !level.isLoaded(e.pos)) {
				continue;
			}
			if (canRestoreInto(level, e.pos, data)) {
				restoreOne(level, data, e);
				n++;
			}
			data.remove(e);
		}
		return n;
	}
}
