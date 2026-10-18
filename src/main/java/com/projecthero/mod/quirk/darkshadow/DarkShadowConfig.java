package com.projecthero.mod.quirk.darkshadow;

/**
 * v0.16.7: every Dark Shadow number (ticks unless noted). Damage values are the base at full strength 1.0; the real hit
 * is {@code base * DarkShadowCombat.strength(player)} -- {@link #STRENGTH_MIN} with an empty Shadow meter (daylight),
 * {@link #STRENGTH_MAX} with a full one (darkness).
 */
public final class DarkShadowConfig {
	private DarkShadowConfig() {
	}

	// ---------------------------------------------------------------- ability ids
	public static final String STRIKE = "quirk_dark_shadow_strike";
	public static final String FLURRY = "quirk_dark_shadow_flurry";
	public static final String GUARD = "quirk_dark_shadow_guard";
	public static final String COUNTER = "quirk_dark_shadow_counter";
	public static final String ANGEL = "quirk_dark_shadow_fallen_angel";
	public static final String DIVE = "quirk_dark_shadow_dive";
	public static final String GRAB = "quirk_dark_shadow_grab";
	public static final String THROW = "quirk_dark_shadow_grab_throw";
	public static final String RAMPAGE = "quirk_dark_shadow_abyssal_rampage";
	public static final String SWALLOW = "quirk_dark_shadow_abyssal_swallow";
	public static final String ANKH = "quirk_dark_shadow_black_ankh";
	public static final String CALM = "quirk_dark_shadow_calm";

	// ---------------------------------------------------------------- the Shadow meter
	/** Light level (0..15, block light and sky light at the eyes) where the meter neither rises nor falls. */
	public static final float LIGHT_PIVOT = 7.5f;
	/** Meter change per tick per light level away from the pivot (light 0: +0.3 a tick = 6 a second). */
	public static final float METER_RATE = 0.04f;
	/** "In darkness": light at or below this (Berserk and Abyssal Rampage need it). */
	public static final int DARK_LIGHT = 7;
	/** Damage multiplier with an empty / a full meter. */
	public static final float STRENGTH_MIN = 0.5f;
	public static final float STRENGTH_MAX = 1.6f;

	// ---------------------------------------------------------------- Berserk
	public static final int BERSERK_TICKS = 160;
	/** The meter after a Berserk burns out on its own (Calm drops it to {@link #CALM_METER}). */
	public static final float BERSERK_AFTER_METER = 60f;
	public static final int BERSERK_INTERVAL = 8;
	public static final double BERSERK_RADIUS = 10.0;
	/** Base damage (always at {@link #STRENGTH_MAX}). */
	public static final float BERSERK_DAMAGE = 7f;
	public static final double BERSERK_KNOCKBACK = 0.9;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- R: Shadow Strike / Shift+R: Shadow Flurry
	public static final double STRIKE_RANGE = 8.0;
	public static final double STRIKE_ASSIST_DEGREES = 10.0;
	public static final float STRIKE_DAMAGE = 9f;
	public static final double STRIKE_KNOCKBACK = 0.7;
	public static final int STRIKE_COOLDOWN = 16;
	public static final double FLURRY_RANGE = 8.0;
	public static final int FLURRY_HITS = 6;
	public static final int FLURRY_INTERVAL = 3;
	public static final float FLURRY_DAMAGE = 3.5f;
	public static final int FLURRY_COOLDOWN = 90;

	// ---------------------------------------------------------------- G: Shadow Guard / Shift+G: Shadow Counter
	/** Fraction of a frontal hit the Guard soaks (projectiles from the front: all of it). */
	public static final float GUARD_BLOCK = 0.85f;
	/** Within this angle of where the body faces counts as "the front" (degrees from the facing, each side). */
	public static final double GUARD_ARC_DEGREES = 80.0;
	public static final int GUARD_MAX_TICKS = 120;
	public static final int GUARD_COOLDOWN = 40;
	public static final int COUNTER_WINDOW = 15;
	public static final float COUNTER_DAMAGE = 10f;
	public static final double COUNTER_KNOCKBACK = 1.1;
	public static final int COUNTER_COOLDOWN = 100;

	// ---------------------------------------------------------------- X: Black Fallen Angel / Shift+X: Shadow Dive
	public static final float ANGEL_FLY_SPEED = 0.07f;
	public static final int ANGEL_COOLDOWN = 20;
	/** Slow Falling given when the wings vanish in mid-air (so a Berserk or an Erase never drops you to death). */
	public static final int ANGEL_SAFE_FALL_TICKS = 100;
	public static final double DIVE_SPEED = 2.1;
	public static final int DIVE_TICKS = 8;
	public static final double DIVE_HIT_RADIUS = 1.6;
	public static final float DIVE_DAMAGE = 6f;
	public static final int DIVE_COOLDOWN = 60;

	// ---------------------------------------------------------------- Z: Shadow Grab / Shift+Z: Grab and Throw
	public static final double GRAB_RANGE = 12.0;
	public static final double GRAB_ASSIST_DEGREES = 10.0;
	public static final int GRAB_PULL_TICKS = 6;
	/** Where the catch lands: this far in front of you. */
	public static final double GRAB_HOLD_DISTANCE = 1.7;
	public static final float GRAB_DAMAGE = 3f;
	public static final int GRAB_COOLDOWN = 60;
	public static final double THROW_SPEED = 1.9;
	public static final double THROW_LIFT = 0.35;
	public static final float THROW_DAMAGE = 6f;
	public static final int THROW_COOLDOWN = 80;

	// ---------------------------------------------------------------- V: Abyssal Rampage / Shift+V: Abyssal Swallow
	public static final int RAMPAGE_TICKS = 120;
	public static final int RAMPAGE_INTERVAL = 5;
	public static final int RAMPAGE_TARGETS_PER_HIT = 3;
	public static final double RAMPAGE_RADIUS = 9.0;
	public static final float RAMPAGE_DAMAGE = 5f;
	public static final double RAMPAGE_KNOCKBACK = 0.8;
	public static final int RAMPAGE_COOLDOWN = 600;
	public static final double SWALLOW_RANGE = 8.0;
	public static final int SWALLOW_TICKS = 100;
	public static final int SWALLOW_INTERVAL = 10;
	public static final float SWALLOW_DAMAGE = 2f;
	public static final int SWALLOW_COOLDOWN = 300;

	// ---------------------------------------------------------------- C: Black Ankh / Shift+C: Calm
	/** Black Ankh: attack damage at strength 1.0 (scaled like the moves), flat armour and toughness. */
	public static final double ANKH_DAMAGE = 4.0;
	public static final double ANKH_ARMOR = 8.0;
	public static final double ANKH_TOUGHNESS = 4.0;
	public static final int ANKH_REFRESH = 20;
	public static final int ANKH_COOLDOWN = 20;
	public static final float CALM_METER = 10f;
	public static final int CALM_COOLDOWN = 200;
}
