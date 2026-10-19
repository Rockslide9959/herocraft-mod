package com.projecthero.mod.miraculous.viperion;

import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7 (user): Second Chance is a real time rewind -- every TIMER a player's power keeps goes back to how it stood at
 * the checkpoint. The checkpoint stores the game time it was taken ({@link Snap#at}) and copies of the power states; on
 * a rewind every absolute game time in them is shifted by {@code now - at}, so each timer has exactly the time left it
 * had at the checkpoint (the Miraculous countdown, every move's cooldown, the kit timers, the Kwami's meal, a Quirk's
 * overload) and anything started since is gone.
 *
 * <p>Which kit values are times: the {@code vals} maps hold timers as absolute game times named {@code *_until},
 * {@code *_at}, {@code *_since} or {@code *_end} (the convention every kit here follows); toggles, counts and entity
 * ids are left as they are now. A timer started since the checkpoint (absent from the snapshot) is removed. Second
 * Chance's own keys ({@code vp_cp_*}, the rewind / checkpoint FX times) are never touched -- {@link SecondChance}
 * manages them.
 */
public final class TimeRewind {
	/** The Kwami's meal (its state, not a plain timer): restored as it was. */
	private static final Set<String> MEAL_KEYS = Set.of(Miraculous.V_EAT_UNTIL, Miraculous.V_EAT_FEEDS, Miraculous.V_SUB_FOOD);
	/** Second Chance's own keys: left alone. */
	private static final Set<String> OWN_KEYS = Set.of(Viperion.V_CP_UNTIL, Viperion.V_CP_LEFT, Viperion.V_REWIND_AT, Viperion.V_CHECKPOINT_AT);

	private TimeRewind() {
	}

	/** One player's power timers at the checkpoint. */
	public record Snap(long at, @Nullable MiraculousState miraculous, @Nullable QuirkState quirk, float quirkMeter) {
	}

	public static Snap snap(ServerPlayer p) {
		MiraculousState m = Miraculous.peek(p);
		QuirkState q = Quirks.peek(p);
		boolean hasQuirk = q != null && q.hasPower;
		return new Snap(p.level().getGameTime(), m != null && m.hasPower ? m.copy() : null, hasQuirk ? q.copy() : null,
				hasQuirk ? Quirks.meter(p) : 0f);
	}

	/** A {@code vals} key holding an absolute game time. */
	public static boolean isTimeKey(String key) {
		return key.endsWith("_until") || key.endsWith("_at") || key.endsWith("_since") || key.endsWith("_end");
	}

	/** {@code t} (an absolute game time from the snapshot) moved to now; 0 stays 0. */
	static long shift(long t, long delta) {
		return t == 0L ? 0L : t + delta;
	}

	/** Cooldowns: a move still cooling at the checkpoint has the same time left; one ready then is ready now. */
	static void restoreCooldowns(Map<String, Long> now, Map<String, Long> then, long at, long delta) {
		now.clear();
		then.forEach((k, v) -> {
			if (v != null && v > at) {
				now.put(k, v + delta);
			}
		});
	}

	/** The time-valued kit keys: back to the snapshot (shifted); those started since go. */
	static void restoreTimeVals(Map<String, Long> now, Map<String, Long> then, long delta, Set<String> keep) {
		now.keySet().removeIf(k -> isTimeKey(k) && !keep.contains(k) && !then.containsKey(k));
		then.forEach((k, v) -> {
			if (isTimeKey(k) && !keep.contains(k) && v != null) {
				now.put(k, shift(v, delta));
			}
		});
	}

	/** Rewinds every power timer of {@code p} to {@code s}. */
	public static void restore(ServerPlayer p, Snap s) {
		long now = p.level().getGameTime();
		long delta = now - s.at();
		MiraculousState then = s.miraculous();
		MiraculousState cur = Miraculous.peek(p);
		if (then != null && cur != null && cur.hasPower && cur.kwami.equals(then.kwami)) {
			Miraculous.edit(p, n -> {
				if (n.transformed == then.transformed) {
					// the countdown back to the time it had left (none then: none now)
					n.countdownEnd = then.countdownEnd > 0L ? then.countdownEnd + delta : 0L;
					n.formChangeAt = shift(then.formChangeAt, delta);
				}
				restoreCooldowns(n.abilityReadyAt, then.abilityReadyAt, s.at(), delta);
				restoreTimeVals(n.vals, then.vals, delta, OWN_KEYS);
				// the Kwami: hungry or not, mid-meal or not, as it was
				n.hungry = then.hungry;
				for (String k : MEAL_KEYS) {
					Long v = then.vals.get(k);
					if (v == null) {
						n.vals.remove(k);
					} else {
						n.vals.put(k, k.equals(Miraculous.V_EAT_UNTIL) ? shift(v, delta) : v);
					}
				}
			});
		}
		QuirkState qThen = s.quirk();
		QuirkState qCur = Quirks.peek(p);
		if (qThen != null && qCur != null && qCur.hasPower && qCur.quirk.equals(qThen.quirk)) {
			Quirks.edit(p, n -> {
				restoreCooldowns(n.abilityReadyAt, qThen.abilityReadyAt, s.at(), delta);
				restoreTimeVals(n.vals, qThen.vals, delta, Set.of());
				n.overloadUntil = qThen.overloadUntil > s.at() ? qThen.overloadUntil + delta : 0L;
				n.meter = s.quirkMeter();
			});
			Quirks.setMeter(p, s.quirkMeter());
		}
	}
}
