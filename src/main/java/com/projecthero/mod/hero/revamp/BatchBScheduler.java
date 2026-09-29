package com.projecthero.mod.hero.revamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.13.22 batch B: a tiny server-side effect scheduler for the travelling / delayed parts of the batch-B moves
 * (Earth Spike's running line of spikes, the Tectonic Pillar rising, the Tidal Wave rolling forward, the whips
 * uncurling ...) plus a ledger of <b>short-lived block changes</b>:
 *
 * <ul>
 *   <li>{@link #removeTemporarily} -- Geokinesis' Sinkhole takes ground away and gives it back;</li>
 *   <li>{@link #placeTemporarily} -- Water Manipulation's wave front and prison shell. Unlike {@code TempBlocks}
 *       (a FIFO whose short entries wait behind any longer one queued before them), every entry here expires on its
 *       own clock, so a 6-tick splash of water really is gone 6 ticks later. Water is only ever placed into air and
 *       without the placement callback, so it never starts flowing.</li>
 * </ul>
 *
 * <p>Both lists hold {@link ServerLevel} references, so both are emptied when the server stops -- and every change is
 * undone first (on {@code SERVER_STOPPING}, while the levels are still loaded), so a pit or a splash can never outlive
 * the session. Hooked from {@link RevampBatchB#init}; this keeps batch B out of the shared {@code ServerStateReset}.
 */
public final class BatchBScheduler {
	/** One running effect. Return {@code false} from {@link #tick} when it is finished. */
	@FunctionalInterface
	public interface Task {
		/** @param age ticks since the task was scheduled (0 on the first call) */
		boolean tick(int age);
	}

	private static final class Running {
		final Task task;
		int age;

		Running(Task task) {
			this.task = task;
		}
	}

	private static final class Changed {
		final ServerLevel level;
		final BlockPos pos;
		final BlockState previous;
		final BlockState placed;
		long restoreAt;

		Changed(ServerLevel level, BlockPos pos, BlockState previous, BlockState placed, long restoreAt) {
			this.level = level;
			this.pos = pos;
			this.previous = previous;
			this.placed = placed;
			this.restoreAt = restoreAt;
		}
	}

	private static final int MAX_TASKS = 512;
	private static final int MAX_CHANGED = 4096;
	private static final List<Running> TASKS = new ArrayList<>();
	private static final Map<Long, List<Changed>> CHANGED = new HashMap<>();
	private static int changedCount;

	private BatchBScheduler() {
	}

	public static void schedule(ServerLevel level, Task task) {
		if (TASKS.size() >= MAX_TASKS) {
			TASKS.remove(0); // oldest effect loses -- never grow without bound
		}
		TASKS.add(new Running(task));
	}

	public static int runningTasks() {
		return TASKS.size();
	}

	public static int pendingRestores() {
		return changedCount;
	}

	private static Changed find(ServerLevel level, BlockPos pos) {
		List<Changed> list = CHANGED.get(pos.asLong());
		if (list != null) {
			for (Changed c : list) {
				if (c.level == level) {
					return c;
				}
			}
		}
		return null;
	}

	private static void track(Changed c) {
		CHANGED.computeIfAbsent(c.pos.asLong(), k -> new ArrayList<>(1)).add(c);
		changedCount++;
	}

	/**
	 * Takes a block out of the world for {@code ttl} ticks (Sinkhole). Only plain, breakable, block-entity-free,
	 * fluid-free blocks are touched; the original comes back when the timer ends (if the spot is still air), with
	 * anything standing in the way lifted clear first.
	 *
	 * @return true if the block was removed
	 */
	public static boolean removeTemporarily(ServerLevel level, BlockPos pos, int ttl) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir() || st.hasBlockEntity() || st.getDestroySpeed(level, pos) < 0 || !st.getFluidState().isEmpty()
				|| changedCount >= MAX_CHANGED || find(level, pos) != null) {
			return false;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		track(new Changed(level, pos.immutable(), st, Blocks.AIR.defaultBlockState(), level.getGameTime() + ttl));
		return true;
	}

	/**
	 * Puts {@code state} into an <b>air</b> cell for {@code ttl} ticks, without neighbour updates or the placement
	 * callback (so a water block sits still instead of flowing). Placing again on a cell this ledger already holds
	 * with the same state just extends its timer.
	 *
	 * @return true if the block is there now
	 */
	public static boolean placeTemporarily(ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		if (!com.projecthero.mod.hero.HeroConfig.get().abilityTerrainDamage) {
			return false;
		}
		Changed existing = find(level, pos);
		if (existing != null) {
			if (existing.placed == state && level.getBlockState(pos) == state) {
				existing.restoreAt = Math.max(existing.restoreAt, level.getGameTime() + ttl);
				return true;
			}
			return false;
		}
		if (!level.getBlockState(pos).isAir() || changedCount >= MAX_CHANGED || !level.hasChunkAt(pos)) {
			return false;
		}
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_MOVE_BY_PISTON);
		track(new Changed(level, pos.immutable(), Blocks.AIR.defaultBlockState(), state, level.getGameTime() + ttl));
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (!TASKS.isEmpty()) {
			// iterate a snapshot: a task may schedule another one
			List<Running> snapshot = new ArrayList<>(TASKS);
			for (Running r : snapshot) {
				boolean keep;
				try {
					keep = r.task.tick(r.age++);
				} catch (RuntimeException e) {
					keep = false; // a broken effect must never take the server tick down
				}
				if (!keep) {
					TASKS.remove(r);
				}
			}
		}
		if (changedCount > 0) {
			// removals are restored bottom-up so a refilling pit lifts whatever is in it out in order
			List<Changed> due = new ArrayList<>();
			Iterator<Map.Entry<Long, List<Changed>>> it = CHANGED.entrySet().iterator();
			while (it.hasNext()) {
				List<Changed> list = it.next().getValue();
				Iterator<Changed> li = list.iterator();
				while (li.hasNext()) {
					Changed c = li.next();
					if (c.level.getGameTime() >= c.restoreAt) {
						due.add(c);
						li.remove();
						changedCount--;
					}
				}
				if (list.isEmpty()) {
					it.remove();
				}
			}
			due.sort((a, b) -> Integer.compare(a.pos.getY(), b.pos.getY()));
			for (Changed c : due) {
				restore(c);
			}
		}
	}

	private static void restore(Changed c) {
		if (!c.level.hasChunkAt(c.pos)) {
			return;
		}
		BlockState now = c.level.getBlockState(c.pos);
		if (c.placed.isAir()) {
			// a removal: only refill if nobody built there in the meantime
			if (!now.isAir() && now.getFluidState().isEmpty() && !now.canBeReplaced()) {
				return;
			}
			for (Entity e : c.level.getEntitiesOfClass(Entity.class, new AABB(c.pos))) {
				e.teleportTo(e.getX(), c.pos.getY() + 1.05, e.getZ());
				e.hurtMarked = true;
			}
			c.level.setBlock(c.pos, c.previous, Block.UPDATE_ALL);
		} else if (now == c.placed) {
			c.level.setBlock(c.pos, c.previous, Block.UPDATE_ALL);
		}
	}

	/** SERVER_STOPPING: undo every change while the levels still exist. */
	public static void restoreAllNow() {
		List<Changed> all = new ArrayList<>();
		for (List<Changed> list : CHANGED.values()) {
			all.addAll(list);
		}
		all.sort((a, b) -> Integer.compare(a.pos.getY(), b.pos.getY()));
		for (Changed c : all) {
			try {
				restore(c);
			} catch (RuntimeException ignored) {
				// the level may already be half torn down -- nothing more we can do for this cell
			}
		}
		CHANGED.clear();
		changedCount = 0;
	}

	/** SERVER_STOPPED: drop every reference to the dead levels. */
	public static void clearSessionState() {
		TASKS.clear();
		CHANGED.clear();
		changedCount = 0;
	}
}
