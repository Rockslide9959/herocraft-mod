package com.projecthero.mod.city;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.2 City part B: per-dimension City state that has to survive a restart -- each City's damage queue (grouped in
 * 16x16 columns with the time of their latest damage), its opt-out cells (renovated by a player, never repaired), when
 * its last Repair Worker was hired, plus the level-wide "repaired recently, drops nothing" positions and any repair
 * scaffolding still standing.
 */
public final class CitySavedData extends SavedData {
	private static final String FILE_ID = "projecthero_city";
	private static final SavedData.Factory<CitySavedData> FACTORY = new SavedData.Factory<>(CitySavedData::new, CitySavedData::load, null);

	private final Long2ObjectOpenHashMap<CityRecord> cities = new Long2ObjectOpenHashMap<>();
	/** pos -> game time until which a repaired block drops nothing. */
	private final Long2LongOpenHashMap noDrop = new Long2LongOpenHashMap();
	/** Repair scaffolding still in the world (removed when its trip ends, or on the next tick after a restart). */
	private final LongOpenHashSet scaffolds = new LongOpenHashSet();

	public static CitySavedData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	/** A damaged 16x16 column of one City. */
	public static final class DamageArea {
		public final long chunk;
		public long lastDamage;
		public long firstDamage;
		public final LongOpenHashSet positions = new LongOpenHashSet();

		DamageArea(long chunk) {
			this.chunk = chunk;
		}
	}

	public static final class CityRecord {
		public final long id;
		public final Long2ObjectOpenHashMap<DamageArea> areas = new Long2ObjectOpenHashMap<>();
		/** 4x4x4 cells (packed with {@link BlockPos#asLong} on the cell coordinates) a player renovated. */
		public final LongOpenHashSet optOut = new LongOpenHashSet();
		/** cell -> last time blueprint damage happened in it (for the renovation window; not saved). */
		final Long2LongOpenHashMap recentCellDamage = new Long2LongOpenHashMap();
		public long lastHire = -1;
		/** chunk -> last audit sweep time. */
		public final Long2LongOpenHashMap swept = new Long2LongOpenHashMap();

		CityRecord(long id) {
			this.id = id;
		}

		public int pendingBlocks() {
			int n = 0;
			for (DamageArea a : areas.values()) {
				n += a.positions.size();
			}
			return n;
		}

		/** Areas whose latest damage is at least {@code delay} ticks old, oldest first. */
		public List<DamageArea> dueAreas(long now, long delay) {
			List<DamageArea> out = new ArrayList<>();
			for (DamageArea a : areas.values()) {
				if (!a.positions.isEmpty() && now - a.lastDamage >= delay) {
					out.add(a);
				}
			}
			out.sort(Comparator.comparingLong(a -> a.firstDamage));
			return out;
		}
	}

	public static long cellOf(BlockPos pos) {
		return BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
	}

	public CityRecord city(long id) {
		return cities.computeIfAbsent(id, CityRecord::new);
	}

	public CityRecord cityIfPresent(long id) {
		return cities.get(id);
	}

	public Iterable<CityRecord> allCities() {
		return cities.values();
	}

	/** Records blueprint damage at {@code pos}. False if the cell is opted out. */
	public boolean addDamage(long cityId, BlockPos pos, long now) {
		CityRecord rec = city(cityId);
		long cell = cellOf(pos);
		if (rec.optOut.contains(cell)) {
			return false;
		}
		long chunk = ChunkPos.asLong(pos);
		DamageArea area = rec.areas.computeIfAbsent(chunk, DamageArea::new);
		if (area.positions.isEmpty()) {
			area.firstDamage = now;
		}
		area.positions.add(pos.asLong());
		area.lastDamage = now;
		rec.recentCellDamage.put(cell, now);
		setDirty();
		return true;
	}

	/** Drops {@code pos} from the queue (repaired, or no longer repairable). */
	public void removeDamage(long cityId, BlockPos pos) {
		CityRecord rec = cities.get(cityId);
		if (rec == null) {
			return;
		}
		long chunk = ChunkPos.asLong(pos);
		DamageArea area = rec.areas.get(chunk);
		if (area != null && area.positions.remove(pos.asLong())) {
			if (area.positions.isEmpty()) {
				rec.areas.remove(chunk);
			}
			setDirty();
		}
	}

	public boolean isQueued(long cityId, BlockPos pos) {
		CityRecord rec = cities.get(cityId);
		if (rec == null) {
			return false;
		}
		DamageArea area = rec.areas.get(ChunkPos.asLong(pos));
		return area != null && area.positions.contains(pos.asLong());
	}

	/** Marks the 4x4x4 cell around {@code pos} as renovated: its queued damage is dropped and it is never repaired. */
	public void optOut(long cityId, BlockPos pos) {
		CityRecord rec = city(cityId);
		long cell = cellOf(pos);
		if (!rec.optOut.add(cell)) {
			return;
		}
		int cx = pos.getX() >> 2;
		int cy = pos.getY() >> 2;
		int cz = pos.getZ() >> 2;
		for (DamageArea area : rec.areas.values()) {
			area.positions.removeIf(p -> BlockPos.getX(p) >> 2 == cx && BlockPos.getY(p) >> 2 == cy && BlockPos.getZ(p) >> 2 == cz);
		}
		rec.areas.values().removeIf(a -> a.positions.isEmpty());
		setDirty();
	}

	public boolean isOptedOut(long cityId, BlockPos pos) {
		CityRecord rec = cities.get(cityId);
		return rec != null && rec.optOut.contains(cellOf(pos));
	}

	public long lastCellDamage(long cityId, BlockPos pos) {
		CityRecord rec = cities.get(cityId);
		return rec == null ? Long.MIN_VALUE : rec.recentCellDamage.getOrDefault(cellOf(pos), Long.MIN_VALUE);
	}

	// ---------------------------------------------------------------- no-drop + scaffolds

	public void markRepaired(BlockPos pos, long until) {
		noDrop.put(pos.asLong(), until);
		setDirty();
	}

	/** True if a block repaired at {@code pos} still drops nothing at {@code now}. */
	public boolean noDropAt(BlockPos pos, long now) {
		long until = noDrop.getOrDefault(pos.asLong(), Long.MIN_VALUE);
		return until >= now;
	}

	/** The repaired block at {@code pos} was replaced: keep the no-drop for this tick's drops only, then forget it. */
	public void consumeRepaired(BlockPos pos, long now) {
		long key = pos.asLong();
		if (noDrop.containsKey(key)) {
			noDrop.put(key, Math.min(noDrop.get(key), now + 2));
			setDirty();
		}
	}

	public void pruneNoDrop(long now) {
		if (noDrop.long2LongEntrySet().removeIf(e -> e.getLongValue() < now)) {
			setDirty();
		}
	}

	public boolean hasNoDropEntries() {
		return !noDrop.isEmpty();
	}

	public LongOpenHashSet scaffolds() {
		return scaffolds;
	}

	// ---------------------------------------------------------------- save / load

	private static CitySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		CitySavedData d = new CitySavedData();
		ListTag list = tag.getList("cities", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag c = list.getCompound(i);
			CityRecord rec = d.city(c.getLong("id"));
			rec.lastHire = c.getLong("lastHire");
			for (long cell : c.getLongArray("optOut")) {
				rec.optOut.add(cell);
			}
			ListTag areas = c.getList("areas", Tag.TAG_COMPOUND);
			for (int j = 0; j < areas.size(); j++) {
				CompoundTag a = areas.getCompound(j);
				DamageArea area = new DamageArea(a.getLong("chunk"));
				area.lastDamage = a.getLong("last");
				area.firstDamage = a.getLong("first");
				for (long p : a.getLongArray("pos")) {
					area.positions.add(p);
				}
				if (!area.positions.isEmpty()) {
					rec.areas.put(area.chunk, area);
				}
			}
		}
		long[] nd = tag.getLongArray("noDrop");
		for (int i = 0; i + 1 < nd.length; i += 2) {
			d.noDrop.put(nd[i], nd[i + 1]);
		}
		for (long p : tag.getLongArray("scaffolds")) {
			d.scaffolds.add(p);
		}
		return d;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (CityRecord rec : cities.values()) {
			if (rec.id < 0) {
				continue; // registered (test) cities are never saved
			}
			CompoundTag c = new CompoundTag();
			c.putLong("id", rec.id);
			c.putLong("lastHire", rec.lastHire);
			c.putLongArray("optOut", rec.optOut.toLongArray());
			ListTag areas = new ListTag();
			for (DamageArea area : rec.areas.values()) {
				CompoundTag a = new CompoundTag();
				a.putLong("chunk", area.chunk);
				a.putLong("last", area.lastDamage);
				a.putLong("first", area.firstDamage);
				a.putLongArray("pos", area.positions.toLongArray());
				areas.add(a);
			}
			c.put("areas", areas);
			list.add(c);
		}
		tag.put("cities", list);
		long[] nd = new long[noDrop.size() * 2];
		int i = 0;
		for (Long2LongMap.Entry e : noDrop.long2LongEntrySet()) {
			nd[i++] = e.getLongKey();
			nd[i++] = e.getLongValue();
		}
		tag.putLongArray("noDrop", nd);
		tag.putLongArray("scaffolds", scaffolds.toLongArray());
		return tag;
	}

	/** Tests: forget a registered (test) City's record. */
	public void forget(long cityId) {
		cities.remove(cityId);
	}
}
