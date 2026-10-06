package com.projecthero.mod.sentinel;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventManager;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/**
 * v0.15.1: the Sentinel Purge's natural trigger -- modelled on Carnage's meteor: a once-a-minute roll per Overworld
 * player at night (never on Peaceful, never before {@link SentinelConfig.Trigger#minimumWorldDay}), and only for players
 * with powers: {@link SentinelConfig.Trigger#mutantChancePerMinute} for a mutant, the smaller
 * {@link SentinelConfig.Trigger#superhumanChancePerMinute} for any other superhuman, nothing for an ordinary player. A
 * cooldown per dimension follows. The cooldown map holds only a game time per dimension and is cleared by
 * {@code ServerStateReset}.
 */
public final class SentinelSpawner {
	private static final Map<ResourceKey<Level>, Long> COOLDOWN_UNTIL = new HashMap<>();

	private SentinelSpawner() {
	}

	public static void clearSessionState() {
		COOLDOWN_UNTIL.clear();
	}

	/** The chance per minute that the Sentinels come for {@code player} (0 for an ordinary human). */
	public static double chanceFor(ServerPlayer player) {
		SentinelConfig.Trigger cfg = SentinelConfig.trigger();
		return switch (SentinelTargets.classify(player)) {
			case MUTANT -> cfg.mutantChancePerMinute;
			case SUPERHUMAN -> cfg.superhumanChancePerMinute;
			default -> 0.0;
		};
	}

	public static void tick(MinecraftServer server) {
		SentinelConfig.Trigger cfg = SentinelConfig.trigger();
		if (!cfg.naturalSpawnEnabled || server.getTickCount() % 1200 != 900) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level.getDifficulty() == Difficulty.PEACEFUL || !level.isNight() || level.getDayTime() / 24000L < cfg.minimumWorldDay) {
			return;
		}
		Long cd = COOLDOWN_UNTIL.get(level.dimension());
		if (cd != null && level.getGameTime() < cd) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (!SentinelPurgeEvent.eligible(p)) {
				continue;
			}
			double chance = chanceFor(p);
			if (chance <= 0 || level.random.nextDouble() >= chance) {
				continue;
			}
			if (EventManager.anyActiveNear(level, p.blockPosition(), SentinelPurgeEvent.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
				continue;
			}
			if (SentinelPurge.start(level, p.blockPosition(), p) != null) {
				COOLDOWN_UNTIL.put(level.dimension(), level.getGameTime() + cfg.cooldownMinutes * 1200L);
				ProjectHeroMod.LOGGER.info("[ProjectHero] The Sentinel Program has detected {}", p.getName().getString());
				return;
			}
		}
	}
}
