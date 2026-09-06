package com.projecthero.mod.symbiote.worldgen;

import com.projecthero.mod.ProjectHeroMod;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * "A Symbiote structure spawns its Symbiote exactly once": a persistent set of every symbiote meteor /
 * lab (keyed by its structure start's chunk position) that has already had its
 * {@link com.projecthero.mod.symbiote.entity.SymbioteEntity} placed. Shared by both structures.
 *
 * <p>Same key-set pattern as {@code SteelCrashSpawnState} / {@code CraterSpawnState}. Once placed the
 * entity persists via ordinary chunk saving and is discarded only when it bonds, so this only ever
 * answers "has this site had its one-time spawn" -- which is what stops a chunk reload placing a second.
 *
 * <p>The {@link SavedData.Factory}'s {@link DataFixTypes} must not be null -- a null there silently
 * discards the whole set on every load.
 */
public final class SymbioteSpawnState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_symbiote_sites";
	private static final String TAG_SPAWNED = "SpawnedSites";

	private final LongOpenHashSet spawned = new LongOpenHashSet();

	private static final SavedData.Factory<SymbioteSpawnState> FACTORY = new SavedData.Factory<>(
			SymbioteSpawnState::new, SymbioteSpawnState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static SymbioteSpawnState get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	private static SymbioteSpawnState load(CompoundTag tag, HolderLookup.Provider registries) {
		SymbioteSpawnState state = new SymbioteSpawnState();
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
