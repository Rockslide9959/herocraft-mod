package com.projecthero.mod.event.boss;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * v0.15.19: the shared boss <b>threat table</b>. Before this most bosses used vanilla aggro --
 * {@code HurtByTargetGoal} only retargets when it <em>starts</em>, and {@code NearestAttackableTargetGoal} keeps
 * whoever it found first while they stay valid -- so one player could kite the boss away while a friend hit it
 * for free ("Carnage never changes targets").
 *
 * <p>The rules:
 * <ul>
 * <li>Every hit that lands on the boss adds its damage (at least {@link #MIN_THREAT_PER_HIT}) to the attacker's
 * threat. Projectiles and summons count for whoever {@link DamageSource#getEntity()} names; only things the boss
 * may fight ({@link BossTargets#isVictim}: players, their pets/summons, golems) are recorded.</li>
 * <li>Threat halves every {@link #HALF_LIFE_TICKS} (10 s, about -6.7% a second), decayed lazily on read, so
 * someone who stops fighting falls off the table.</li>
 * <li>Every {@link #RETHINK_TICKS} the boss looks at the table: the highest-threat attacker becomes the target when
 * the boss has none, when the current target is no longer valid, or when the challenger's threat is more than
 * {@link #SWITCH_MARGIN} times the current target's (a target who never hurt the boss has 0, so anyone who does
 * takes it). After a switch it holds for {@link #SWITCH_LOCKOUT_TICKS} so it never flip-flops.</li>
 * <li>Entries are dropped when the attacker is dead, removed, a spectator / creative-invulnerable, in another
 * dimension, beyond the boss's range (its {@code FOLLOW_RANGE}, clamped to 24..96) or faded to nothing.</li>
 * <li>An empty table changes nothing: the boss keeps its own acquisition (nearest player, raid lists...).</li>
 * </ul>
 *
 * <p>One instance per boss (an instance field, never a static map -- nothing for {@code ServerStateReset} to clear).
 * Server side only; not saved (a reloaded boss starts with a clean table and re-acquires normally).
 *
 * <p>Wiring a boss: {@code threat.record(source, damageTaken)} from {@code hurt}/{@code actuallyHurt} after the hit
 * lands, and {@code threat.tick()} once per server tick (aiStep / customServerAiStep). Attack state machines that
 * remember a victim finish on it; the next attack reads {@code getTarget()} and so picks up the new one.
 */
public final class BossThreat {
	/** Threat halves every 10 s. */
	public static final int HALF_LIFE_TICKS = 200;
	/** A challenger needs 20% more threat than the current target to take the boss's attention. */
	public static final float SWITCH_MARGIN = 1.2f;
	/** How often (ticks) the boss re-reads the table. */
	public static final int RETHINK_TICKS = 10;
	/** After switching, the boss stays on its new target at least this long (1.5 s). */
	public static final int SWITCH_LOCKOUT_TICKS = 30;
	/** Even a tiny hit (a snowball-weight arrow, a 0.5-damage tick) is worth this much threat. */
	public static final float MIN_THREAT_PER_HIT = 1.0f;
	/** Entries below this are forgotten. */
	private static final float FORGET_BELOW = 0.05f;

	private static final class Entry {
		final LivingEntity entity;
		float threat;
		int updatedTick;

		Entry(LivingEntity entity, int now) {
			this.entity = entity;
			this.updatedTick = now;
		}
	}

	private static final int NEVER = -1_000_000;

	private final Mob boss;
	private final Map<Integer, Entry> entries = new HashMap<>();
	private double rangeOverride = -1.0;
	private Predicate<LivingEntity> filter = e -> true;
	private BooleanSupplier hold = () -> false;
	private int lastSwitchTick = NEVER;
	private int lastThinkTick = NEVER;

	public BossThreat(Mob boss) {
		this.boss = boss;
	}

	/** A fixed range instead of the boss's {@code FOLLOW_RANGE}. */
	public BossThreat range(double range) {
		this.rangeOverride = range;
		return this;
	}

	/** An extra boss-specific rule a target must pass (e.g. "is a fighter in this raid"). */
	public BossThreat filter(Predicate<LivingEntity> extra) {
		this.filter = extra == null ? e -> true : extra;
		return this;
	}

	/**
	 * While {@code busy} says so (an attack mid-swing), {@link #tick} keeps the current target as long as it is still
	 * valid: the attack in flight finishes on who it was aimed at, the next one goes to the new top threat.
	 */
	public BossThreat holdWhile(BooleanSupplier busy) {
		this.hold = busy == null ? () -> false : busy;
		return this;
	}

	public double range() {
		if (rangeOverride > 0) {
			return rangeOverride;
		}
		double follow = boss.getAttribute(Attributes.FOLLOW_RANGE) != null ? boss.getAttributeValue(Attributes.FOLLOW_RANGE) : 48.0;
		return Math.max(24.0, Math.min(96.0, follow));
	}

	private int now() {
		return boss.tickCount;
	}

	private static float decayed(Entry e, int now) {
		int dt = Math.max(0, now - e.updatedTick);
		if (dt == 0) {
			return e.threat;
		}
		return (float) (e.threat * Math.pow(0.5, dt / (double) HALF_LIFE_TICKS));
	}

	// ---------------------------------------------------------------- recording

	/** A hit that landed: credits whoever is behind {@code source} with {@code amount} threat (if the boss may fight them). */
	public void record(DamageSource source, float amount) {
		if (source == null || boss.level().isClientSide()) {
			return;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof LivingEntity living) {
			add(living, amount);
		}
	}

	/** {@code amount} threat for {@code attacker} (ignored unless it is something the boss may fight). */
	public void add(LivingEntity attacker, float amount) {
		if (attacker == null || attacker == boss || !BossTargets.isVictim(boss, attacker)) {
			return;
		}
		int now = now();
		Entry e = entries.get(attacker.getId());
		if (e == null || e.entity != attacker) {
			e = new Entry(attacker, now);
			entries.put(attacker.getId(), e);
		}
		e.threat = decayed(e, now) + Math.max(MIN_THREAT_PER_HIT, amount);
		e.updatedTick = now;
	}

	public float threatOf(LivingEntity entity) {
		if (entity == null) {
			return 0.0f;
		}
		Entry e = entries.get(entity.getId());
		return e == null || e.entity != entity ? 0.0f : decayed(e, now());
	}

	// ---------------------------------------------------------------- deciding

	/** May the boss go after {@code e} through the table: a victim, here, in range, passing the boss's own rule. */
	public boolean isEligible(LivingEntity e) {
		if (e == null || !BossTargets.isVictim(boss, e) || e.level() != boss.level()) {
			return false;
		}
		double r = range();
		return boss.distanceToSqr(e) <= r * r && filter.test(e);
	}

	/** Forgets everyone no longer eligible or faded to nothing. */
	public void prune() {
		int now = now();
		for (Iterator<Entry> it = entries.values().iterator(); it.hasNext();) {
			Entry e = it.next();
			if (!isEligible(e.entity) || decayed(e, now) < FORGET_BELOW) {
				it.remove();
			}
		}
	}

	/** The highest-threat eligible attacker, or null when the table is empty. */
	public LivingEntity strongest() {
		prune();
		int now = now();
		LivingEntity best = null;
		float bestThreat = 0.0f;
		for (Entry e : entries.values()) {
			float t = decayed(e, now);
			if (t > bestThreat) {
				bestThreat = t;
				best = e.entity;
			}
		}
		return best;
	}

	/**
	 * Who the boss should be fighting given its {@code current} target: the table's top attacker when the boss has no
	 * (valid) target or the top attacker beats the current target by {@link #SWITCH_MARGIN} outside the lockout;
	 * otherwise {@code current}. Returns {@code current} unchanged when the table is empty (fallback acquisition).
	 */
	public LivingEntity decide(LivingEntity current) {
		LivingEntity best = strongest();
		if (best == null || best == current) {
			return current;
		}
		if (current == null || !current.isAlive() || current.isRemoved() || !isEligible(current)) {
			return best;
		}
		if (now() - lastSwitchTick < SWITCH_LOCKOUT_TICKS) {
			return current;
		}
		return threatOf(best) > threatOf(current) * SWITCH_MARGIN ? best : current;
	}

	/** Call when the boss switched to a target the table chose (starts the lockout). */
	public void noteSwitched() {
		lastSwitchTick = now();
	}

	/**
	 * Once per server tick: every {@link #RETHINK_TICKS} applies {@link #decide} to the boss's target. Returns the new
	 * target when it switched, else null.
	 */
	public LivingEntity tick() {
		if (boss.level().isClientSide() || boss.isNoAi() || !boss.isAlive()) {
			return null;
		}
		int now = now();
		if (now - lastThinkTick < RETHINK_TICKS && now >= lastThinkTick) {
			return null;
		}
		LivingEntity current = boss.getTarget();
		if (current != null && current.isAlive() && isEligible(current) && hold.getAsBoolean()) {
			return null; // re-think as soon as the attack in flight is over
		}
		lastThinkTick = now;
		return applyNow();
	}

	/** {@link #decide} right now, ignoring the re-think interval (tests, or a boss that needs an immediate answer). */
	public LivingEntity applyNow() {
		LivingEntity current = boss.getTarget();
		LivingEntity next = decide(current);
		if (next != null && next != current) {
			boss.setTarget(next);
			noteSwitched();
			return next;
		}
		return null;
	}

	public boolean isEmpty() {
		prune();
		return entries.isEmpty();
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
		lastSwitchTick = NEVER;
		lastThinkTick = NEVER;
	}

	/** Test hook: ages every entry by {@code ticks} (as if that much time had passed with no hits). */
	public void ageForTest(int ticks) {
		for (Entry e : entries.values()) {
			e.updatedTick -= ticks;
		}
		lastSwitchTick -= ticks;
		lastThinkTick -= ticks;
	}
}
