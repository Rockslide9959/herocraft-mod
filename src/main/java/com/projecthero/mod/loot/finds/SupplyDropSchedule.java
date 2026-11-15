package com.projecthero.mod.loot.finds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * When each player's next Supply Drop is due (overworld game time), kept as world saved data so the schedule survives
 * restarts and there is no static map to leak across worlds.
 */
public class SupplyDropSchedule extends SavedData {
	private static final String FILE_ID = "projecthero_supply_drops";
	private static final SavedData.Factory<SupplyDropSchedule> FACTORY =
			new SavedData.Factory<>(SupplyDropSchedule::new, SupplyDropSchedule::load,
			net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES); // same as ContagionState: a null type NPEs on load

	private final Map<UUID, Long> nextAt = new HashMap<>();

	public static SupplyDropSchedule get(ServerLevel overworld) {
		return overworld.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	/** The game time this player's next drop is due, or -1 if none is scheduled yet. */
	public long nextAt(UUID player) {
		return nextAt.getOrDefault(player, -1L);
	}

	public void schedule(UUID player, long gameTime) {
		nextAt.put(player, gameTime);
		setDirty();
	}

	private static SupplyDropSchedule load(CompoundTag tag, HolderLookup.Provider registries) {
		SupplyDropSchedule s = new SupplyDropSchedule();
		CompoundTag players = tag.getCompound("Players");
		for (String key : players.getAllKeys()) {
			try {
				s.nextAt.put(UUID.fromString(key), players.getLong(key));
			} catch (IllegalArgumentException ignored) {
				// a mangled key: that player is simply rescheduled
			}
		}
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag players = new CompoundTag();
		nextAt.forEach((id, t) -> players.putLong(id.toString(), t));
		tag.put("Players", players);
		return tag;
	}
}
