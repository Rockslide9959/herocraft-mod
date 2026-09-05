package com.herocraft.mod.worldgen;

import com.herocraft.mod.HeroCraftMod;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The single guarantee behind "a Mjolnir Crater spawns its hammer exactly once": a persistent set of
 * every crater (keyed by its structure start's chunk position -- one per crater, stable across
 * save/load) that has already had its natural Mjolnir placed.
 *
 * <p>Deliberately just a key set, not a position/UUID record: the crater's own {@link MjolnirEntity}
 * -- once placed -- persists like any other entity via ordinary chunk saving, so there is nothing
 * else here that needs tracking. This only answers "has this crater already had its one-time spawn,
 * yes or no," which is exactly what stops a chunk unload/reload cycle (or a server restart) from
 * placing a second hammer -- see {@link CraterAmbience#onChunkLoad}.
 */
public final class CraterSpawnState extends SavedData {
	private static final String FILE_ID = HeroCraftMod.MOD_ID + "_craters";
	private static final String TAG_SPAWNED = "SpawnedCraters";

	private final LongOpenHashSet spawned = new LongOpenHashSet();

	// See MjolnirRegistry's javadoc on this same pattern: the DataFixTypes must not be null, even
	// though this data needs no fixing -- a null here would silently discard the whole set on every
	// load instead of crashing.
	private static final SavedData.Factory<CraterSpawnState> FACTORY = new SavedData.Factory<>(
			CraterSpawnState::new, CraterSpawnState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static CraterSpawnState get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	private static CraterSpawnState load(CompoundTag tag, HolderLookup.Provider registries) {
		CraterSpawnState state = new CraterSpawnState();
		for (long key : tag.getLongArray(TAG_SPAWNED)) {
			state.spawned.add(key);
		}
		return state;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putLongArray(TAG_SPAWNED, spawned.toLongArray());
		return tag;
	}

	public boolean hasSpawned(long craterKey) {
		return spawned.contains(craterKey);
	}

	public void markSpawned(long craterKey) {
		if (spawned.add(craterKey)) {
			setDirty();
		}
	}
}
