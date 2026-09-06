package com.projecthero.mod.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.raid.SupervillainRaid;
import com.projecthero.mod.event.raid.ZombieRaid;

/**
 * The tiny registry that lets {@link EventSavedData} turn a saved {@code "Type"} string back into a
 * live {@link EventInstance}. Adding a future event (End invasion, robot uprising, world boss) is one
 * {@link #register} call plus the event class itself -- nothing else in the framework changes.
 */
public final class EventTypes {
	private static final Map<String, Function<UUID, EventInstance>> FACTORIES = new LinkedHashMap<>();

	public static final String ZOMBIE_RAID = "zombie_raid";
	public static final String SUPERVILLAIN_RAID = SupervillainRaid.TYPE_ID;

	private EventTypes() {
	}

	public static void initialize() {
		register(ZOMBIE_RAID, ZombieRaid::new);
		register(SUPERVILLAIN_RAID, SupervillainRaid::new);
	}

	public static void register(String typeId, Function<UUID, EventInstance> factory) {
		if (FACTORIES.putIfAbsent(typeId, factory) != null) {
			throw new IllegalStateException("duplicate event type " + typeId);
		}
	}

	/** @return a fresh, unplaced instance of {@code typeId}, or {@code null} if unknown. */
	public static EventInstance create(String typeId, UUID id) {
		Function<UUID, EventInstance> factory = FACTORIES.get(typeId);
		if (factory == null) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] unknown world-event type '{}' -- dropping it", typeId);
			return null;
		}
		return factory.apply(id);
	}

	public static boolean isRegistered(String typeId) {
		return FACTORIES.containsKey(typeId);
	}
}
