package com.projecthero.mod.oathbreaker.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

import com.projecthero.mod.oathbreaker.OathbreakerTuning;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.13.19: the Oathbreaker's aggro table. Replaces vanilla's {@code HurtByTargetGoal}, which only ever retargets
 * when it <em>starts</em> -- once it was running with one player as the target, every other player's hits were
 * ignored ("my friend lures him one way and I can spam hit him and he never turns round").
 *
 * <p>Every landed hit adds its raw damage (at least {@link OathbreakerTuning#THREAT_MIN_PER_HIT}) to the
 * attacker's threat, and threat halves every {@link OathbreakerTuning#THREAT_HALF_LIFE_TICKS} (decayed lazily,
 * on read). {@link #shouldSwitch} is the whole switching rule; the entity/combat side decides <em>when</em> to
 * ask (every hit from someone who isn't the target, plus a periodic re-check) and whether he's allowed to
 * (never away from an Execution victim mid-hold). Pure bookkeeping: no world access, so it can be tested
 * directly. Keyed by entity id; server-side only.
 */
public final class OathbreakerThreat {
	private static final class Entry {
		final LivingEntity entity;
		float threat;
		int updatedTick;
		int lastHitTick;

		Entry(LivingEntity entity, int now) {
			this.entity = entity;
			this.updatedTick = now;
			this.lastHitTick = now;
		}
	}

	private static final int NEVER = -1_000_000;

	private final Map<Integer, Entry> entries = new HashMap<>();
	private int lastSwitchTick = NEVER;

	private static float decayed(Entry e, int now) {
		int dt = Math.max(0, now - e.updatedTick);
		if (dt == 0) {
			return e.threat;
		}
		return (float) (e.threat * Math.pow(0.5, dt / (double) OathbreakerTuning.THREAT_HALF_LIFE_TICKS));
	}

	/** A landed hit: {@code amount} (raw damage) worth of threat for {@code attacker}, stamped with {@code now}. */
	public void addHit(LivingEntity attacker, float amount, int now) {
		Entry e = entries.computeIfAbsent(attacker.getId(), id -> new Entry(attacker, now));
		e.threat = decayed(e, now) + Math.max(OathbreakerTuning.THREAT_MIN_PER_HIT, amount);
		e.updatedTick = now;
		e.lastHitTick = now;
	}

	public float threatOf(LivingEntity entity, int now) {
		if (entity == null) {
			return 0.0f;
		}
		Entry e = entries.get(entity.getId());
		return e == null || e.entity != entity ? 0.0f : decayed(e, now);
	}

	/** When {@code entity} last landed a hit on him, or a very old tick if never. */
	public int lastHitTick(LivingEntity entity) {
		if (entity == null) {
			return NEVER;
		}
		Entry e = entries.get(entity.getId());
		return e == null || e.entity != entity ? NEVER : e.lastHitTick;
	}

	public boolean hitRecently(LivingEntity entity, int now, int withinTicks) {
		return now - lastHitTick(entity) <= withinTicks;
	}

	/** The highest-threat entry that passes {@code usable} and isn't {@code exclude}, or null. */
	public LivingEntity strongest(int now, LivingEntity exclude, Predicate<LivingEntity> usable) {
		LivingEntity best = null;
		float bestThreat = 0.0f;
		for (Entry e : entries.values()) {
			if (e.entity == exclude || !usable.test(e.entity)) {
				continue;
			}
			float t = decayed(e, now);
			if (t > bestThreat) {
				bestThreat = t;
				best = e.entity;
			}
		}
		return best;
	}

	public boolean lockedOut(int now) {
		return now - lastSwitchTick < OathbreakerTuning.THREAT_SWITCH_LOCKOUT_TICKS;
	}

	public void noteSwitched(int now) {
		lastSwitchTick = now;
	}

	/**
	 * The switching rule. Switch from {@code current} to {@code candidate} when he has no target at all, or --
	 * outside the post-switch lockout -- when either:
	 * <ul>
	 * <li>the candidate's threat is more than {@link OathbreakerTuning#THREAT_SWITCH_MARGIN} times the current
	 * target's (a target who has never hit him has 0, so any attacker beats them), or</li>
	 * <li>the current target hasn't hurt him for {@link OathbreakerTuning#THREAT_IDLE_TICKS}, the candidate has,
	 * and the candidate is closer.</li>
	 * </ul>
	 * Distances are squared, from the boss; validity (alive, not creative/spectator, in range) is the caller's job.
	 */
	public boolean shouldSwitch(LivingEntity current, LivingEntity candidate, int now, double currentDistSqr,
			double candidateDistSqr) {
		if (candidate == null || candidate == current) {
			return false;
		}
		if (current == null) {
			return true;
		}
		if (lockedOut(now)) {
			return false;
		}
		float candidateThreat = threatOf(candidate, now);
		if (candidateThreat <= 0.0f) {
			return false;
		}
		if (candidateThreat > threatOf(current, now) * OathbreakerTuning.THREAT_SWITCH_MARGIN) {
			return true;
		}
		return !hitRecently(current, now, OathbreakerTuning.THREAT_IDLE_TICKS)
				&& hitRecently(candidate, now, OathbreakerTuning.THREAT_IDLE_TICKS)
				&& candidateDistSqr < currentDistSqr;
	}

	/** Forgets anyone gone (dead, removed) or whose threat has faded to nothing and who hasn't hit in a while. */
	public void prune(int now) {
		for (Iterator<Entry> it = entries.values().iterator(); it.hasNext();) {
			Entry e = it.next();
			if (!e.entity.isAlive() || e.entity.isRemoved()
					|| (decayed(e, now) < 0.5f && now - e.lastHitTick > OathbreakerTuning.THREAT_HALF_LIFE_TICKS * 4)) {
				it.remove();
			}
		}
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
		lastSwitchTick = NEVER;
	}
}
