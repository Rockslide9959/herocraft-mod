package com.projecthero.mod.titan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: the Titan's aggro table. Before this the Titan locked onto one player (vanilla
 * {@code HurtByTargetGoal} only retargets when it <em>starts</em>, and the old "nearest player if I have
 * none" scan never switched away from a living target), so everyone else could hit it for free.
 *
 * <p>Threat is built by <b>damage dealt</b> to the Titan (raw damage x {@link TitanConfig.Aggro#damageThreatMultiplier})
 * and by <b>proximity</b> (standing close to it accrues {@link TitanConfig.Aggro#proximityThreatPerSecond}), and
 * halves every {@link TitanConfig.Aggro#threatHalfLifeTicks} (decayed lazily, on read). The Titan re-evaluates
 * every {@link TitanConfig.Aggro#retargetIntervalTicks}: {@link #pick} scores every valid player as threat plus a
 * small "closest" bonus and switches when someone beats the current target by the configured margin -- with a
 * chance of picking at random among the near-best instead, so a group can't script him perfectly.
 *
 * <p>Pure bookkeeping (no world access), one per Titan instance -- not a static cache, so nothing to reset.
 */
public final class TitanThreat {
	private static final class Entry {
		final Player player;
		double threat;
		int updatedTick;

		Entry(Player player, int now) {
			this.player = player;
			this.updatedTick = now;
		}
	}

	private final Map<Integer, Entry> entries = new HashMap<>();

	private static double decayed(Entry e, int now, int halfLife) {
		int dt = Math.max(0, now - e.updatedTick);
		if (dt == 0 || halfLife <= 0) {
			return e.threat;
		}
		return e.threat * Math.pow(0.5, dt / (double) halfLife);
	}

	public void add(Player player, double amount, int now, int halfLife) {
		if (player == null || amount <= 0.0) {
			return;
		}
		Entry e = entries.computeIfAbsent(player.getId(), id -> new Entry(player, now));
		if (e.player != player) {
			e = new Entry(player, now);
			entries.put(player.getId(), e);
		}
		e.threat = decayed(e, now, halfLife) + amount;
		e.updatedTick = now;
	}

	public double threatOf(Player player, int now, int halfLife) {
		if (player == null) {
			return 0.0;
		}
		Entry e = entries.get(player.getId());
		return e == null || e.player != player ? 0.0 : decayed(e, now, halfLife);
	}

	/** Forgets anyone no longer valid (dead, logged out, creative...) or whose threat has faded to nothing. */
	public void prune(int now, int halfLife, Predicate<Player> valid) {
		for (Iterator<Entry> it = entries.values().iterator(); it.hasNext();) {
			Entry e = it.next();
			if (!valid.test(e.player) || decayed(e, now, halfLife) < 0.05) {
				it.remove();
			}
		}
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
	}

	/** Threat plus a proximity bonus that fades out over {@link TitanConfig.Aggro#nearbyRange}. */
	public double score(Player p, Vec3 from, int now, TitanConfig.Aggro cfg) {
		double d = Math.sqrt(p.distanceToSqr(from));
		double near = cfg.nearbyRange <= 0 ? 0.0 : cfg.nearbyWeight * Math.max(0.0, 1.0 - d / cfg.nearbyRange);
		return threatOf(p, now, cfg.threatHalfLifeTicks) + near;
	}

	/**
	 * The switching rule. {@code candidates} are already filtered to valid players in range; {@code current} may be
	 * null or no longer a candidate. Returns who the Titan should be targeting (possibly {@code current}), or null
	 * if there is nobody. {@code random == null} makes it deterministic (no random pick).
	 */
	public Player pick(List<? extends Player> candidates, Player current, Vec3 from, int now,
			TitanConfig.Aggro cfg, RandomSource random) {
		if (candidates.isEmpty()) {
			return null;
		}
		Player best = null;
		double bestScore = -1.0;
		double currentScore = -1.0;
		double[] scores = new double[candidates.size()];
		for (int i = 0; i < candidates.size(); i++) {
			Player p = candidates.get(i);
			double s = score(p, from, now, cfg);
			scores[i] = s;
			if (p == current) {
				currentScore = s;
			}
			if (s > bestScore) {
				bestScore = s;
				best = p;
			}
		}
		if (random != null && candidates.size() > 1 && random.nextDouble() < cfg.randomSwitchChance) {
			List<Player> close = new ArrayList<>();
			for (int i = 0; i < candidates.size(); i++) {
				if (scores[i] >= bestScore * cfg.randomScoreFloor) {
					close.add(candidates.get(i));
				}
			}
			if (!close.isEmpty()) {
				return close.get(random.nextInt(close.size()));
			}
		}
		if (currentScore >= 0.0 && best != current && bestScore <= currentScore * cfg.switchMargin + 0.5) {
			return current; // not enough of a gap to be worth turning round for
		}
		return best;
	}
}
