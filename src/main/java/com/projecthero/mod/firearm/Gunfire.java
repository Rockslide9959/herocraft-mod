package com.projecthero.mod.firearm;

import java.util.function.BooleanSupplier;

/**
 * v0.14.31: marks a hit as gunfire. Guns deal ordinary attack damage (a player attack, a mob attack), so nothing in the
 * {@code DamageSource} says "bullet" -- every gun wraps its {@code hurt} call in {@link #hit} and damage listeners
 * (Iron Man's bulletproof armour) read {@link #active}. Server thread only; re-entrant.
 */
public final class Gunfire {
	private static int depth;

	private Gunfire() {
	}

	public static boolean hit(BooleanSupplier hurt) {
		depth++;
		try {
			return hurt.getAsBoolean();
		} finally {
			depth--;
		}
	}

	/** True while a gun's damage is being applied. */
	public static boolean active() {
		return depth > 0;
	}
}
