package com.projecthero.mod.ultron;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.1: Ultron's world-level memory (a {@link SavedData} on the overworld, file {@code data/projecthero_ultron_world.dat}).
 * It holds one thing for now: {@code ultron_first_defeat_time} -- the overworld game time (ticks, {@code getGameTime()})
 * at which the Ultron Sentry first fell in a running uprising in this world, or {@code -1} if he never has.
 *
 * <p>This is the hook for Part B, the Ultron Contagion: it reads {@link #firstDefeatTime} and starts its timer from it.
 * The flag is written once and never moved: a second clear leaves it alone ({@link #recordDefeat} returns false), and
 * the Sentry's last line on that first clear is "You can't kill an idea. I'll see you soon."
 *
 * <p>No static state: the data lives in the world's storage (nothing to add to {@code ServerStateReset}).
 */
public final class UltronWorldState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_ultron_world";
	public static final String TAG_FIRST_DEFEAT = "ultron_first_defeat_time";
	public static final String TAG_DEFEATS = "ultron_defeats";

	private long firstDefeatTime = -1L;
	private int defeats;

	// as elsewhere in the mod: the DataFixTypes must not be null (a null silently discards the data on load)
	private static final SavedData.Factory<UltronWorldState> FACTORY = new SavedData.Factory<>(
			UltronWorldState::new, UltronWorldState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static UltronWorldState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static UltronWorldState get(ServerLevel level) {
		return get(level.getServer());
	}

	private static UltronWorldState load(CompoundTag tag, HolderLookup.Provider registries) {
		UltronWorldState s = new UltronWorldState();
		s.firstDefeatTime = tag.contains(TAG_FIRST_DEFEAT) ? tag.getLong(TAG_FIRST_DEFEAT) : -1L;
		s.defeats = tag.getInt(TAG_DEFEATS);
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putLong(TAG_FIRST_DEFEAT, firstDefeatTime);
		tag.putInt(TAG_DEFEATS, defeats);
		return tag;
	}

	// ---------------------------------------------------------------- public API (Part B reads these)

	/** The overworld game time of Ultron's first defeat in this world, or -1 if he has never been beaten. */
	public static long firstDefeatTime(ServerLevel level) {
		return get(level).firstDefeatTime;
	}

	/** Has Ultron ever been beaten in this world? */
	public static boolean defeatedOnce(ServerLevel level) {
		return firstDefeatTime(level) >= 0L;
	}

	/** How many times the Sentry has fallen in a running uprising in this world. */
	public static int defeats(ServerLevel level) {
		return get(level).defeats;
	}

	/**
	 * Records a defeat (the Sentry fell in a running uprising). Returns true when it was the world's first -- the flag is
	 * set to the overworld's current game time then, and never again.
	 */
	public static boolean recordDefeat(ServerLevel level) {
		UltronWorldState s = get(level);
		s.defeats++;
		boolean first = s.firstDefeatTime < 0L;
		if (first) {
			s.firstDefeatTime = level.getServer().overworld().getGameTime();
		}
		s.setDirty();
		return first;
	}

	/** Test / admin hook: forget every defeat (the next one is a "first" again). */
	public static void resetForTest(ServerLevel level) {
		UltronWorldState s = get(level);
		s.firstDefeatTime = -1L;
		s.defeats = 0;
		s.setDirty();
	}
}
