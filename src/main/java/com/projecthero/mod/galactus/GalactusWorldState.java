package com.projecthero.mod.galactus;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.1: the world's Galactus record (overworld saved data): whether the cosmic signal can find this world yet (it has
 * beaten the Ultron Contagion finale or a Frost Giant Assault), the last in-game day the signal was rolled, and how many
 * times he has been driven off.
 */
public final class GalactusWorldState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_galactus_world";

	public boolean frostBeaten;
	public long lastRollDay = -1;
	public int retreats;
	public int consumed;

	private static final SavedData.Factory<GalactusWorldState> FACTORY = new SavedData.Factory<>(GalactusWorldState::new, GalactusWorldState::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static GalactusWorldState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	/** Has this world earned the cosmic signal (the Ultron Contagion finale won, or a Frost Giant Assault broken)? */
	public static boolean unlocked(MinecraftServer server) {
		return get(server).frostBeaten || com.projecthero.mod.ultron.contagion.ContagionState.get(server).omegaBeaten;
	}

	/** Called by the Frost Giant Assault when it is won. */
	public static void markFrostBeaten(MinecraftServer server) {
		GalactusWorldState s = get(server);
		if (!s.frostBeaten) {
			s.frostBeaten = true;
			s.setDirty();
		}
	}

	private static GalactusWorldState load(CompoundTag tag, HolderLookup.Provider registries) {
		GalactusWorldState s = new GalactusWorldState();
		s.frostBeaten = tag.getBoolean("FrostBeaten");
		s.lastRollDay = tag.contains("LastRollDay") ? tag.getLong("LastRollDay") : -1;
		s.retreats = tag.getInt("Retreats");
		s.consumed = tag.getInt("Consumed");
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("FrostBeaten", frostBeaten);
		tag.putLong("LastRollDay", lastRollDay);
		tag.putInt("Retreats", retreats);
		tag.putInt("Consumed", consumed);
		return tag;
	}
}
