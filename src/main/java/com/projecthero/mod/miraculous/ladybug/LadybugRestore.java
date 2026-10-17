package com.projecthero.mod.miraculous.ladybug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.city.CityDamageTracker;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: what Miraculous Ladybug puts back. Every block change in a loaded chunk is offered here by the City's
 * existing hook ({@code CityLevelChunkMixin} on {@code LevelChunk.setBlockState}); a change within
 * {@link LadybugConfig#RESTORE_RADIUS} of a TRANSFORMED Ladybug that destroys or replaces a block (explosions, mobs,
 * powers, players) records the block's old state -- only the first one per spot, so a crater refilled and blown up
 * again still comes back as the original. Outside that range the hook is one empty-list check.
 *
 * <p>Bounded: {@link LadybugConfig#RESTORE_MAX_TRACKED} spots per dimension (the oldest go first) and nothing older than
 * {@link LadybugConfig#RESTORE_WINDOW}. {@link #restoreAround} queues every remembered spot near her that is still
 * missing (air, fluid or fire now) and puts them back {@link LadybugConfig#RESTORE_PER_TICK} a tick, nearest first -- a
 * wave rolling out from her. A spot someone is standing in waits.
 */
public final class LadybugRestore {
	private record Rec(BlockState old, long at) {
	}

	private record Active(ResourceKey<Level> dim, Vec3 pos, long seen) {
	}

	private record Job(ResourceKey<Level> dim, ArrayDeque<Long> queue, Map<Long, BlockState> states, long giveUpAt) {
	}

	private static final Map<ResourceKey<Level>, Long2ObjectLinkedOpenHashMap<Rec>> TRACKED = new ConcurrentHashMap<>();
	/** Transformed Ladybugs, refreshed every tick from the kit. */
	private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
	private static final List<Job> JOBS = new ArrayList<>();
	private static int restoring;

	private LadybugRestore() {
	}

	static void clearSessionState() {
		TRACKED.clear();
		ACTIVE.clear();
		synchronized (JOBS) {
			JOBS.clear();
		}
	}

	static void forget(UUID id) {
		ACTIVE.remove(id);
	}

	/** The kit's tick: she is transformed here, now. */
	static void markActive(ServerPlayer p) {
		ACTIVE.put(p.getUUID(), new Active(p.level().dimension(), p.position(), p.level().getGameTime()));
	}

	static void markInactive(ServerPlayer p) {
		ACTIVE.remove(p.getUUID());
	}

	/** From {@code CityLevelChunkMixin}: a block changed in a loaded chunk. Must stay cheap. */
	public static void onBlockChanged(Level level, BlockPos pos, BlockState old, BlockState neu) {
		if (ACTIVE.isEmpty() || restoring > 0 || level.isClientSide() || old == neu || old.getBlock() == neu.getBlock()
				|| CityDamageTracker.airish(old)) {
			return;
		}
		ResourceKey<Level> dim = level.dimension();
		long now = level.getGameTime();
		double r2 = LadybugConfig.RESTORE_RADIUS * LadybugConfig.RESTORE_RADIUS;
		boolean near = false;
		for (Active a : ACTIVE.values()) {
			if (a.dim() == dim && now - a.seen() < 40 && a.pos().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2) {
				near = true;
				break;
			}
		}
		if (!near) {
			return;
		}
		Long2ObjectLinkedOpenHashMap<Rec> map = TRACKED.computeIfAbsent(dim, k -> new Long2ObjectLinkedOpenHashMap<>());
		synchronized (map) {
			long key = pos.asLong();
			if (map.containsKey(key)) {
				return;
			}
			map.put(key, new Rec(old, now));
			while (map.size() > LadybugConfig.RESTORE_MAX_TRACKED) {
				map.removeFirst();
			}
		}
	}

	/** How many spots this dimension remembers (gametests). */
	public static int tracked(Level level) {
		Long2ObjectLinkedOpenHashMap<Rec> map = TRACKED.get(level.dimension());
		return map == null ? 0 : map.size();
	}

	/** Miraculous Ladybug: queue every remembered, still-missing spot near {@code center}. Returns how many. */
	public static int restoreAround(ServerLevel level, Vec3 center, double radius) {
		Long2ObjectLinkedOpenHashMap<Rec> map = TRACKED.get(level.dimension());
		if (map == null) {
			return 0;
		}
		long now = level.getGameTime();
		double r2 = radius * radius;
		List<long[]> found = new ArrayList<>();
		Map<Long, BlockState> states = new java.util.HashMap<>();
		synchronized (map) {
			// (not the fast iterator: its reused entry changes under remove())
			var it = map.long2ObjectEntrySet().iterator();
			while (it.hasNext()) {
				var en = it.next();
				long key = en.getLongKey();
				Rec r = en.getValue();
				if (now - r.at() > LadybugConfig.RESTORE_WINDOW) {
					it.remove();
					continue;
				}
				BlockPos bp = BlockPos.of(key);
				double d = center.distanceToSqr(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
				if (d > r2) {
					continue;
				}
				it.remove();
				if (!level.isLoaded(bp) || !CityDamageTracker.airish(level.getBlockState(bp))) {
					continue; // something else stands there now (rebuilt, or a player's block)
				}
				found.add(new long[] { key, (long) d });
				states.put(key, r.old());
			}
		}
		found.sort((a, b) -> Long.compare(a[1], b[1]));
		ArrayDeque<Long> q = new ArrayDeque<>();
		for (long[] f : found) {
			q.add(f[0]);
		}
		if (!q.isEmpty()) {
			synchronized (JOBS) {
				JOBS.add(new Job(level.dimension(), q, states, now + 600L));
			}
		}
		return q.size();
	}

	/** True while a wave is still putting blocks back (gametests). */
	public static boolean busy() {
		synchronized (JOBS) {
			return !JOBS.isEmpty();
		}
	}

	/** END_SERVER_TICK: the waves, and forgetting what is too old. */
	static void serverTick(MinecraftServer server) {
		ACTIVE.values().removeIf(a -> {
			ServerLevel l = server.getLevel(a.dim());
			return l == null || l.getGameTime() - a.seen() > 200;
		});
		synchronized (JOBS) {
			JOBS.removeIf(job -> {
				ServerLevel level = server.getLevel(job.dim());
				if (level == null || level.getGameTime() > job.giveUpAt()) {
					return true; // (a spot somebody never leaves is given up after 30 s)
				}
				int budget = LadybugConfig.RESTORE_PER_TICK;
				int deferred = 0;
				while (budget-- > 0 && !job.queue().isEmpty()) {
					long key = job.queue().poll();
					BlockPos bp = BlockPos.of(key);
					BlockState old = job.states().get(key);
					if (old == null || !level.isLoaded(bp) || !CityDamageTracker.airish(level.getBlockState(bp))) {
						continue;
					}
					if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(bp)).isEmpty()) {
						if (deferred++ < 64) {
							job.queue().add(key); // someone is standing in it: later
						}
						continue;
					}
					restoring++;
					try {
						level.setBlock(bp, old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					} finally {
						restoring--;
					}
					if (level.random.nextInt(3) == 0) {
						level.sendParticles(LadybugMoves.RED, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
						level.sendParticles(ParticleTypes.END_ROD, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.02);
					}
				}
				return job.queue().isEmpty();
			});
		}
		if (server.getTickCount() % 200 == 0) {
			for (Map.Entry<ResourceKey<Level>, Long2ObjectLinkedOpenHashMap<Rec>> e : TRACKED.entrySet()) {
				ServerLevel level = server.getLevel(e.getKey());
				if (level == null) {
					continue;
				}
				long now = level.getGameTime();
				Long2ObjectLinkedOpenHashMap<Rec> map = e.getValue();
				synchronized (map) {
					while (!map.isEmpty() && now - map.get(map.firstLongKey()).at() > LadybugConfig.RESTORE_WINDOW) {
						map.removeFirst();
					}
				}
			}
		}
	}
}
