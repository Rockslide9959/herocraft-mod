package com.projecthero.mod.miraculous;

/** v0.16.6: the Miraculous core's tunables (the kits keep their own in their own config classes). */
public final class MiraculousConfig {
	private MiraculousConfig() {
	}

	/** Shift+Z's countdown to the forced detransformation: 5 minutes. */
	public static final int COUNTDOWN_TICKS = 5 * 60 * 20;
	/** The transformation sequence: the Kwami into the jewel, the light, the suit (moves wait for it). */
	public static final int TRANSFORM_TICKS = 40;
	/** Gap between two presses of H. */
	public static final int TOGGLE_COOLDOWN = 20;

	// shared transformed stats (MiraculousStats)
	public static final double ARMOR_BONUS = 12.0;
	public static final double TOUGHNESS_BONUS = 6.0;
	public static final double MAX_HEALTH_BONUS = 10.0;
	public static final double MELEE_BONUS = 5.0;
	/** Multiplied total. */
	public static final double SPEED_BONUS = 0.25;
	/** Multiplied total. */
	public static final double JUMP_BONUS = 0.35;
}
