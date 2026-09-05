package com.herocraft.mod.event.raid;

import java.util.UUID;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.EventInstance;
import com.herocraft.mod.event.EventManager;
import com.herocraft.mod.event.EventSavedData;
import com.herocraft.mod.event.EventState;
import com.herocraft.mod.event.EventTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Turning "this player's curse just ran out" (or "this player used a Grave Ritual Totem") into an
 * actual raid.
 *
 * <p>Kept separate from both {@link com.herocraft.mod.grave.GraveboundCurse} and {@link ZombieRaid}
 * so neither has to know about the other: the curse only knows it has expired, the raid only knows
 * where it is.
 */
public final class ZombieRaidStarter {
	private ZombieRaidStarter() {
	}

	/**
	 * Begin the raid a cursed player has been counting down to. Called the tick the curse hits zero.
	 * If a raid is already running on top of them -- they were dragged into someone else's, or their
	 * own somehow survived -- they simply join that one instead of a second raid appearing.
	 */
	public static void startForCursedPlayer(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos center = raidCenter(level, player.blockPosition());

		EventInstance existing = EventManager.at(level, center);
		if (existing != null && existing.typeId().equals(EventTypes.ZOMBIE_RAID)
				&& existing.state() == EventState.RUNNING) {
			player.sendSystemMessage(Component.translatable("event.herocraft.zombie_raid.joined")
					.withStyle(ChatFormatting.GOLD));
			return;
		}
		if (!start(level, center)) {
			// The start was refused because another Zombie Raid record sits within the minimum spacing.
			// If that record is not actually being fought (a leftover PENDING/PAUSED raid from a previous
			// curse that was abandoned, never cleaned up yet), clear it out and try once more -- a player
			// who has served a full curse must always get their raid, however many they have beaten.
			clearStaleNearby(level, center);
			if (!start(level, center)) {
				player.sendSystemMessage(Component.translatable("event.herocraft.zombie_raid.too_close")
						.withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** Abort any non-RUNNING Zombie Raid record close enough to block a fresh one. */
	private static void clearStaleNearby(ServerLevel level, BlockPos center) {
		int minGap = EventConfig.framework().minDistanceBetweenEvents;
		for (EventInstance e : new java.util.ArrayList<>(EventManager.active(level.getServer()))) {
			if (e.typeId().equals(EventTypes.ZOMBIE_RAID) && e.state() != EventState.RUNNING
					&& e.isAt(level, center, minGap)) {
				e.abort(level);
				EventSavedData.get(level).remove(e.id());
			}
		}
	}

	/** @return true if a new raid was created at {@code center}. */
	public static boolean start(ServerLevel level, BlockPos center) {
		ZombieRaid raid = new ZombieRaid(UUID.randomUUID());
		boolean started = EventManager.start(level, raid, center);
		if (started) {
			HeroCraftMod.LOGGER.info("[HeroCraft] scheduled Zombie Raid at {} in {}", center, level.dimension().location());
		}
		return started;
	}

	/**
	 * Where to centre a raid for a player standing at {@code near}.
	 *
	 * <p>Their own position is almost always right -- the raid is supposed to come to them -- but if
	 * they are deep underground (mining when the timer ran out, say) the surface directly above is a
	 * far better arena than a one-block corridor, and the mobs have somewhere legal to appear. The
	 * "deep underground" test is a heightmap read on one column, which is cheap and needs no chunk
	 * loading.
	 */
	private static BlockPos raidCenter(ServerLevel level, BlockPos near) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
		if (surface - near.getY() > EventConfig.framework().maxSpawnDistance) {
			return new BlockPos(near.getX(), surface, near.getZ());
		}
		return near;
	}
}
