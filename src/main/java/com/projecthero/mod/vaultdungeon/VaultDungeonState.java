package com.projecthero.mod.vaultdungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.17.5: which Vault dungeons in this dimension have been cleared, keyed by each dungeon's <b>anchor</b> (the centre of
 * the Warden's arena, where Warden Kross is leashed). An entry appears when Kross falls (or his seals give up on him);
 * a dungeon nobody has beaten has no entry. Cleared stays cleared for good -- the dungeon is not reset, but every player
 * still gets their own roll from each opened Dungeon Vault (the Hero Vault remembers who has looted it).
 *
 * <p>World storage ({@code data/projecthero_vault_dungeons.dat}), not a static map: nothing for
 * {@code ServerStateReset}.
 */
public final class VaultDungeonState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_vault_dungeons";

	public static final class Entry {
		public final BlockPos anchor;
		public long clearedAt;

		Entry(BlockPos anchor, long clearedAt) {
			this.anchor = anchor.immutable();
			this.clearedAt = clearedAt;
		}
	}

	private final Map<Long, Entry> entries = new LinkedHashMap<>();

	private static final SavedData.Factory<VaultDungeonState> FACTORY = new SavedData.Factory<>(
			VaultDungeonState::new, VaultDungeonState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static VaultDungeonState get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public boolean cleared(BlockPos anchor) {
		return anchor != null && entries.containsKey(anchor.asLong());
	}

	/** Marks the dungeon at {@code anchor} cleared. False if it already was. */
	public boolean markCleared(BlockPos anchor, long now) {
		if (entries.containsKey(anchor.asLong())) {
			return false;
		}
		entries.put(anchor.asLong(), new Entry(anchor, now));
		setDirty();
		return true;
	}

	/** The nearest cleared dungeon anchor within {@code radius} (3D) of {@code pos}, or null. */
	public Entry clearedNear(BlockPos pos, double radius) {
		Entry best = null;
		double bestSq = radius * radius;
		for (Entry e : entries.values()) {
			double d = e.anchor.distSqr(pos);
			if (d <= bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	public List<Entry> all() {
		return Collections.unmodifiableList(new ArrayList<>(entries.values()));
	}

	/** Test seam: forget everything. */
	public void resetForTest() {
		entries.clear();
		setDirty();
	}

	private static VaultDungeonState load(CompoundTag tag, HolderLookup.Provider registries) {
		VaultDungeonState s = new VaultDungeonState();
		ListTag list = tag.getList("Dungeons", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			Entry e = new Entry(BlockPos.of(t.getLong("Anchor")), t.getLong("ClearedAt"));
			s.entries.put(e.anchor.asLong(), e);
		}
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Entry e : entries.values()) {
			CompoundTag t = new CompoundTag();
			t.putLong("Anchor", e.anchor.asLong());
			t.putLong("ClearedAt", e.clearedAt);
			list.add(t);
		}
		tag.put("Dungeons", list);
		return tag;
	}
}
