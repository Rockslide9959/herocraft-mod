package com.herocraft.mod.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where live {@link EventInstance}s actually live. Server-global {@link SavedData} on the overworld,
 * exactly like {@code MjolnirRegistry} / {@code StarkPlatformRegistry}.
 *
 * <p>Holding the instances here rather than in a {@code static} map is deliberate and is the whole
 * reason this class exists: the world owns them, they are written and read with the save, and when
 * the server stops they become garbage along with it. A static map would have to be cleared by hand
 * on {@code SERVER_STOPPED} or it would pin a dead {@code ServerLevel} and every entity in it -- the
 * exact leak {@code ServerStateReset} had to be written to mop up elsewhere in this mod.
 */
public final class EventSavedData extends SavedData {
	private static final String FILE_ID = HeroCraftMod.MOD_ID + "_world_events";

	private final List<EventInstance> events = new ArrayList<>();
	/** Bumped every time a raid finishes anywhere, so first-clear checks can be cheap. */
	private int completedCount;

	private static final SavedData.Factory<EventSavedData> FACTORY = new SavedData.Factory<>(
			EventSavedData::new, EventSavedData::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static EventSavedData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static EventSavedData get(ServerLevel level) {
		return get(level.getServer());
	}

	private static EventSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		EventSavedData data = new EventSavedData();
		data.completedCount = tag.getInt("Completed");
		ListTag list = tag.getList("Events", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			if (!t.hasUUID("Id")) {
				continue;
			}
			EventInstance instance = EventTypes.create(t.getString("Type"), t.getUUID("Id"));
			if (instance == null) {
				continue;
			}
			try {
				instance.load(t);
			} catch (RuntimeException e) {
				HeroCraftMod.LOGGER.warn("[HeroCraft] dropping unreadable world event record", e);
				continue;
			}
			// A finished event has nothing left to do; never resurrect one (spec section 26: a restart
			// must not restart completed waves or re-hand rewards).
			if (instance.state().active()) {
				data.events.add(instance);
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (EventInstance instance : events) {
			list.add(instance.save());
		}
		tag.put("Events", list);
		tag.putInt("Completed", completedCount);
		return tag;
	}

	// ---------------- access ----------------

	public List<EventInstance> events() {
		return events;
	}

	public void add(EventInstance instance) {
		events.add(instance);
		setDirty();
	}

	public void remove(UUID id) {
		if (events.removeIf(e -> e.id().equals(id))) {
			setDirty();
		}
	}

	public EventInstance byId(UUID id) {
		for (EventInstance e : events) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	public int completedCount() {
		return completedCount;
	}

	public void noteCompleted() {
		completedCount++;
		setDirty();
	}
}
