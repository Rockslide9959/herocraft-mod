package com.projecthero.mod.titanshifter;

import com.projecthero.mod.titanshifter.data.TitanShifterState;

/**
 * v0.17.5 (user): Titan charges. Every normal shift (H, Bite Shift) spends one; with at least one left the shifter can shift
 * again straight away, as often as charges allow. Missing charges come back <b>one at a time</b>: a single timer runs
 * {@code charges.regenTicks} (60 s) for the first missing charge, then starts on the next. The tougher the Titan the fewer it
 * stores (config {@code charges}): Female 4, Attack 3, Armoured 2, Beast 2.
 *
 * <ul>
 *   <li>The timer runs in human form and while recovering, and pauses (keeps its progress) inside the Titan.</li>
 *   <li>It is a tick counter in the state ({@link TitanShifterState#chargeProgress}), so it survives relogs, deaths and world
 *       switches and never runs while offline. Death keeps charges and progress; respawning does not refill them.</li>
 *   <li>The Emergency Titan (hold H) is the zero-charge fallback: it costs no charge, but restarts the running timer.</li>
 *   <li>Re-forging into another Titan clamps the charges to the new maximum (never refills).</li>
 * </ul>
 * Pure helpers: callers copy the state, change it here and save it.
 */
public final class TitanCharges {
	private TitanCharges() {
	}

	/** How many charges {@code type} stores (at least 1). */
	public static int max(TitanType type) {
		var c = TitanShifterConfig.charges();
		int n = switch (type) {
			case ARMORED_TITAN -> c.armoredTitan;
			case FEMALE_TITAN -> c.femaleTitan;
			case BEAST_TITAN -> c.beastTitan;
			default -> c.attackTitan;
		};
		return Math.max(1, Math.min(9, n));
	}

	public static int max(TitanShifterState s) {
		return max(TitanType.byId(s.typeId));
	}

	/** Ticks one charge takes to come back. */
	public static int regenTicks() {
		return Math.max(20, TitanShifterConfig.charges().regenTicks);
	}

	/** Charges available now (a never-set count reads as full). */
	public static int current(TitanShifterState s) {
		int max = max(s);
		return s.charges < 0 ? max : Math.min(s.charges, max);
	}

	public static boolean full(TitanShifterState s) {
		return current(s) >= max(s);
	}

	/** 0..1 of the charge currently regenerating (0 when full). */
	public static float progress(TitanShifterState s) {
		return full(s) ? 0f : Math.min(1f, s.chargeProgress / (float) regenTicks());
	}

	/** Ticks until the next charge is back (0 when full). */
	public static int ticksToNext(TitanShifterState s) {
		return full(s) ? 0 : Math.max(0, regenTicks() - s.chargeProgress);
	}

	/**
	 * Runs the regeneration timer {@code ticks} forward: whenever it reaches {@link #regenTicks} one charge comes back and the
	 * timer starts over for the next; at the maximum it stops at 0. Returns how many charges came back.
	 */
	public static int advance(TitanShifterState s, int ticks) {
		int max = max(s);
		int have = current(s);
		s.charges = have;
		if (have >= max) {
			s.chargeProgress = 0;
			return 0;
		}
		int need = regenTicks();
		long progress = (long) s.chargeProgress + Math.max(0, ticks);
		int gained = 0;
		while (progress >= need && have < max) {
			progress -= need;
			have++;
			gained++;
		}
		s.charges = have;
		s.chargeProgress = have >= max ? 0 : (int) progress;
		return gained;
	}

	/** Spends one charge if there is one. The timer keeps whatever progress it had (it was already running, or starts now). */
	public static boolean spend(TitanShifterState s) {
		int have = current(s);
		if (have <= 0) {
			return false;
		}
		if (have >= max(s)) {
			s.chargeProgress = 0; // the timer starts from scratch for the first missing charge
		}
		s.charges = have - 1;
		return true;
	}

	/** Fills every charge (a fresh shifter). */
	public static void fill(TitanShifterState s) {
		s.charges = max(s);
		s.chargeProgress = 0;
	}

	/** After the Titan type changed: never more than the new Titan stores. */
	public static void clamp(TitanShifterState s) {
		if (s.charges >= 0) {
			s.charges = Math.min(s.charges, max(s));
		}
		if (full(s)) {
			s.chargeProgress = 0;
		}
	}

	/** Admin / tests: exactly {@code n} charges (clamped to 0..max); the timer restarts if any are missing. */
	public static void set(TitanShifterState s, int n) {
		s.charges = Math.max(0, Math.min(n, max(s)));
		s.chargeProgress = 0;
	}
}
