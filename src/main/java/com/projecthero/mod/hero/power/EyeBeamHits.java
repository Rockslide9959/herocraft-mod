package com.projecthero.mod.hero.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.Entity;

/**
 * v0.17.7 (user: "you need to hold the laser vision on the enemy for like a second for it to deal damage, ... it should do damage
 * instantly"): held eye beams used to hit on a fixed clock ({@code age % interval}, Laser Vision on {@code tickCount % 10}), so the
 * beam could sit on a creature for up to half a second before the first hit. Now each target has its own clock: the first tick a
 * beam touches it, it is hit; after that, once every {@code interval} ticks while the beam stays on it. Damage per second is
 * unchanged, and flicking the beam off and back on never hits faster than the interval.
 *
 * <p>Holds UUIDs, entity ids and game times only (no worlds or entities); still cleared by {@code ServerStateReset}.
 */
public final class EyeBeamHits {
	private static final Map<Key, Long> LAST = new HashMap<>();
	private static long lastPrune;

	private record Key(UUID shooter, String beam, int target) {
	}

	private EyeBeamHits() {
	}

	/**
	 * True when {@code beam} from {@code shooter} should hit {@code target} this tick: the first tick it is on the target, then every
	 * {@code interval} ticks. Records the hit when it returns true.
	 */
	public static boolean due(Entity shooter, String beam, Entity target, int interval) {
		long now = shooter.level().getGameTime();
		if (now - lastPrune > 200L || now < lastPrune) {
			prune(now);
		}
		Key k = new Key(shooter.getUUID(), beam, target.getId());
		Long last = LAST.get(k);
		if (last != null && now >= last && now - last < interval) {
			return false;
		}
		LAST.put(k, now);
		return true;
	}

	private static void prune(long now) {
		lastPrune = now;
		for (Iterator<Long> it = LAST.values().iterator(); it.hasNext();) {
			long t = it.next();
			if (now - t > 100L || now < t) {
				it.remove();
			}
		}
	}

	public static void clear() {
		LAST.clear();
		lastPrune = 0L;
	}
}
