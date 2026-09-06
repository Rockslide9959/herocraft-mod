package com.projecthero.mod.event.raid;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Post-raid cooldowns for the Supervillain Village Raid. After a village survives (or falls to) a
 * Supervillain Raid it is immune to another for {@link EventConfig.SupervillainRaid#villageRaidCooldownDays}
 * Minecraft days -- so a Pillager Spy cannot chain-mark the same village and the event stays a rare
 * occasion.
 *
 * <p>Server-global {@link SavedData} on the overworld, exactly like {@code EventSavedData}: the world
 * owns it, it is written and read with the save, and it becomes garbage when the server stops. The
 * "village" is identified by the raid centre snapped to a 64-block grid, so any spy hit anywhere in
 * the same village resolves to the same cooldown cell.
 */
public final class SupervillainVillages extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_supervillain_villages";
	private static final long DAY_TICKS = 24000L;
	private static final int GRID = 64;

	private final Map<Long, Long> cooldownEndByCell = new HashMap<>();

	private static final SavedData.Factory<SupervillainVillages> FACTORY = new SavedData.Factory<>(
			SupervillainVillages::new, SupervillainVillages::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static SupervillainVillages get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static SupervillainVillages get(ServerLevel level) {
		return get(level.getServer());
	}

	private static long cell(BlockPos center) {
		return BlockPos.asLong(Math.floorDiv(center.getX(), GRID), 0, Math.floorDiv(center.getZ(), GRID));
	}

	/** True if this village may be marked for a new Supervillain Raid right now. */
	public boolean available(ServerLevel level, BlockPos center) {
		prune(level.getGameTime());
		Long end = cooldownEndByCell.get(cell(center));
		return end == null || level.getGameTime() >= end;
	}

	/** Seconds until this village's cooldown lifts, or 0 if it is available. */
	public long cooldownSecondsRemaining(ServerLevel level, BlockPos center) {
		Long end = cooldownEndByCell.get(cell(center));
		if (end == null || level.getGameTime() >= end) {
			return 0;
		}
		return (end - level.getGameTime()) / 20L;
	}

	/** Begin the post-raid cooldown for the village around {@code center}. */
	public void startCooldown(ServerLevel level, BlockPos center) {
		long days = Math.max(0, EventConfig.supervillain().villageRaidCooldownDays);
		cooldownEndByCell.put(cell(center), level.getGameTime() + days * DAY_TICKS);
		setDirty();
	}

	/** Clear a village's cooldown (debug command). */
	public void clearCooldown(BlockPos center) {
		if (cooldownEndByCell.remove(cell(center)) != null) {
			setDirty();
		}
	}

	private void prune(long now) {
		Iterator<Map.Entry<Long, Long>> it = cooldownEndByCell.entrySet().iterator();
		boolean changed = false;
		while (it.hasNext()) {
			if (it.next().getValue() <= now) {
				it.remove();
				changed = true;
			}
		}
		if (changed) {
			setDirty();
		}
	}

	// ---------------- persistence ----------------

	private static SupervillainVillages load(CompoundTag tag, HolderLookup.Provider registries) {
		SupervillainVillages data = new SupervillainVillages();
		ListTag list = tag.getList("Cooldowns", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			data.cooldownEndByCell.put(t.getLong("Cell"), t.getLong("End"));
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<Long, Long> e : cooldownEndByCell.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putLong("Cell", e.getKey());
			t.putLong("End", e.getValue());
			list.add(t);
		}
		tag.put("Cooldowns", list);
		return tag;
	}
}
