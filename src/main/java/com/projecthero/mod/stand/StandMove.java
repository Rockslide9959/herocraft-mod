package com.projecthero.mod.stand;

import java.util.List;

/**
 * v0.17.2: one Stand move (same shape as the Omnitrix's alien moves): its id (lang key
 * {@code projecthero.stand.move.<id>} / {@code .desc}), the key slot (1 R, 2 G, 3 X, 4 Z, 5 V, 6 C), Shift or not, the
 * cooldown in ticks, the Stamina cost and whether it is held (press + release).
 */
public record StandMove(String id, int slot, boolean shift, int cooldown, float cost, boolean held) {
	public static final String ORA_RUSH = "ora_rush";
	public static final String STAR_FINGER = "star_finger";
	public static final String POWER_GRAB = "power_grab";
	public static final String BEARING_FLICK = "bearing_flick";
	public static final String LEAP = "stand_leap";
	public static final String DASH = "stand_dash";
	public static final String STAR_BREAKER = "star_breaker";
	public static final String THE_WORLD = "the_world";
	public static final String GUARD = "stand_guard";
	public static final String SIGHT = "precision_sight";
	public static final String INHALE = "super_inhale";
	public static final String UPPERCUT = "ora_uppercut";

	public static final List<StandMove> STAR_PLATINUM = List.of(
			new StandMove(ORA_RUSH, 1, false, StandConfig.RUSH_COOLDOWN, StandConfig.RUSH_START_COST, true),
			new StandMove(STAR_FINGER, 1, true, StandConfig.FINGER_COOLDOWN, StandConfig.FINGER_COST, false),
			new StandMove(POWER_GRAB, 2, false, StandConfig.THROW_COOLDOWN, StandConfig.THROW_COST, false),
			new StandMove(BEARING_FLICK, 2, true, StandConfig.FLICK_COOLDOWN, StandConfig.FLICK_COST, false),
			new StandMove(LEAP, 3, false, StandConfig.LEAP_COOLDOWN, StandConfig.LEAP_COST, false),
			new StandMove(DASH, 3, true, StandConfig.DASH_COOLDOWN, StandConfig.DASH_COST, false),
			new StandMove(STAR_BREAKER, 4, false, StandConfig.BREAKER_COOLDOWN, StandConfig.BREAKER_COST, true),
			new StandMove(THE_WORLD, 4, true, StandConfig.TIME_STOP_COOLDOWN, StandConfig.TIME_STOP_COST, false),
			new StandMove(GUARD, 5, false, StandConfig.GUARD_COOLDOWN, 0f, true),
			new StandMove(SIGHT, 5, true, StandConfig.SIGHT_COOLDOWN, 0f, false),
			new StandMove(INHALE, 6, false, StandConfig.INHALE_COOLDOWN, StandConfig.INHALE_COST, false),
			new StandMove(UPPERCUT, 6, true, StandConfig.UPPERCUT_COOLDOWN, StandConfig.UPPERCUT_COST, false));
}
