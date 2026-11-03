package com.projecthero.mod.doomsday;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.17.2: per-world Doomsday bookkeeping (overworld saved data): whether the world has earned the natural impact (beaten
 * Galactus -- {@code GalactusWorldState.retreats} -- or the Apokolips Invasion, {@link #darkseidBeaten}), the last in-game
 * day the impact was rolled, and the tallies.
 */
public final class DoomsdayWorldState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_doomsday_world";

	public boolean darkseidBeaten;
	public long lastRollDay = -1;
	public int banished;
	public int lost;

	private static final SavedData.Factory<DoomsdayWorldState> FACTORY = new SavedData.Factory<>(DoomsdayWorldState::new, DoomsdayWorldState::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static DoomsdayWorldState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	/** Has this world earned the natural impact (Galactus driven off, or the Apokolips Invasion won)? */
	public static boolean unlocked(MinecraftServer server) {
		return get(server).darkseidBeaten || com.projecthero.mod.galactus.GalactusWorldState.get(server).retreats > 0;
	}

	/** Called by the Apokolips Invasion when it is won (Doomsday beat Darkseid in <i>Hunter/Prey</i>). */
	public static void markDarkseidBeaten(MinecraftServer server) {
		DoomsdayWorldState s = get(server);
		if (!s.darkseidBeaten) {
			s.darkseidBeaten = true;
			s.setDirty();
		}
	}

	private static DoomsdayWorldState load(CompoundTag tag, HolderLookup.Provider registries) {
		DoomsdayWorldState s = new DoomsdayWorldState();
		s.darkseidBeaten = tag.getBoolean("DarkseidBeaten");
		s.lastRollDay = tag.contains("LastRollDay") ? tag.getLong("LastRollDay") : -1;
		s.banished = tag.getInt("Banished");
		s.lost = tag.getInt("Lost");
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("DarkseidBeaten", darkseidBeaten);
		tag.putLong("LastRollDay", lastRollDay);
		tag.putInt("Banished", banished);
		tag.putInt("Lost", lost);
		return tag;
	}
}
