package com.projecthero.mod.maxsteel.worldgen;

import com.projecthero.mod.ProjectHeroMod;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * "A Steel Crash Site spawns its Steel exactly once": a persistent set of every crash site (keyed by
 * its structure start's chunk position -- one per site, stable across save/load) that has already had
 * its Steel entity placed.
 *
 * <p>Same pattern as {@code CraterSpawnState}: just a key set. Once placed, the {@link
 * com.projecthero.mod.maxsteel.entity.SteelEntity} persists like any other entity via ordinary chunk
 * saving, and it is discarded only when it bonds -- so this only ever answers "has this site had its
 * one-time spawn," which is what stops a chunk unload/reload (or server restart) placing a second one.
 *
 * <p>The {@link SavedData.Factory}'s {@link DataFixTypes} must not be null even though this data needs
 * no fixing -- a null there silently discards the whole set on every load.
 */
public final class SteelCrashSpawnState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_steel_crash_sites";
	private static final String TAG_SPAWNED = "SpawnedSites";

	private final LongOpenHashSet spawned = new LongOpenHashSet();

	private static final SavedData.Factory<SteelCrashSpawnState> FACTORY = new SavedData.Factory<>(
			SteelCrashSpawnState::new, SteelCrashSpawnState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static SteelCrashSpawnState get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	private static SteelCrashSpawnState load(CompoundTag tag, HolderLookup.Provider registries) {
		SteelCrashSpawnState state = new SteelCrashSpawnState();
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

	public boolean hasSpawned(long siteKey) {
		return spawned.contains(siteKey);
	}

	public void markSpawned(long siteKey) {
		if (spawned.add(siteKey)) {
			setDirty();
		}
	}
}
