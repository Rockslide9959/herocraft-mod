package com.projecthero.mod.sentry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.17.5: every block the Void's ring swallowed in one dimension, waiting to come back ({@code projecthero_void_matter}, a per-dimension
 * SavedData, so nothing of it is static and it survives a server stop). Each entry is a position, the original block state, the
 * Sentry whose Void ate it and when it is due back (0 = still being eaten: the takeover is running). Block entities are never eaten,
 * so no block entity data is ever kept. Under {@code projectheroVoidGriefing} nothing is recorded at all.
 */
public final class VoidMatterData extends SavedData {
	private static final String FILE_ID = "projecthero_void_matter";
	private static final SavedData.Factory<VoidMatterData> FACTORY = new SavedData.Factory<>(
			VoidMatterData::new, VoidMatterData::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** One swallowed block. */
	public static final class Entry {
		public final BlockPos pos;
		public final BlockState state;
		public final UUID owner;
		public long dueAt;

		Entry(BlockPos pos, BlockState state, UUID owner, long dueAt) {
			this.pos = pos;
			this.state = state;
			this.owner = owner;
			this.dueAt = dueAt;
		}
	}

	private final Map<Long, Entry> entries = new LinkedHashMap<>();

	public VoidMatterData() {
	}

	public static VoidMatterData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public int size() {
		return entries.size();
	}

	public boolean has(BlockPos pos) {
		return entries.containsKey(pos.asLong());
	}

	public Entry entry(BlockPos pos) {
		return entries.get(pos.asLong());
	}

	/** Records a swallowed block (the first record of a position wins: that is the original). */
	public void record(BlockPos pos, BlockState state, UUID owner) {
		if (entries.putIfAbsent(pos.asLong(), new Entry(pos.immutable(), state, owner, 0L)) == null) {
			setDirty();
		}
	}

	/** The takeover ended: everything this Sentry's Void ate comes back from {@code due} on. Returns how many. */
	public int schedule(UUID owner, long due) {
		int n = 0;
		for (Entry e : entries.values()) {
			if (e.owner.equals(owner) && e.dueAt == 0L) {
				e.dueAt = due;
				n++;
			}
		}
		if (n > 0) {
			setDirty();
		}
		return n;
	}

	/** Pending entries (dueAt 0) whose owner {@code stillEating} says no longer has a takeover: give them a due time. */
	public void adoptOrphans(java.util.function.Predicate<UUID> stillEating, long due) {
		boolean changed = false;
		for (Entry e : entries.values()) {
			if (e.dueAt == 0L && !stillEating.test(e.owner)) {
				e.dueAt = due;
				changed = true;
			}
		}
		if (changed) {
			setDirty();
		}
	}

	/** Up to {@code max} entries due by {@code now}. */
	public List<Entry> due(long now, int max) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries.values()) {
			if (e.dueAt != 0L && e.dueAt <= now) {
				out.add(e);
				if (out.size() >= max) {
					break;
				}
			}
		}
		return out;
	}

	public void remove(Entry e) {
		if (entries.remove(e.pos.asLong()) != null) {
			setDirty();
		}
	}

	/** Forgets everything one Sentry ate (the gamerule turned on mid-way: what's gone stays gone). */
	public int forget(UUID owner) {
		int n = 0;
		for (Iterator<Entry> it = entries.values().iterator(); it.hasNext();) {
			if (it.next().owner.equals(owner)) {
				it.remove();
				n++;
			}
		}
		if (n > 0) {
			setDirty();
		}
		return n;
	}

	private static VoidMatterData load(CompoundTag tag, HolderLookup.Provider registries) {
		VoidMatterData d = new VoidMatterData();
		var blocks = registries.lookupOrThrow(Registries.BLOCK);
		for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag) t;
			BlockPos pos = BlockPos.of(c.getLong("p"));
			BlockState state = NbtUtils.readBlockState(blocks, c.getCompound("s"));
			if (state.isAir() || !c.hasUUID("o")) {
				continue;
			}
			d.entries.put(pos.asLong(), new Entry(pos, state, c.getUUID("o"), c.getLong("d")));
		}
		return d;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Entry e : entries.values()) {
			CompoundTag c = new CompoundTag();
			c.putLong("p", e.pos.asLong());
			c.put("s", NbtUtils.writeBlockState(e.state));
			c.putUUID("o", e.owner);
			c.putLong("d", e.dueAt);
			list.add(c);
		}
		tag.put("entries", list);
		return tag;
	}
}
