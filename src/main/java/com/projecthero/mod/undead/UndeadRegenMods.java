package com.projecthero.mod.undead;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: what slows Undead's regeneration down. Stub contract: the regeneration code multiplies a lost part's start delay by
 * {@link #delayMult} and its regrowth time by {@link #durationMult}. The shoulder sword (user: "if player shift+right clicks with
 * a sword they stab it into their shoulder ... makes regeneration 10% slower and increase regen delay by 10%") makes both 1.1.
 *
 * <p>v0.17.3 tester note ("Undead body is still regenerating at normal speed when hungry"): hunger slows all of it --
 * {@link #hungerMult}: full speed at 18+ food (9 drumsticks), sliding down to a quarter at 6 food (3 drumsticks), a quarter from
 * 5 down to 1, and nothing at all at 0 (a starving body only starves). The health regen and the part knitting heal
 * {@code hungerMult} times as much per step; a lost part waits and regrows {@code 1 / hungerMult} times as long; at 0 food no
 * regrowth starts and one already growing holds still.
 */
public final class UndeadRegenMods {
	private UndeadRegenMods() {
	}

	/** Food at or above this: full speed. */
	public static final int FULL_FOOD = 18;
	/** Food at or below this (but above 0): {@link #HUNGRY_MULT}. */
	public static final int HUNGRY_FOOD = 6;
	/** The speed at {@link #HUNGRY_FOOD} and below. */
	public static final float HUNGRY_MULT = 0.25f;

	/** 0..1: how fast a body this hungry regenerates. */
	public static float hungerMult(Player p) {
		return hungerMult(p.getFoodData().getFoodLevel());
	}

	public static float hungerMult(int food) {
		if (food <= 0) {
			return 0f;
		}
		if (food >= FULL_FOOD) {
			return 1f;
		}
		if (food <= HUNGRY_FOOD) {
			return HUNGRY_MULT;
		}
		return HUNGRY_MULT + (1f - HUNGRY_MULT) * (food - HUNGRY_FOOD) / (float) (FULL_FOOD - HUNGRY_FOOD);
	}

	/** Out of food: nothing regenerates. */
	public static boolean starving(Player p) {
		return hungerMult(p) <= 0f;
	}

	private static float sword(Player p) {
		return UndeadShoulderSword.has(p) ? UndeadKitConfig.SHOULDER_REGEN_MULT : 1f;
	}

	/** Hunger stretches the times (capped at the {@link #HUNGRY_MULT} stretch; at 0 food nothing starts, see {@link #starving}). */
	private static float hungerStretch(Player p) {
		return 1f / Math.max(HUNGRY_MULT, hungerMult(p));
	}

	public static float delayMult(Player p) {
		return sword(p) * hungerStretch(p);
	}

	public static float durationMult(Player p) {
		return sword(p) * hungerStretch(p);
	}
}
