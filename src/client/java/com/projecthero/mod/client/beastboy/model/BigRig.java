package com.projecthero.mod.client.beastboy.model;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: animation helpers for the big blocky animals (duty-factor gaits, blinking, easing, wobbles). Angles are
 * in the vanilla model convention the {@code *Geo} parts use: xRot + = tip forward / nose down, y down, front -Z.
 */
final class BigRig {
	static final float TAU = Mth.TWO_PI;

	private BigRig() {
	}

	/**
	 * A foot's gait at cycle position {@code cyc} (whole numbers = one stride): {forward offset 0..1-ish (+ = forward), lift 0..1, swing
	 * 0..1}. The stance (fraction {@code duty}) slides the planted foot back at a constant speed; the swing lifts it in an arc and brings
	 * it forward with an ease, so a heavy animal plants and rolls instead of paddling.
	 */
	static float[] gait(float cyc, float duty) {
		float f = cyc - Mth.floor(cyc);
		if (f < duty) {
			float t = f / duty;
			return new float[] { 0.5f - t, 0f, 0f };
		}
		float t = (f - duty) / (1f - duty);
		float e = t * t * (3f - 2f * t);
		return new float[] { -0.5f + e, Mth.sin(Mth.PI * t), Mth.sin(Mth.PI * t) };
	}

	/** Two dips per stride (each footfall of a heavy walker), 0..1. */
	static float dip(float cyc) {
		return 0.5f - 0.5f * Mth.cos(cyc * TAU * 2f);
	}

	/** 0..1 how closed the eyes are: a quick blink every few seconds, now and then a double one. */
	static float blink(float age, float seed) {
		float per = 74f + seed * 31f;
		float f = ((age + seed * 53f) % per + per) % per;
		float b = f < 4f ? Mth.sin(f / 4f * Mth.PI) : 0f;
		float g = ((age + seed * 53f + 8f) % (per * 3f) + per * 3f) % (per * 3f);
		if (g < 4f) {
			b = Math.max(b, Mth.sin(g / 4f * Mth.PI));
		}
		return b;
	}

	static float clamp01(float t) {
		return Mth.clamp(t, 0f, 1f);
	}

	/** 0..1 progress of {@code p} through [a, b]. */
	static float ph(float p, float a, float b) {
		return clamp01((p - a) / (b - a));
	}

	static float smooth(float t) {
		t = clamp01(t);
		return t * t * (3f - 2f * t);
	}

	static float easeOut(float t) {
		t = clamp01(t);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	static float easeIn(float t) {
		t = clamp01(t);
		return t * t * t;
	}

	/** Ease out with a small overshoot. */
	static float back(float t) {
		t = clamp01(t);
		float c = 1.7f, u = t - 1f;
		return 1f + (c + 1f) * u * u * u + c * u * u;
	}

	/** A decaying wobble after an impact at t = 0 (0 before). */
	static float wobble(float t, float freq, float decay) {
		if (t <= 0f) {
			return 0f;
		}
		return Mth.sin(t * freq) * (float) Math.exp(-t * decay);
	}

	/** A 0..1 bump over [a, b] of p (smooth up, smooth down). */
	static float bump(float p, float a, float b) {
		if (p <= a || p >= b) {
			return 0f;
		}
		return Mth.sin((p - a) / (b - a) * Mth.PI);
	}

	static float sin(float x) {
		return Mth.sin(x);
	}

	static float cos(float x) {
		return Mth.cos(x);
	}

	/** Cheap smooth 1D noise (sum of sines), about -1..1. */
	static float noise(float x, float seed) {
		return Mth.sin(x + seed) * 0.5f + Mth.sin(x * 2.31f + seed * 1.7f) * 0.3f + Mth.sin(x * 4.13f + seed * 2.9f) * 0.2f;
	}

	/** Exponential approach of a lagging value (for chains: each link follows the one before). */
	static float lag(float cur, float target, float k) {
		return cur + (target - cur) * k;
	}
}
