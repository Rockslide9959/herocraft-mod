package com.projecthero.mod.sentry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

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
 * v0.17.5: every block the Void swallowed in one dimension, waiting to come back ({@code projecthero_void_matter}, a per-dimension
 * SavedData, so nothing of it is static and it survives a server stop). Each entry is a position, the original block state, the
 * Sentry whose Void ate it and when it is due back (0 = still being eaten: the takeover is running). Block entities are never eaten,
 * so no block entity data is ever kept. Under {@code projectheroVoidGriefing} nothing is recorded at all.
 *
 * <p>Rework 2 (2026-11-17): the takeover eats tens of thousands of blocks now, so
 * <ul>
 *   <li>the file is a palette (each block state written once) plus flat arrays; the old per-entry list still loads;</li>
 *   <li>what is scheduled sits in a queue in due order, and the restore takes from its front (an entry in an unloaded chunk goes to the
 *   back and waits), so a tick never scans the whole map;</li>
 *   <li>the blocks fading to black right now (position -> the tick they go) and the per-column bookkeeping of the disc (each column's
 *   original surface, how deep it has been eaten) are kept here too -- per world, never saved, never static. After a restart a block
 *   that was mid-fade simply stays (its record puts the same block back later).</li>
 * </ul>
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
	/** Scheduled entries in due order (transient; rebuilt on load). */
	private final ArrayDeque<Entry> scheduled = new ArrayDeque<>();

	// ---- transient: the blocks fading right now, and the disc's columns
	/** Positions fading to black (never saved). */
	final LongOpenHashSet decaying = new LongOpenHashSet();
	/** Game tick -> the positions that go at that tick. */
	final Long2ObjectOpenHashMap<LongArrayList> decayDue = new Long2ObjectOpenHashMap<>();
	/** The last tick whose decays were processed. */
	long decayProcessed = Long.MIN_VALUE;
	/** Column (x, z packed) -> its original ground height (the first solid non-log, non-leaf block from the top). */
	final Long2IntOpenHashMap columnSurface = new Long2IntOpenHashMap();
	/** Column -> the depth it is finished to (nothing left to eat down to that depth). */
	final Long2IntOpenHashMap columnDone = new Long2IntOpenHashMap();
	/** Positions that started decaying this tick, waiting to be sent: ground (slow fade) and carved (fast). */
	final LongArrayList pendingGround = new LongArrayList();
	final LongArrayList pendingCarve = new LongArrayList();
	/** Measured cost of the decay work (ns) since the last report. */
	long statNanos;
	long statMaxNanos;
	long statSumNanos;
	int statTicks;
	int statStarted;
	int statRemoved;
	int statRestored;

	public VoidMatterData() {
		columnSurface.defaultReturnValue(Integer.MIN_VALUE);
		columnDone.defaultReturnValue(-1);
	}

	public static VoidMatterData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public int size() {
		return entries.size();
	}

	public int scheduledSize() {
		return scheduled.size();
	}

	public boolean has(BlockPos pos) {
		return entries.containsKey(pos.asLong());
	}

	public Entry entry(BlockPos pos) {
		return entries.get(pos.asLong());
	}

	/** Whether the block at {@code pos} is fading to black right now. */
	public boolean decaying(BlockPos pos) {
		return decaying.contains(pos.asLong());
	}

	public int decayingCount() {
		return decaying.size();
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
				scheduled.addLast(e);
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
		UUID lastOwner = null;
		boolean lastEating = true;
		for (Entry e : entries.values()) {
			if (e.dueAt != 0L) {
				continue;
			}
			if (!e.owner.equals(lastOwner)) {
				lastOwner = e.owner;
				lastEating = stillEating.test(e.owner);
			}
			if (!lastEating) {
				e.dueAt = due;
				scheduled.addLast(e);
				changed = true;
			}
		}
		if (changed) {
			setDirty();
		}
	}

	/** Up to {@code max} entries due by {@code now} (a copy; tests and the one-go restore). */
	public List<Entry> due(long now, int max) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : scheduled) {
			if (e.dueAt != 0L && e.dueAt <= now && entries.get(e.pos.asLong()) == e) {
				out.add(e);
				if (out.size() >= max) {
					break;
				}
			}
		}
		return out;
	}

	/**
	 * The restore's queue: the next scheduled entry due by {@code now}, or null. Stale ones (already restored or forgotten) are dropped
	 * on the way. The caller either restores it ({@link #remove}) or sends it to the back ({@link #requeue}).
	 */
	Entry pollDue(long now) {
		while (!scheduled.isEmpty()) {
			Entry e = scheduled.peekFirst();
			if (entries.get(e.pos.asLong()) != e) {
				scheduled.pollFirst();
				continue;
			}
			if (e.dueAt > now) {
				return null;
			}
			scheduled.pollFirst();
			return e;
		}
		return null;
	}

	/** An entry whose chunk is not loaded waits at the back of the queue. */
	void requeue(Entry e) {
		scheduled.addLast(e);
	}

	public void remove(Entry e) {
		if (entries.remove(e.pos.asLong(), e)) {
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

	/** No takeover is eating this world any more: drop the disc's column bookkeeping (the decays still finish). */
	void forgetColumns() {
		columnSurface.clear();
		columnDone.clear();
		columnSurface.trim();
		columnDone.trim();
	}

	private static VoidMatterData load(CompoundTag tag, HolderLookup.Provider registries) {
		VoidMatterData d = new VoidMatterData();
		var blocks = registries.lookupOrThrow(Registries.BLOCK);
		if (tag.contains("palette", Tag.TAG_LIST)) {
			ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
			BlockState[] palette = new BlockState[paletteTag.size()];
			for (int i = 0; i < palette.length; i++) {
				palette[i] = NbtUtils.readBlockState(blocks, paletteTag.getCompound(i));
			}
			ListTag ownersTag = tag.getList("owners", Tag.TAG_INT_ARRAY);
			UUID[] owners = new UUID[ownersTag.size()];
			for (int i = 0; i < owners.length; i++) {
				owners[i] = NbtUtils.loadUUID(ownersTag.get(i));
			}
			long[] pos = tag.getLongArray("pos");
			int[] st = tag.getIntArray("st");
			int[] own = tag.getIntArray("own");
			long[] due = tag.getLongArray("due");
			int n = Math.min(Math.min(pos.length, st.length), Math.min(own.length, due.length));
			for (int i = 0; i < n; i++) {
				if (st[i] < 0 || st[i] >= palette.length || own[i] < 0 || own[i] >= owners.length || palette[st[i]].isAir()) {
					continue;
				}
				d.put(new Entry(BlockPos.of(pos[i]), palette[st[i]], owners[own[i]], due[i]));
			}
		} else {
			// the first v0.17.5 format: one compound per entry
			for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
				CompoundTag c = (CompoundTag) t;
				BlockState state = NbtUtils.readBlockState(blocks, c.getCompound("s"));
				if (state.isAir() || !c.hasUUID("o")) {
					continue;
				}
				d.put(new Entry(BlockPos.of(c.getLong("p")), state, c.getUUID("o"), c.getLong("d")));
			}
		}
		// the restore queue, in due order
		List<Entry> sched = new ArrayList<>();
		for (Entry e : d.entries.values()) {
			if (e.dueAt != 0L) {
				sched.add(e);
			}
		}
		sched.sort((a, b) -> Long.compare(a.dueAt, b.dueAt));
		d.scheduled.addAll(sched);
		return d;
	}

	private void put(Entry e) {
		entries.put(e.pos.asLong(), e);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		Map<BlockState, Integer> palette = new LinkedHashMap<>();
		Map<UUID, Integer> owners = new LinkedHashMap<>();
		int n = entries.size();
		long[] pos = new long[n];
		int[] st = new int[n];
		int[] own = new int[n];
		long[] due = new long[n];
		int i = 0;
		for (Entry e : entries.values()) {
			pos[i] = e.pos.asLong();
			st[i] = palette.computeIfAbsent(e.state, k -> palette.size());
			own[i] = owners.computeIfAbsent(e.owner, k -> owners.size());
			due[i] = e.dueAt;
			i++;
		}
		ListTag paletteTag = new ListTag();
		for (BlockState s : palette.keySet()) {
			paletteTag.add(NbtUtils.writeBlockState(s));
		}
		ListTag ownersTag = new ListTag();
		for (UUID u : owners.keySet()) {
			ownersTag.add(NbtUtils.createUUID(u));
		}
		tag.put("palette", paletteTag);
		tag.put("owners", ownersTag);
		tag.putLongArray("pos", pos);
		tag.putIntArray("st", st);
		tag.putIntArray("own", own);
		tag.putLongArray("due", due);
		return tag;
	}
}
