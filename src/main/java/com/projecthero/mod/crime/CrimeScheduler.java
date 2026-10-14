package com.projecthero.mod.crime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/**
 * v0.16.2: crimes happening on their own. Every {@code trigger.checkIntervalSeconds} each player in the Overworld is
 * looked at: off cooldown, near a village (a bell within {@code trigger.villageSearchRadius}) that is itself off
 * cooldown, not on Peaceful, no raid or other world event there -- and a crime starts in that village.
 *
 * <p>Two cooldowns, both game-time stamps in static maps (dropped by {@code ServerStateReset} when the server stops,
 * so a restart just means the next crime may come a little sooner):
 * <ul>
 *   <li><b>per village</b> (keyed by its bell): {@code trigger.minIntervalMinutes}-{@code maxIntervalMinutes} between
 *       crimes there;</li>
 *   <li><b>per player</b>: {@code trigger.playerCooldownMinutes} after a crime starts near them, and
 *       {@code trigger.firstCrimeDelayMinutes} after they are first seen.</li>
 * </ul>
 */
public final class CrimeScheduler {
	private static final Map<UUID, Long> PLAYER_NEXT = new HashMap<>();
	private static final Map<Long, Long> VILLAGE_NEXT = new HashMap<>();

	private CrimeScheduler() {
	}

	public static void clearSessionState() {
		PLAYER_NEXT.clear();
		VILLAGE_NEXT.clear();
	}

	static long minutes(double m) {
		return Math.max(0L, Math.round(m * 60.0 * 20.0));
	}

	/** A random time between the village interval bounds, in ticks. */
	public static long rollVillageInterval(RandomSource random) {
		CrimeConfig.Trigger t = CrimeConfig.trigger();
		long lo = minutes(Math.min(t.minIntervalMinutes, t.maxIntervalMinutes));
		long hi = minutes(Math.max(t.minIntervalMinutes, t.maxIntervalMinutes));
		return lo + (hi > lo ? (long) (random.nextDouble() * (hi - lo)) : 0L);
	}

	public static boolean playerOnCooldown(UUID player, long now) {
		Long next = PLAYER_NEXT.get(player);
		return next != null && now < next;
	}

	public static boolean villageOnCooldown(BlockPos bell, long now) {
		Long next = VILLAGE_NEXT.get(bell.asLong());
		return next != null && now < next;
	}

	/** Marks a crime as just started in {@code bell}'s village near {@code player}. */
	public static void noteCrime(UUID player, BlockPos bell, long now, RandomSource random) {
		if (player != null) {
			PLAYER_NEXT.put(player, now + minutes(CrimeConfig.trigger().playerCooldownMinutes));
		}
		if (bell != null) {
			VILLAGE_NEXT.put(bell.asLong(), now + rollVillageInterval(random));
		}
	}

	public static void tick(MinecraftServer server) {
		CrimeConfig.Trigger t = CrimeConfig.trigger();
		int interval = Math.max(20, t.checkIntervalSeconds * 20);
		if (!t.enabled || server.getTickCount() % interval != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.serverLevel();
			if (level.dimension() != Level.OVERWORLD || player.isSpectator()) {
				continue;
			}
			long now = level.getGameTime();
			if (!PLAYER_NEXT.containsKey(player.getUUID())) {
				PLAYER_NEXT.put(player.getUUID(), now + minutes(t.firstCrimeDelayMinutes));
				continue;
			}
			if (playerOnCooldown(player.getUUID(), now)) {
				continue;
			}
			if (tryStartNear(level, player, false) == null) {
				// nothing here right now: look again in half a minute rather than every check
				PLAYER_NEXT.put(player.getUUID(), now + 30 * 20);
			}
		}
	}

	/**
	 * Tries to start a natural crime in the village nearest {@code player}. {@code ignoreCooldowns} is the
	 * {@code /projecthero crime trigger} test path. Returns the crime, or {@code null} with nothing changed.
	 */
	public static CrimeEvent tryStartNear(ServerLevel level, ServerPlayer player, boolean ignoreCooldowns) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return null;
		}
		long now = level.getGameTime();
		Optional<BlockPos> found = CrimeVillages.findBell(level, player.blockPosition(), CrimeConfig.trigger().villageSearchRadius);
		if (found.isEmpty()) {
			return null;
		}
		BlockPos bell = found.get();
		if (!ignoreCooldowns && villageOnCooldown(bell, now)) {
			return null;
		}
		if (!level.isPositionEntityTicking(bell) || level.isRaided(bell) || Crimes.otherEventNear(level, bell)) {
			return null;
		}
		CrimeType type = Crimes.pickType(level, bell);
		if (type == null) {
			return null;
		}
		CrimeEvent crime = Crimes.start(level, type, bell, true, true);
		if (crime != null) {
			noteCrime(player.getUUID(), bell, now, level.random);
		}
		return crime;
	}
}
