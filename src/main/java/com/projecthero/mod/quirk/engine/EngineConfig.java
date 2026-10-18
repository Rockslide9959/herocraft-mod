package com.projecthero.mod.quirk.engine;

/** v0.16.7: every Engine (Tenya Iida / Ingenium) number. Ticks unless named otherwise; heat is the 0..100 meter. */
public final class EngineConfig {
	private EngineConfig() {
	}

	// ---------------------------------------------------------------- ability ids
	public static final String KICK = "quirk_engine_recipro_kick";
	public static final String SPIN = "quirk_engine_spin_kick";
	public static final String BURST = "quirk_engine_recipro_burst";
	public static final String GEAR = "quirk_engine_gear_up";
	public static final String LEAP = "quirk_engine_engine_leap";
	public static final String AIR_DASH = "quirk_engine_air_dash";
	public static final String EXTEND = "quirk_engine_recipro_extend";
	public static final String AFTERBURNER = "quirk_engine_afterburner_kick";
	public static final String CARRY = "quirk_engine_rescue_carry";
	public static final String THROW = "quirk_engine_rescue_throw";
	public static final String TURBO = "quirk_engine_recipro_turbo";
	public static final String COOL = "quirk_engine_cool_down";

	// ---------------------------------------------------------------- bosses
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most 10% max health a hit, no knockback, no carry. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- passive
	/** Speed I: +20% movement speed (the same as the effect), always on while the engines run. */
	public static final double PASSIVE_SPEED = 0.20;
	/** 0.6 + 0.4 = a full block stepped without jumping. */
	public static final double STEP_BONUS = 0.4;
	/** The view widens with speed; it never widens past this (vanilla would double it at Turbo). */
	public static final float FOV_CAP = 1.22f;

	// ---------------------------------------------------------------- the meter (Engine heat, per second)
	/** Walking / standing with no gear engaged. */
	public static final float COOL_PER_SECOND = 6f;
	/** Sprinting with no gear engaged: the engines barely warm. */
	public static final float SPRINT_COOL_PER_SECOND = 1.5f;
	/** In water the engines cool this much faster. */
	public static final float WATER_COOL_MULT = 2f;
	public static final float STALL_COOL_PER_SECOND = 8f;
	/** At 100 heat: Stall -- Slowness III, no moves. */
	public static final int STALL_TICKS = 120;
	public static final int STALL_SLOW_AMPLIFIER = 2;

	// ---------------------------------------------------------------- R: Recipro Kick / Shift+R: Spin Kick
	public static final float KICK_DAMAGE = 5f;
	public static final int KICK_HITS = 3;
	public static final int KICK_INTERVAL = 4;
	public static final double KICK_REACH = 3.5;
	public static final double KICK_KNOCKBACK = 0.25;
	public static final double KICK_FINAL_KNOCKBACK = 1.1;
	public static final float KICK_HEAT = 4f;
	public static final int KICK_COOLDOWN = 20;

	public static final float SPIN_DAMAGE = 7f;
	public static final double SPIN_RADIUS = 3.5;
	public static final double SPIN_KNOCKBACK = 1.5;
	public static final double SPIN_LIFT = 0.3;
	public static final float SPIN_HEAT = 6f;
	public static final int SPIN_COOLDOWN = 60;

	// ---------------------------------------------------------------- G: Recipro Burst / Shift+G: Gear Up
	/** +100% movement speed on top of the passive (about 13 blocks a second sprinting). */
	public static final double BURST_SPEED = 1.0;
	public static final int BURST_TICKS = 200;
	public static final float BURST_HEAT = 10f;
	public static final float BURST_HEAT_PER_SECOND = 3f;
	/** Recipro Burst always ends in a short stall (the engines seize, like Iida's). */
	public static final int BURST_STALL_TICKS = 60;
	public static final int BURST_COOLDOWN = 600;

	public static final double GEAR_SPEED = 1.6;
	public static final int GEAR_TICKS = 120;
	public static final float GEAR_HEAT = 10f;
	/** Double Recipro Burst's heat. */
	public static final float GEAR_HEAT_PER_SECOND = 6f;
	public static final int GEAR_COOLDOWN = 400;

	// ---------------------------------------------------------------- X: Engine Leap / Shift+X: Air Dash
	public static final double LEAP_UP = 1.25;
	public static final double LEAP_FORWARD = 0.9;
	public static final float LEAP_HEAT = 5f;
	public static final int LEAP_COOLDOWN = 80;
	/** After a leap or an air dash, the next landing is cushioned by the exhausts (no fall damage). */
	public static final int SOFT_LANDING_TICKS = 160;

	public static final double AIR_DASH_SPEED = 1.7;
	public static final float AIR_DASH_HEAT = 4f;
	public static final int AIR_DASH_COOLDOWN = 30;

	// ---------------------------------------------------------------- Z: Recipro Extend / Shift+Z: Afterburner Kick
	public static final double EXTEND_DISTANCE = 15.0;
	public static final int EXTEND_TICKS = 6;
	public static final double EXTEND_WIDTH = 1.4;
	public static final float EXTEND_DAMAGE = 10f;
	public static final double EXTEND_KNOCKBACK = 1.0;
	public static final double EXTEND_LIFT = 0.25;
	public static final float EXTEND_HEAT = 10f;
	public static final int EXTEND_COOLDOWN = 160;

	public static final double AFTERBURNER_REACH = 4.0;
	public static final float AFTERBURNER_DAMAGE = 12f;
	public static final double AFTERBURNER_LAUNCH = 2.6;
	public static final double AFTERBURNER_LIFT = 0.65;
	public static final float AFTERBURNER_HEAT = 8f;
	public static final int AFTERBURNER_COOLDOWN = 160;

	// ---------------------------------------------------------------- V: Rescue Carry / Shift+V: Rescue Throw
	public static final double CARRY_REACH = 5.0;
	public static final int CARRY_COOLDOWN = 20;
	public static final double THROW_SPEED = 2.0;
	public static final double THROW_LIFT = 0.45;
	/** A thrown ally floats down (Slow Falling) for this long. */
	public static final int THROW_ALLY_SLOW_FALL = 80;
	public static final int THROW_COOLDOWN = 60;

	// ---------------------------------------------------------------- C: Recipro Turbo / Shift+C: Cool Down
	public static final double TURBO_SPEED = 2.6;
	public static final int TURBO_TICKS = 60;
	public static final float TURBO_HEAT = 20f;
	public static final float TURBO_DAMAGE = 8f;
	public static final double TURBO_RADIUS = 1.5;
	public static final double TURBO_KNOCKBACK = 1.2;
	public static final int TURBO_COOLDOWN = 500;

	public static final float COOL_VENT = 50f;
	public static final int COOL_COOLDOWN = 300;
}
