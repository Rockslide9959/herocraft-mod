package com.projecthero.mod.miraculous;

/** v0.16.6: the Miraculous core's tunables (the kits keep their own in their own config classes). */
public final class MiraculousConfig {
	private MiraculousConfig() {
	}

	/** Shift+Z's countdown to the forced detransformation: 3 minutes (v0.16.7, user: was 5). */
	public static final int COUNTDOWN_TICKS = 3 * 60 * 20;
	/** The HUD jewel's five spots go out one every this long (a fifth of the countdown: 36 s). */
	public static final int COUNTDOWN_SPOT_TICKS = COUNTDOWN_TICKS / 5;
	/** The transformation sequence: the Kwami into the jewel, the light, the suit (moves wait for it). */
	public static final int TRANSFORM_TICKS = 40;
	/** Gap between two transformations / detransformations (a phrase, a command). */
	public static final int TOGGLE_COOLDOWN = 20;

	// the transformation sequence (MiraculousClient plays the light show from formChangeAt)
	/** The Kwami's spin into the jewel; then it vanishes. */
	public static final int INTO_JEWEL_TICKS = 12;
	/** The swirl of light gets its second chime. */
	public static final int SWIRL_SOUND_TICK = 14;
	/** The flash (and its sound). */
	public static final int FLASH_TICK = 30;

	// the Kwami
	/** Further than this from its owner and it is put straight back beside them. */
	public static final double KWAMI_SNAP_DISTANCE = 16.0;
	/** Ticks the owner must stand still before the Kwami starts drifting round them. */
	public static final int KWAMI_DRIFT_AFTER = 100;
	/** The intro: pop out of the jewel (0.5 s) and wave (1.5 s). */
	public static final int POP_OUT_TICKS = 40;
	/** Coming out after a detransformation: the pop, then tired, in front of the face this long. */
	public static final int POP_TIRED_TICKS = 30;
	/** One snack. */
	public static final int EAT_TICKS = 200; // v0.16.6 (user): 10 seconds to eat (was 2 s)
	/** v0.16.7 (user): the 10-piece substitute meal takes 22 s (the favourite food stays at {@link #EAT_TICKS}). */
	public static final int SUBSTITUTE_EAT_TICKS = 440;
	/** v0.16.7 (user): Shift+N is held this long to take the Miraculous off. */
	public static final int TAKE_OFF_HOLD_TICKS = 100;
	/** v0.16.7 (user): transformed, falls hurt half as much (they used to never hurt). */
	public static final float FALL_DAMAGE_FACTOR = 0.5f;
	/**
	 * v0.16.7 (user): the movement moves -- when one fires (its cooldown starts) the NEXT landing takes no fall damage at
	 * all (dashes, vaults, leaps, swings, surf, portal hops...).
	 */
	public static final java.util.Set<String> SAFE_LANDING_MOVES = java.util.Set.of(
			"ladybug_yoyo_swing", "ladybug_zip_line", "ladybug_pole_vault",
			"cat_noir_pole_vault", "cat_noir_cat_leap", "cat_noir_pounce_strike",
			"rena_rouge_fox_dash", "rena_rouge_fox_pounce", "rena_rouge_illusion_swap",
			"hawk_moth_swarm_flight", "hawk_moth_swarm_dash",
			"carapace_shield_surf", "carapace_shell_roll", "carapace_shield_slam", "carapace_tidal_swim",
			"pegasus_gallop_trample", "pegasus_portal_hop", "pegasus_sky_gallop",
			"viperion_serpent_dash", "viperion_coil_leap",
			"multimouse_rope_swing");
	/** A safe landing that never left the ground is dropped after this long. */
	public static final int SAFE_LANDING_GROUND_TICKS = 400;
	/** v0.16.6 (user): this many pieces of any other food stand in for the favourite. */
	public static final int SUBSTITUTE_FOOD_COUNT = 10;
	/** Feeding a Kwami that is not hungry: Regeneration I for the holder, this long. */
	public static final int SNACK_REGEN_TICKS = 100;
	/** The intro's three chat lines: the first this long after the jewel, then this far apart. */
	public static final int INTRO_FIRST_LINE = 15;
	public static final int INTRO_LINE_GAP = 50;

	// shared transformed stats (MiraculousStats)
	public static final double ARMOR_BONUS = 12.0;
	public static final double TOUGHNESS_BONUS = 6.0;
	public static final double MAX_HEALTH_BONUS = 10.0;
	public static final double MELEE_BONUS = 7.0; // v0.16.6 (user): +7 melee -- they are strong (was 5)
	/** Multiplied total. */
	public static final double SPEED_BONUS = 0.25;
	/** Multiplied total. */
	public static final double JUMP_BONUS = 0.35;
}
