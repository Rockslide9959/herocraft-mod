package com.projecthero.mod.thoracquire;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.17.7: "is it storming where the hammer stands", and a running count of thunderstorms (the Trial of Asgard allows one
 * attempt per storm per player). The count goes up each time the overworld starts thundering and is saved with the world.
 */
public final class Storms {
	/** Tests force the weather with this instead of waiting for the sky (null = the real weather). */
	public static volatile Boolean forced;

	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_storms";

	private Storms() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(Storms::tick);
	}

	/** Thundering at {@code pos} in {@code level}: needs a sky (the Nether and the End never storm). */
	public static boolean isStorming(ServerLevel level, BlockPos pos) {
		Boolean f = forced;
		if (f != null) {
			return f;
		}
		return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && level.isThundering();
	}

	/** The number of the current (or most recent) thunderstorm. */
	public static long current(MinecraftServer server) {
		return Data.get(server).count;
	}

	/** Test hook: a new storm begins. */
	public static void beginNewStormForTest(MinecraftServer server) {
		Data d = Data.get(server);
		d.count++;
		d.setDirty();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		boolean now = forced != null ? forced : overworld.isThundering();
		Data d = Data.get(server);
		if (now && !d.wasThundering) {
			d.count++;
		}
		if (now != d.wasThundering) {
			d.wasThundering = now;
			d.setDirty();
		}
	}

	static final class Data extends SavedData {
		long count;
		boolean wasThundering;

		private static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load,
				DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

		static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
		}

		private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data d = new Data();
			d.count = tag.getLong("Count");
			d.wasThundering = tag.getBoolean("WasThundering");
			return d;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putLong("Count", count);
			tag.putBoolean("WasThundering", wasThundering);
			return tag;
		}
	}
}
