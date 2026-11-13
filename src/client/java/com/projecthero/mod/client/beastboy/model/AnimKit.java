package com.projecthero.mod.client.beastboy.model;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: small animation and modelling helpers for the T-Rex, pterodactyl, snake, fly, hawk and bat (keyframed
 * move curves, blinks, footfall impacts, hover wobble). Shared-file free so the parallel art rounds don't collide.
 */
public final class AnimKit {
	private AnimKit() {
	}

	/** Eased keyframes: the value at {@code p} through (times[i], values[i]), smoothstep between keys, held outside. */
	public static float key(float p, float[] times, float[] values) {
		if (p <= times[0]) {
			return values[0];
		}
		for (int i = 0; i < times.length - 1; i++) {
			if (p <= times[i + 1]) {
				float k = BeastPose.smooth((p - times[i]) / Math.max(1.0e-4f, times[i + 1] - times[i]));
				return Mth.lerp(k, values[i], values[i + 1]);
			}
		}
		return values[values.length - 1];
	}

	/** The keyframed value of {@code move} (0 when it isn't playing). */
	public static float key(BeastPose s, String move, float[] times, float[] values) {
		return s.is(move) ? key(s.prog, times, values) : 0f;
	}

	/** 0..1 eyelid closure: a quick blink every {@code period} ticks (offset by {@code seed}), now and then a double blink. */
	public static float blink(float age, float period, float seed) {
		float c = age / period + seed;
		int n = Mth.floor(c);
		float f = (c - n) * period; // ticks into this cycle
		float b = Math.max(0f, 1f - Math.abs(f - 2.2f) / 2.2f);
		if ((n * 7 + 3) % 5 == 0) {
			b = Math.max(b, Math.max(0f, 1f - Math.abs(f - 7f) / 2.2f));
		}
		return BeastPose.smooth(b);
	}

	/** A footfall impact 0..1 that peaks as a foot lands at {@code phase} (mod 2pi = 0) and dies off quickly after. */
	public static float impact(float phase) {
		float p = phase - Mth.TWO_PI * Mth.floor(phase / Mth.TWO_PI);
		return (float) Math.exp(-p * 3.2f) * Math.min(1f, p * 12f + 0.0f);
	}

	/** Smooth 1D noise in -1..1 (sum of incommensurate sines), for hover jitter and idle drift. */
	public static float wobble(float t, float seed) {
		return (Mth.sin(t * 0.37f + seed) * 0.5f + Mth.sin(t * 0.83f + seed * 2.1f) * 0.3f + Mth.sin(t * 1.71f + seed * 3.7f) * 0.2f);
	}
}
