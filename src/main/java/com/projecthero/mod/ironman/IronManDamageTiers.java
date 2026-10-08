package com.projecthero.mod.ironman;

/**
 * v0.15.15: the nine battle-damage tiers an Iron Man suit's integrity steps through. Tier 0 is a clean suit; each
 * threshold the integrity fraction drops to (or below) adds one tier, so the visual damage only ever gets worse:
 * 45% = 1, 40% = 2, 35% = 3, 30% = 4, 25% = 5, 20% = 6, 15% = 7, 10% = 8, 5% (and a wrecked 0%) = 9.
 * Shared by the client texture / particle side ({@code client.ironman.IronManBattleDamage}) and its gametest.
 */
public final class IronManDamageTiers {
	/** Integrity fractions at (or below) which each tier starts, tier 1 first. */
	public static final float[] THRESHOLDS = { 0.45f, 0.40f, 0.35f, 0.30f, 0.25f, 0.20f, 0.15f, 0.10f, 0.05f };
	public static final int MAX_TIER = THRESHOLDS.length;
	/** Sparks start at this tier (25%), light smoke at {@link #SMOKE_TIER} (15%), the wrecked effects at {@link #MAX_TIER} (5%). */
	public static final int SPARK_TIER = 5;
	public static final int SMOKE_TIER = 7;
	/** Lens / reactor damage + flicker start here (30%). */
	public static final int LIGHTS_TIER = 4;
	/** Plates start tearing off here (20%). */
	public static final int MISSING_PLATE_TIER = 6;

	private IronManDamageTiers() {
	}

	/** 0 (clean) .. 9 (wrecked) for an integrity fraction 0..1. */
	public static int tier(float integrityFraction) {
		if (Float.isNaN(integrityFraction)) {
			return 0;
		}
		int t = 0;
		for (float th : THRESHOLDS) {
			// a hair of slack so a suit sitting on exactly 45.0% (float maths) still reads as tier 1
			if (integrityFraction <= th + 1.0e-4f) {
				t++;
			}
		}
		return t;
	}
}
