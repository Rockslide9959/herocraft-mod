package com.projecthero.mod.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.7 Cosmic revamp: the timed parts of Thor's moves -- a wind-up before a strike, the four punches of Storm Rush, a leap
 * that slams on landing, an ultimate's timeline. Each running move is a {@link Seq} ticked once a server tick (END) with its
 * age; it returns false when done. Several may run per player. Holds no entity or world references (only ids, looked up
 * each tick); cleared on server stop by {@code ServerStateReset}.
 */
public final class ThorSequences {
	/** One running move. {@code t} = ticks since it started (0 on the first tick after it was queued). */
	public interface Seq {
		boolean tick(ServerPlayer player, int t);

		/** A tag so a move can find / replace its own sequence (e.g. one leap at a time). */
		default String tag() {
			return "";
		}
	}

	private static final class Running {
		final Seq seq;
		final long start;

		Running(Seq seq, long start) {
			this.seq = seq;
			this.start = start;
		}
	}

	private static final Map<UUID, List<Running>> RUNNING = new HashMap<>();

	private ThorSequences() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ThorSequences::tick);
	}

	/** Starts {@code seq} for {@code player}; its first tick (t = 0) runs at the end of this server tick. */
	public static void start(ServerPlayer player, Seq seq) {
		RUNNING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Running(seq, player.level().getGameTime()));
	}

	public static boolean running(ServerPlayer player, String tag) {
		List<Running> list = RUNNING.get(player.getUUID());
		if (list == null) {
			return false;
		}
		for (Running r : list) {
			if (r.seq.tag().equals(tag)) {
				return true;
			}
		}
		return false;
	}

	public static void cancel(ServerPlayer player, String tag) {
		List<Running> list = RUNNING.get(player.getUUID());
		if (list != null) {
			list.removeIf(r -> r.seq.tag().equals(tag));
		}
	}

	private static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, List<Running>>> it = RUNNING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, List<Running>> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive() || p.isRemoved() || p.isSpectator()) {
				it.remove();
				continue;
			}
			// copy: a sequence may start another one
			List<Running> list = new ArrayList<>(e.getValue());
			List<Running> done = new ArrayList<>();
			for (Running r : list) {
				int t = (int) (p.level().getGameTime() - r.start);
				boolean keep;
				try {
					keep = t < 0 || r.seq.tick(p, t);
				} catch (RuntimeException ex) {
					com.projecthero.mod.ProjectHeroMod.LOGGER.error("Thor move sequence failed", ex);
					keep = false;
				}
				if (!keep || t > 20 * 30) {
					done.add(r);
				}
			}
			e.getValue().removeAll(done);
			if (e.getValue().isEmpty()) {
				it.remove();
			}
		}
	}

	public static void clearSessionState() {
		RUNNING.clear();
	}
}
