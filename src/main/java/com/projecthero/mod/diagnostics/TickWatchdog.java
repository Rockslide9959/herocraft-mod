package com.projecthero.mod.diagnostics;

import com.projecthero.mod.ProjectHeroMod;

/**
 * A permanent, near-zero-cost watchdog around the handful of call sites a user-reported "the world
 * completely froze" bug pointed at (hammerless flight's per-tick handling, the crater ambient-
 * lightning tick, natural-Mjolnir chunk-load placement, and a resting hammer's own tick). Static
 * audit of all four -- including bytecode-level verification that {@code LightningBolt.spawnFire}
 * and its damage path both correctly bail out early when {@code visualOnly} is set -- found no
 * infinite loop, unbounded search, blocking call, or recursive event trigger in any of them. Since
 * that audit could not be confirmed against a live server (no {@code run/eula.txt} in this
 * environment -- see project memory), this stays in permanently as a real safety net rather than
 * being temporary debug spam: a single {@code System.nanoTime()} pair costs nanoseconds, and it is
 * silent unless one of these calls is genuinely taking far longer than anything in this code path
 * should ever take. If the freeze recurs, the log will name the exact call and how long it took,
 * which is a concrete lead instead of another guess.
 */
public final class TickWatchdog {
	/** Anything in these systems taking longer than this, even once, is a real anomaly worth a log line. */
	private static final long WARN_THRESHOLD_NANOS = 50_000_000L; // 50ms -- 20 ticks' worth of budget

	private TickWatchdog() {
	}

	public static void run(String label, Runnable action) {
		long start = System.nanoTime();
		try {
			action.run();
		} finally {
			long elapsedNanos = System.nanoTime() - start;
			if (elapsedNanos > WARN_THRESHOLD_NANOS) {
				ProjectHeroMod.LOGGER.warn("[watchdog] {} took {}ms -- expected sub-millisecond",
						label, elapsedNanos / 1_000_000.0);
			}
		}
	}
}
