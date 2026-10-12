package com.projecthero.mod.hero;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.1: who holds each Hero-Tier power, server-wide -- power key -> (holder UUID -> last-known name). Kept in sync by
 * {@link UniquePowers} whatever the {@code projectheroUniquePowers} gamerule says, so turning the rule on finds the
 * table already filled in. A power may list several holders (duplicates made before the rule was turned on are kept;
 * any of them blocks a new grant). Also remembers holders an operator released while they were offline, so their power
 * is taken the next time they join ({@link #pendingRevokes}).
 *
 * <p>Stored on the overworld's {@code DimensionDataStorage}; written only when something changes.
 */
public final class UniquePowersData extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_unique_powers";
	private static final String TAG_HOLDERS = "Holders";
	private static final String TAG_PENDING = "PendingRevokes";
	private static final String TAG_KEY = "power";
	private static final String TAG_ID = "id";
	private static final String TAG_NAME = "name";

	/** See MjolnirRegistry: the DataFixTypes must not be null or the file silently fails to load. */
	private static final SavedData.Factory<UniquePowersData> FACTORY = new SavedData.Factory<>(
			UniquePowersData::new, UniquePowersData::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** power key -> holder UUID -> last-known name (sorted by key for a stable list output). */
	private final Map<String, LinkedHashMap<UUID, String>> holders = new TreeMap<>();
	/** power key -> players whose copy an operator released while they were offline. */
	private final Map<String, LinkedHashSet<UUID>> pending = new TreeMap<>();

	public static UniquePowersData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static UniquePowersData load(CompoundTag tag, HolderLookup.Provider registries) {
		UniquePowersData data = new UniquePowersData();
		ListTag list = tag.getList(TAG_HOLDERS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompound(i);
			if (e.hasUUID(TAG_ID)) {
				data.holders.computeIfAbsent(e.getString(TAG_KEY), k -> new LinkedHashMap<>()).put(e.getUUID(TAG_ID), e.getString(TAG_NAME));
			}
		}
		ListTag p = tag.getList(TAG_PENDING, Tag.TAG_COMPOUND);
		for (int i = 0; i < p.size(); i++) {
			CompoundTag e = p.getCompound(i);
			if (e.hasUUID(TAG_ID)) {
				data.pending.computeIfAbsent(e.getString(TAG_KEY), k -> new LinkedHashSet<>()).add(e.getUUID(TAG_ID));
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<String, LinkedHashMap<UUID, String>> power : holders.entrySet()) {
			for (Map.Entry<UUID, String> h : power.getValue().entrySet()) {
				CompoundTag e = new CompoundTag();
				e.putString(TAG_KEY, power.getKey());
				e.putUUID(TAG_ID, h.getKey());
				e.putString(TAG_NAME, h.getValue());
				list.add(e);
			}
		}
		tag.put(TAG_HOLDERS, list);
		ListTag p = new ListTag();
		for (Map.Entry<String, LinkedHashSet<UUID>> power : pending.entrySet()) {
			for (UUID id : power.getValue()) {
				CompoundTag e = new CompoundTag();
				e.putString(TAG_KEY, power.getKey());
				e.putUUID(TAG_ID, id);
				p.add(e);
			}
		}
		tag.put(TAG_PENDING, p);
		return tag;
	}

	/** The holders of {@code key} (read-only view; may be empty). */
	public Map<UUID, String> holders(String key) {
		Map<UUID, String> m = holders.get(key);
		return m == null ? Collections.emptyMap() : Collections.unmodifiableMap(m);
	}

	/** Every power that has at least one holder, with its holders (read-only). */
	public Map<String, LinkedHashMap<UUID, String>> all() {
		return Collections.unmodifiableMap(holders);
	}

	public void add(String key, UUID id, String name) {
		LinkedHashMap<UUID, String> m = holders.computeIfAbsent(key, k -> new LinkedHashMap<>());
		String previous = m.put(id, name);
		if (!name.equals(previous)) {
			setDirty();
		}
	}

	public void remove(String key, UUID id) {
		LinkedHashMap<UUID, String> m = holders.get(key);
		if (m != null && m.remove(id) != null) {
			if (m.isEmpty()) {
				holders.remove(key);
			}
			setDirty();
		}
	}

	/** Drops every holder of {@code key}; returns how many there were. */
	public int clear(String key) {
		LinkedHashMap<UUID, String> m = holders.remove(key);
		if (m == null) {
			return 0;
		}
		setDirty();
		return m.size();
	}

	/** Forget everything (tests). */
	public void clearAll() {
		holders.clear();
		pending.clear();
		setDirty();
	}

	public void addPendingRevoke(String key, UUID id) {
		if (pending.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(id)) {
			setDirty();
		}
	}

	/** Removes and returns the powers released from {@code id} while they were offline. */
	public Set<String> takePendingRevokes(UUID id) {
		Set<String> out = new LinkedHashSet<>();
		for (Map.Entry<String, LinkedHashSet<UUID>> e : pending.entrySet()) {
			if (e.getValue().remove(id)) {
				out.add(e.getKey());
			}
		}
		if (!out.isEmpty()) {
			pending.values().removeIf(Set::isEmpty);
			setDirty();
		}
		return out;
	}
}
