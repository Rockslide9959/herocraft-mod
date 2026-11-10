package com.projecthero.mod.stand;

import java.util.List;

/**
 * v0.17.2: one Stand move (same shape as the Omnitrix's alien moves): its id (lang key
 * {@code projecthero.stand.move.<id>} / {@code .desc}), the key slot (1 R, 2 G, 3 X, 4 Z, 5 V, 6 C), Shift or not, the
 * cooldown in ticks, the Stamina cost and whether it is held (press + release).
 * <p>v0.17.3 (the user's rework): R is two moves on one key -- a tap is Platinum Smash, holding it is the ORA Barrage
 * ({@link StandConfig#HOLD_THRESHOLD}).
 */
public record StandMove(String id, int slot, boolean shift, int cooldown, float cost, boolean held) {
	public static final String PLATINUM_SMASH = "platinum_smash";
	public static final String ORA_BARRAGE = "ora_barrage";
	public static final String STAR_FINGER = "star_finger";
	public static final String ORA_CHASE = "ora_chase";
	public static final String BEARING_FLICK = "bearing_flick";
	public static final String STAND_JUMP = "stand_jump";
	public static final String ORA_RUSH = "ora_rush";
	public static final String STAR_BREAKER = "star_breaker";
	public static final String THE_WORLD = "the_world";
	public static final String GUARD = "stand_guard";
	public static final String STAND_GRAB = "stand_grab";
	public static final String INHALE = "super_inhale";
	public static final String UPPERCUT = "ora_uppercut";

	public static final List<StandMove> STAR_PLATINUM = List.of(
			new StandMove(PLATINUM_SMASH, 1, false, StandConfig.SMASH_COOLDOWN, StandConfig.SMASH_COST, false),
			new StandMove(ORA_BARRAGE, 1, false, StandConfig.BARRAGE_COOLDOWN, StandConfig.BARRAGE_STAMINA_PER_SECOND, true),
			new StandMove(STAR_FINGER, 1, true, StandConfig.FINGER_COOLDOWN, StandConfig.FINGER_COST, false),
			new StandMove(ORA_CHASE, 2, false, StandConfig.CHASE_COOLDOWN, StandConfig.CHASE_COST, false),
			new StandMove(BEARING_FLICK, 2, true, StandConfig.FLICK_COOLDOWN, StandConfig.FLICK_COST, false),
			new StandMove(STAND_JUMP, 3, false, StandConfig.JUMP_COOLDOWN, StandConfig.JUMP_COST, false),
			new StandMove(ORA_RUSH, 3, true, StandConfig.ORA_RUSH_COOLDOWN, StandConfig.ORA_RUSH_COST, false),
			new StandMove(STAR_BREAKER, 4, false, StandConfig.BREAKER_COOLDOWN, StandConfig.BREAKER_COST, true),
			new StandMove(THE_WORLD, 4, true, StandConfig.TIME_STOP_COOLDOWN, StandConfig.TIME_STOP_MIN_STAMINA, false),
			new StandMove(GUARD, 5, false, StandConfig.GUARD_COOLDOWN, 0f, true),
			new StandMove(STAND_GRAB, 5, true, StandConfig.GRAB_COOLDOWN, 0f, false),
			new StandMove(INHALE, 6, false, StandConfig.INHALE_COOLDOWN, StandConfig.INHALE_COST, false),
			new StandMove(UPPERCUT, 6, true, StandConfig.UPPERCUT_COOLDOWN, StandConfig.UPPERCUT_COST, false));
}
