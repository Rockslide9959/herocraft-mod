package com.projecthero.mod.hideout;

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
 * v0.17.5: what this dimension knows about its villain hideouts, keyed by the position of each hideout's core block.
 * An entry appears the first time anything happens there (the mini-boss falls, the core is broken); a hideout nobody
 * has touched has no entry. A cleared hideout stays cleared for good; its raid calm runs {@link #CALM_TICKS} from the
 * moment it was cleared.
 *
 * <p>World storage, not a static map: nothing for {@code ServerStateReset} to clear.
 */
public final class HideoutState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_hideouts";
	/** Five in-game days. */
	public static final long CALM_TICKS = 5L * 24000L;
	/** The radius (blocks, horizontal) a cleared hideout calms its faction's raids in. */
	public static final double CALM_RADIUS = 512.0;

	public static final class Entry {
		public final BlockPos core;
		public final HideoutFaction faction;
		public boolean bossDead;
		public boolean cleared;
		public long clearedAt = -1L;

		Entry(BlockPos core, HideoutFaction faction) {
			this.core = core.immutable();
			this.faction = faction;
		}

		/** Game ticks of raid calm left at {@code now} (0 when none). */
		public long calmLeft(long now) {
			if (!cleared || clearedAt < 0) {
				return 0L;
			}
			return Math.max(0L, clearedAt + CALM_TICKS - now);
		}
	}

	private final Map<Long, Entry> entries = new LinkedHashMap<>();
	/** Not saved: when each player was last told about a refused raid (rate limit). Lives and dies with the level. */
	private final Map<java.util.UUID, Long> lastNotice = new java.util.HashMap<>();

	/** True (and remembered) when {@code player} has not been told anything in the last {@code gap} ticks. */
	public boolean mayNotify(java.util.UUID player, long now, long gap) {
		Long last = lastNotice.get(player);
		if (last != null && now - last < gap && now >= last) {
			return false;
		}
		lastNotice.put(player, now);
		return true;
	}

	private static final SavedData.Factory<HideoutState> FACTORY = new SavedData.Factory<>(
			HideoutState::new, HideoutState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static HideoutState get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	/** The entry for the hideout whose core is at {@code core}, or null when nothing has happened there yet. */
	public Entry at(BlockPos core) {
		return entries.get(core.asLong());
	}

	/** The entry for {@code core}, made if missing. */
	public Entry getOrCreate(BlockPos core, HideoutFaction faction) {
		Entry e = entries.get(core.asLong());
		if (e == null) {
			e = new Entry(core, faction);
			entries.put(core.asLong(), e);
			setDirty();
		}
		return e;
	}

	public List<Entry> all() {
		return Collections.unmodifiableList(new ArrayList<>(entries.values()));
	}

	public boolean bossDead(BlockPos core) {
		Entry e = at(core);
		return e != null && e.bossDead;
	}

	public boolean cleared(BlockPos core) {
		Entry e = at(core);
		return e != null && e.cleared;
	}

	public void markBossDead(BlockPos core, HideoutFaction faction) {
		Entry e = getOrCreate(core, faction);
		if (!e.bossDead) {
			e.bossDead = true;
			setDirty();
		}
	}

	/** Marks the hideout cleared at {@code now}. False if it already was (the calm is not restarted). */
	public boolean markCleared(BlockPos core, HideoutFaction faction, long now) {
		Entry e = getOrCreate(core, faction);
		e.bossDead = true;
		if (e.cleared) {
			setDirty();
			return false;
		}
		e.cleared = true;
		e.clearedAt = now;
		setDirty();
		return true;
	}

	/**
	 * The cleared {@code faction} hideout within {@code radius} (horizontal) of {@code pos} with the most calm left at
	 * {@code now}, or null.
	 */
	public Entry calming(HideoutFaction faction, BlockPos pos, double radius, long now) {
		Entry best = null;
		for (Entry e : entries.values()) {
			if (e.faction != faction || e.calmLeft(now) <= 0) {
				continue;
			}
			double dx = e.core.getX() - pos.getX();
			double dz = e.core.getZ() - pos.getZ();
			if (dx * dx + dz * dz <= radius * radius && (best == null || e.calmLeft(now) > best.calmLeft(now))) {
				best = e;
			}
		}
		return best;
	}

	/** The nearest cleared hideout of {@code faction} within {@code radius} (3D) of {@code pos}, or null. */
	public Entry clearedNear(HideoutFaction faction, BlockPos pos, double radius) {
		Entry best = null;
		double bestSq = radius * radius;
		for (Entry e : entries.values()) {
			if (e.faction != faction || !e.cleared) {
				continue;
			}
			double d = e.core.distSqr(pos);
			if (d <= bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	/** Test seam: forget everything. */
	public void resetForTest() {
		entries.clear();
		setDirty();
	}

	private static HideoutState load(CompoundTag tag, HolderLookup.Provider registries) {
		HideoutState s = new HideoutState();
		ListTag list = tag.getList("Hideouts", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			HideoutFaction f = HideoutFaction.byName(t.getString("Faction"));
			if (f == null) {
				continue;
			}
			Entry e = new Entry(BlockPos.of(t.getLong("Core")), f);
			e.bossDead = t.getBoolean("BossDead");
			e.cleared = t.getBoolean("Cleared");
			e.clearedAt = t.contains("ClearedAt") ? t.getLong("ClearedAt") : -1L;
			s.entries.put(e.core.asLong(), e);
		}
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Entry e : entries.values()) {
			CompoundTag t = new CompoundTag();
			t.putLong("Core", e.core.asLong());
			t.putString("Faction", e.faction.getSerializedName());
			t.putBoolean("BossDead", e.bossDead);
			t.putBoolean("Cleared", e.cleared);
			t.putLong("ClearedAt", e.clearedAt);
			list.add(t);
		}
		tag.put("Hideouts", list);
		return tag;
	}
}
