package com.projecthero.mod.saiyan.kit;

/**
 * v0.17.3 Saiyan revamp: every number of the move kit (damage before {@link SaiyanKit#damageMult}, Ki, cooldowns in ticks,
 * ranges in blocks). The tests read these, so a change here is a change of the tested spec.
 *
 * <pre>
 *   key        move                damage                      Ki          cooldown
 *   R          Punch Combo         8 x4, then 15 + knockback   -           0.6 s after the 5th hit
 *   Shift+R    Rising Dragon Kick  12 launch + 10 axe kick     15          6 s
 *   G (hold)   Ki Blast Volley     5 a sphere, 4 a second      2 a sphere  -
 *   Shift+G    Kamehameha          15..40 by charge            25..60      12 s
 *   Z          Meteor Smash        22                          25          10 s
 *   X          Ki Dash             -                           5           2 s
 *   Shift+X    Dragon Rush         6 each + knockback          15          6 s
 *   C          Explosive Wave      14, radius 7                20          10 s
 *   Shift+C    Ki Rain             10 x 4, radius 6            30          15 s
 *   Shift+V    Solar Flare         blind + slow 4 s, 12 blocks 15          20 s
 * </pre>
 */
public final class SaiyanKitConfig {
	private SaiyanKitConfig() {
	}

	// ---------------------------------------------------------------- R: Punch Combo
	public static final float COMBO_HIT_DAMAGE = 8f;
	public static final float COMBO_FINISH_DAMAGE = 15f;
	public static final int COMBO_HITS = 5;
	/** The fastest the combo steps: one hit every this many ticks (4 a second). */
	public static final int COMBO_STEP_TICKS = 5;
	/** Idle this long and the combo starts over (~1.5 s). */
	public static final int COMBO_RESET_TICKS = 30;
	/** The short recovery after the 5th hit. */
	public static final int COMBO_RECOVERY = 12;
	public static final double COMBO_RANGE = 3.6;
	public static final double COMBO_FINISH_KNOCKBACK = 1.6;
	public static final double COMBO_FINISH_LIFT = 0.35;

	// ---------------------------------------------------------------- Shift+R: Rising Dragon Kick
	public static final float MARTIAL_LAUNCH_DAMAGE = 12f;
	public static final float MARTIAL_AXE_DAMAGE = 10f;
	public static final float MARTIAL_KI = 15f;
	public static final int MARTIAL_COOLDOWN = 120;
	/** No one to kick: a short cooldown and no Ki. */
	public static final int MARTIAL_WHIFF_COOLDOWN = 20;
	public static final double MARTIAL_RANGE = 4.0;
	/** The tick the axe kick lands (the target is near the top of its rise). */
	public static final int MARTIAL_AXE_AT = 11;
	/** The longest the move waits for the target to land. */
	public static final int MARTIAL_MAX = 40;
	public static final double MARTIAL_LAUNCH_SPEED = 1.15;
	public static final double MARTIAL_SLAM_SPEED = -2.2;

	// ---------------------------------------------------------------- G: Ki Blast Volley
	public static final float BLAST_DAMAGE = 5f;
	/** The small blast round the hit: anyone else this close takes {@link #BLAST_SPLASH_DAMAGE}. */
	public static final double BLAST_SPLASH_RADIUS = 1.5;
	public static final float BLAST_SPLASH_DAMAGE = 2f;
	public static final float BLAST_KI = 2f;
	/** One sphere every this many ticks while G is held (4 a second). */
	public static final int BLAST_EVERY = 5;
	public static final double BLAST_SPEED = 1.8;
	public static final int BLAST_LIFE = 26;
	public static final float BLAST_SIZE = 0.28f;

	// ---------------------------------------------------------------- Shift+G: Kamehameha
	public static final float BEAM_MIN_DAMAGE = 15f;
	public static final float BEAM_MAX_DAMAGE = 40f;
	/** Ki to start the charge; the charge then spends up to {@link #BEAM_MAX_KI} in all. */
	public static final float BEAM_START_KI = 25f;
	public static final float BEAM_MAX_KI = 60f;
	/** Ticks to a full charge. */
	public static final int BEAM_FULL_CHARGE = 40;
	/** The shortest charge that fires on release (shorter: it still fires at this charge). */
	public static final int BEAM_MIN_CHARGE = 8;
	/** Held at full charge this long, it fires on its own. */
	public static final int BEAM_MAX_HOLD = 100;
	public static final int BEAM_FIRE_TICKS = 30;
	/** The beam's damage lands in this many pulses on each creature it touches (the total is 15..40). */
	public static final int BEAM_PULSES = 5;
	public static final int BEAM_PULSE_EVERY = 6;
	public static final double BEAM_RANGE = 40.0;
	public static final int BEAM_COOLDOWN = 240;

	// ---------------------------------------------------------------- Z: Meteor Smash
	public static final float SMASH_DAMAGE = 22f;
	public static final float SMASH_KI = 25f;
	public static final int SMASH_COOLDOWN = 200;
	public static final int SMASH_WHIFF_COOLDOWN = 10;
	public static final double SMASH_RANGE = 16.0;
	/** The tick (after the vanish) the double axe-handle lands. */
	public static final int SMASH_HIT_AT = 5;
	public static final int SMASH_TICKS = 10;
	public static final double SMASH_CRATER_RADIUS = 2.5;

	// ---------------------------------------------------------------- X: Ki Dash
	public static final float DASH_KI = 5f;
	public static final int DASH_COOLDOWN = 40;
	/** Blocks a tick for {@link #DASH_TICKS} ticks: about 10 blocks. */
	public static final double DASH_SPEED = 2.0;
	public static final int DASH_TICKS = 5;
	/** No fall damage until he lands, or this long after a dash. */
	public static final int FALL_SAFE_TICKS = 60;

	// ---------------------------------------------------------------- Shift+X: Dragon Rush
	public static final float RUSH_DAMAGE = 6f;
	public static final float RUSH_KI = 15f;
	public static final int RUSH_COOLDOWN = 120;
	public static final double RUSH_SPEED = 2.0;
	/** 14 blocks. */
	public static final int RUSH_TICKS = 7;
	public static final double RUSH_KNOCKBACK = 1.5;
	public static final double RUSH_LIFT = 0.4;

	// ---------------------------------------------------------------- C: Explosive Wave
	public static final float WAVE_DAMAGE = 14f;
	public static final float WAVE_KI = 20f;
	public static final int WAVE_COOLDOWN = 200;
	public static final double WAVE_RADIUS = 7.0;
	public static final double WAVE_KNOCKBACK = 2.0;
	public static final double WAVE_LIFT = 0.5;

	// ---------------------------------------------------------------- Shift+C: Ki Rain
	public static final float RAIN_DAMAGE = 4f;
	public static final int RAIN_SHOTS = 10;
	public static final double RAIN_RADIUS = 6.0;
	/** Each falling blast hurts everyone this close to where it lands. */
	public static final double RAIN_SPLASH = 1.5;
	public static final float RAIN_KI = 30f;
	public static final int RAIN_COOLDOWN = 300;
	public static final double RAIN_REACH = 32.0;
	/** The leap, then a blast every {@link #RAIN_EVERY} ticks from {@link #RAIN_FIRE_AT}. */
	public static final int RAIN_FIRE_AT = 8;
	public static final int RAIN_EVERY = 2;
	public static final double RAIN_SHOT_SPEED = 2.2;

	// ---------------------------------------------------------------- Shift+V: Solar Flare
	public static final float FLARE_KI = 15f;
	public static final int FLARE_COOLDOWN = 400;
	public static final double FLARE_RANGE = 12.0;
	public static final int FLARE_TICKS = 80;
	/** How squarely a creature must face him to be blinded (the cosine of ~70 degrees). */
	public static final double FLARE_FACING = 0.35;

	// ---------------------------------------------------------------- shared
	/** The busy-pose ticks a press of an instant move holds the other inputs (Explosive Wave, Solar Flare). */
	public static final int SHORT_BUSY = 8;
}
