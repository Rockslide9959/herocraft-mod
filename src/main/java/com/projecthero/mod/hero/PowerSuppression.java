package com.projecthero.mod.hero;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Erasure (Eraser Head) -- a creature whose power is cancelled. While {@link #isSuppressed} a PLAYER can not use
 * any power at all (every system's keys: mutations, Hero-Tier powers, the Miraculous, the Quirks, H / N actions; running
 * toggles / flight / forms are paused by their owners checking this), and a MOB loses its abilities (creepers do not
 * swell, skeletons do not shoot, endermen do not teleport, bosses' special moves are held).
 *
 * <p>Server-authoritative: the Erasure kit calls {@link #suppress} every tick it keeps its stare on someone (a short
 * window, so the effect ends a moment after the stare breaks). The core syncs a player's own flag to that client
 * ({@link #clientUntil}) so the HUD can grey out. Static state: cleared by {@code ServerStateReset}.
 */
public final class PowerSuppression {
	/** entity UUID -> game time the suppression ends. */
	private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();
	/** Client mirror for the local player (set by the sync payload). */
	public static volatile long clientUntil;

	private PowerSuppression() {
	}

	/** Cancel {@code target}'s power for {@code ticks} more ticks from now (extends, never shortens). */
	public static void suppress(LivingEntity target, int ticks) {
		long end = target.level().getGameTime() + ticks;
		UNTIL.merge(target.getUUID(), end, Math::max);
	}

	/** Lift it at once. */
	public static void release(Entity target) {
		UNTIL.remove(target.getUUID());
	}

	/** Server side; client side for the LOCAL player too (via {@link #clientUntil}). */
	public static boolean isSuppressed(Entity e) {
		if (e == null) {
			return false;
		}
		if (e.level().isClientSide()) {
			return e instanceof Player && clientUntil > e.level().getGameTime();
		}
		Long end = UNTIL.get(e.getUUID());
		if (end == null) {
			return false;
		}
		if (end <= e.level().getGameTime()) {
			UNTIL.remove(e.getUUID());
			return false;
		}
		return true;
	}

	/** Ticks left (server). */
	public static int remaining(Entity e) {
		Long end = UNTIL.get(e.getUUID());
		return end == null ? 0 : (int) Math.max(0L, end - e.level().getGameTime());
	}

	public static void clearSessionState() {
		UNTIL.clear();
		clientUntil = 0L;
	}
}
