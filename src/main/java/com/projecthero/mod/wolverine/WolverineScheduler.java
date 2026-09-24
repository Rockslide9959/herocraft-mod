package com.projecthero.mod.wolverine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * A tiny per-player delayed-task queue for the multi-hit moves (Cross Slash's second cut, Frenzy's five
 * strikes, the Execution wind-up). Server-side only; a static world-object cache, so it is emptied by
 * {@code ServerStateReset} and by {@link Wolverine#clearTransient} on death / logout / dimension change.
 */
public final class WolverineScheduler {
	private record Task(long at, Runnable run) {
	}

	private static final Map<UUID, List<Task>> TASKS = new ConcurrentHashMap<>();

	private WolverineScheduler() {
	}

	public static void clearSessionState() {
		TASKS.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
	}

	/** Run {@code task} {@code delayTicks} from now (0 = the next tick this player is ticked). */
	public static void schedule(ServerPlayer player, int delayTicks, Runnable task) {
		TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
				.add(new Task(player.level().getGameTime() + Math.max(0, delayTicks), task));
	}

	public static boolean hasPending(ServerPlayer player) {
		List<Task> list = TASKS.get(player.getUUID());
		return list != null && !list.isEmpty();
	}

	public static void tick(ServerPlayer player) {
		List<Task> list = TASKS.get(player.getUUID());
		if (list == null || list.isEmpty()) {
			return;
		}
		long now = player.level().getGameTime();
		List<Task> due = new ArrayList<>();
		for (Task t : list) {
			if (t.at() <= now) {
				due.add(t);
			}
		}
		if (due.isEmpty()) {
			return;
		}
		list.removeAll(due);
		if (!player.isAlive()) {
			return;
		}
		for (Task t : due) {
			t.run().run();
		}
	}
}
