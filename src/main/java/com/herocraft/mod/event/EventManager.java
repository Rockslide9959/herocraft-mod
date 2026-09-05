package com.herocraft.mod.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The one entry point for starting, finding and ticking world events. Stateless itself -- everything
 * it touches lives in {@link EventSavedData} -- so there is nothing here to leak between worlds.
 *
 * <p>{@link #tick} runs every server tick but does almost nothing on most of them: it returns
 * immediately unless the framework's slow cadence is due, and even then it only walks the (normally
 * empty, at most a handful) list of live events.
 */
public final class EventManager {
	private EventManager() {
	}

	// ---------------- lifecycle ----------------

	/**
	 * Register and begin an event at {@code center}. Refuses if another <em>active</em> event of the
	 * same type is already running nearby, which is what stops a player spawning dozens of overlapping
	 * raids (spec section 41) and stops two raids merging into each other (section 56).
	 *
	 * @return true if the event was started
	 */
	public static boolean start(ServerLevel level, EventInstance instance, BlockPos center) {
		EventSavedData data = EventSavedData.get(level);
		int minGap = EventConfig.framework().minDistanceBetweenEvents;
		for (EventInstance existing : data.events()) {
			if (existing.state().active() && existing.typeId().equals(instance.typeId())
					&& existing.isAt(level, center, minGap)) {
				return false;
			}
		}
		instance.placeAt(level, center);
		data.add(instance);
		return true;
	}

	/** True if an active event of {@code typeId} is running within {@code radius} of {@code pos}. */
	public static boolean anyActiveNear(ServerLevel level, BlockPos pos, String typeId, double radius) {
		for (EventInstance e : EventSavedData.get(level).events()) {
			if (e.state().active() && e.typeId().equals(typeId) && e.isAt(level, pos, radius)) {
				return true;
			}
		}
		return false;
	}

	// ---------------- queries ----------------

	public static List<EventInstance> active(MinecraftServer server) {
		List<EventInstance> out = new ArrayList<>();
		for (EventInstance e : EventSavedData.get(server).events()) {
			if (e.state().active()) {
				out.add(e);
			}
		}
		return out;
	}

	public static EventInstance byId(MinecraftServer server, UUID id) {
		return EventSavedData.get(server).byId(id);
	}

	/** The active event this player currently counts as a participant of, or {@code null}. */
	public static EventInstance forPlayer(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		for (EventInstance e : EventSavedData.get(level).events()) {
			if (e.state().active() && e.dimension() == level.dimension()
					&& e.participants().isEligible(player.getUUID())) {
				return e;
			}
		}
		return null;
	}

	/** The active event nearest to {@code pos} within its own radius, or {@code null}. */
	public static EventInstance at(ServerLevel level, BlockPos pos) {
		EventInstance best = null;
		double bestSq = Double.MAX_VALUE;
		for (EventInstance e : EventSavedData.get(level).events()) {
			if (!e.state().active() || e.dimension() != level.dimension() || e.center() == null) {
				continue;
			}
			double d = e.center().distSqr(pos);
			if (d <= e.radius() * e.radius() && d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	/** The active event that owns {@code entity}, or {@code null}. Used by the death/loot hooks. */
	public static EventInstance owning(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		// Cheap pre-filter: event mobs all carry this tag, so a normal mob's death costs one set lookup
		// instead of walking the event list.
		if (!entity.getTags().contains(EventInstance.EVENT_TAG)) {
			return null;
		}
		for (EventInstance e : EventSavedData.get(level).events()) {
			if (e.owns(entity.getUUID())) {
				return e;
			}
		}
		return null;
	}

	// ---------------- ticking ----------------

	public static void tick(MinecraftServer server) {
		int interval = Math.max(1, EventConfig.framework().tickIntervalTicks);
		if (server.getTickCount() % interval != 0) {
			return;
		}
		EventSavedData data = EventSavedData.get(server);
		if (data.events().isEmpty()) {
			return;
		}
		boolean changed = false;
		Iterator<EventInstance> it = data.events().iterator();
		List<EventInstance> finished = new ArrayList<>();
		while (it.hasNext()) {
			EventInstance instance = it.next();
			ServerLevel level = instance.dimension() == null ? null : server.getLevel(instance.dimension());
			if (level == null) {
				continue; // that dimension isn't loaded this run -- leave the record alone
			}
			if (!instance.tick(level)) {
				finished.add(instance);
			}
			changed = true;
		}
		for (EventInstance instance : finished) {
			data.remove(instance.id());
		}
		if (changed) {
			data.setDirty();
		}
	}
}
