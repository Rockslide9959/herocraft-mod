package com.herocraft.mod.hero.power;

import java.util.ArrayDeque;
import java.util.Deque;

import com.herocraft.mod.hero.HeroConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temporary conjured blocks (stone/ice/crystal walls, etc.). Places a block only where the current
 * one is replaceable, remembers what was there, and restores it after a TTL. A single bounded deque
 * ticked once per server tick -- no per-block scheduling, no chunk scanning. Respects
 * {@link HeroConfig#abilityTerrainDamage}: when terrain modification is disabled, placements are
 * silently skipped (abilities fall back to their particle-only feedback).
 */
public final class TempBlocks {
	private record Entry(ServerLevel level, BlockPos pos, BlockState previous, long expiresAt) {
	}

	private static final Deque<Entry> QUEUE = new ArrayDeque<>();
	private static final int MAX_TRACKED = 4096;

	private TempBlocks() {
	}

	/** @return true if a block was actually placed. */
	public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ttlTicks) {
		return place(level, pos, state, ttlTicks, Block.UPDATE_ALL);
	}

	/**
	 * Place a temporary block that does NOT trigger neighbour/shape updates. For fluids this means the
	 * source never schedules a fluid tick, so it just sits there statically instead of flowing out
	 * across the world (which {@link TempBlocks} would then never clean up).
	 */
	public static boolean placeStatic(ServerLevel level, BlockPos pos, BlockState state, int ttlTicks) {
		return place(level, pos, state, ttlTicks, Block.UPDATE_CLIENTS);
	}

	public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ttlTicks, int flags) {
		if (!HeroConfig.get().abilityTerrainDamage) {
			return false;
		}
		BlockState current = level.getBlockState(pos);
		if (!current.canBeReplaced() && !current.isAir()) {
			return false;
		}
		if (QUEUE.size() >= MAX_TRACKED) {
			restore(QUEUE.pollFirst());
		}
		level.setBlock(pos, state, flags);
		QUEUE.addLast(new Entry(level, pos.immutable(), current, level.getGameTime() + ttlTicks));
		return true;
	}

	/**
	 * Drop every tracked placement without restoring it. Called only when the owning server has
	 * already stopped (see {@code ServerStateReset}), where "restore" would be meaningless -- the
	 * level is gone and its chunks are saved. Holding these entries instead would pin the dead
	 * {@link ServerLevel} forever, and a new world's game time never reaches their {@code expiresAt}.
	 */
	public static void clearSessionState() {
		QUEUE.clear();
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		int budget = 256;
		while (!QUEUE.isEmpty() && budget-- > 0) {
			Entry e = QUEUE.peekFirst();
			if (e.expiresAt > now) {
				break;
			}
			restore(QUEUE.pollFirst());
		}
	}

	private static void restore(Entry e) {
		if (e != null && e.level.hasChunkAt(e.pos)) {
			e.level.setBlock(e.pos, e.previous, Block.UPDATE_ALL);
		}
	}
}
