package com.projecthero.mod.quirk.engine;

/**
 * v0.16.7 (revamp): every Engine (Tenya Iida / Ingenium) number. Ticks unless named otherwise; heat is the 0..100 meter.
 * Speeds are movement-speed multipliers on top of normal (the same scale as the Speed effect: Speed I = +0.2).
 *
 * <p>These are code constants, not a config file, so there is nothing on disk to migrate: a world that ran the old kit
 * just reads the new numbers. Old save data (the pre-revamp gear timer and kick keys) is dropped by
 * {@link EngineMoves#tick} the first time it sees them.
 */
public final class EngineConfig {
	private EngineConfig() {
	}

	// ---------------------------------------------------------------- ability ids (kept ids keep their cooldown saves)
	// V / Shift+V: the mode -- the gearbox; Recipro Burst / Extend live inside it (V at top gear)
	public static final String SHIFT_UP = "quirk_engine_shift_up";
	public static final String SHIFT_DOWN = "quirk_engine_shift_down";
	/** Not keys of their own: V at Gear 3 engages them (their cooldowns are kept under these ids). */
	public static final String BURST = "quirk_engine_recipro_burst";
	public static final String EXTEND = "quirk_engine_recipro_extend";
	// X: the one dash
	public static final String DASH = "quirk_engine_recipro_dash";
	// every other key is a kick attack
	public static final String KICK = "quirk_engine_recipro_kick";
	public static final String SPIN = "quirk_engine_spin_kick";
	public static final String BARRAGE = "quirk_engine_kick_barrage";
	public static final String EXTEND_KICK = "quirk_engine_recipro_extend_kick";
	public static final String RISING = "quirk_engine_rising_kick";
	public static final String AXE = "quirk_engine_axe_kick";
	public static final String SWEEP = "quirk_engine_sweep_kick";
	public static final String TURBO = "quirk_engine_recipro_turbo";
	public static final String FINISHER = "quirk_engine_finisher";

	// ---------------------------------------------------------------- bosses
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most 10% max health a hit, no knockback, no carry. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.10f;

	// ---------------------------------------------------------------- passive
	/** 0.6 + 0.4 = a full block stepped without jumping (the engines hop him up kerbs at speed). */
	public static final double STEP_BONUS = 0.4;
	/** The view widens with speed; it never widens past this (vanilla would double it in Recipro). */
	public static final float FOV_CAP = 1.25f;

	// ---------------------------------------------------------------- the mode: the gearbox (V / Shift+V)
	public static final int MAX_GEAR = 3;
	/** Top speed of each gear with the run fully built (0, Speed III, Speed V, Speed VII). */
	public static final double[] GEAR_SPEED = { 0.0, 0.6, 1.0, 1.4 };
	/** A fresh gear starts at this fraction of its top speed; momentum builds the rest. */
	public static final double MOMENTUM_FLOOR = 0.4;
	/** Momentum gained a tick while sprinting forward in each gear (higher gears take longer to wind up: 1.25 s, 1.9 s, 2.5 s). */
	public static final float[] MOMENTUM_BUILD = { 0f, 0.04f, 0.026f, 0.02f };
	/** Walking (not sprinting) only builds up to this much. */
	public static final float MOMENTUM_WALK_CAP = 0.55f;
	/** Momentum lost a tick when he stops (nearly still). */
	public static final float MOMENTUM_BLEED = 0.08f;
	/** Recipro builds momentum this many times faster. */
	public static final float RECIPRO_BUILD_MULT = 3f;
	/** Shifting down keeps this fraction of the momentum (the engine braking). */
	public static final float DOWNSHIFT_KEEP = 0.85f;
	/** The speed modifier is updated in steps this fine (a few attribute syncs a second, not one a tick). */
	public static final double SPEED_STEP = 0.02;
	public static final int SHIFT_COOLDOWN = 5;
	public static final float SHIFT_UP_HEAT = 2f;

	// ---------------------------------------------------------------- the meter (Engine heat, per second)
	/** Sprinting in each gear (walking or standing in gear cools at IDLE_COOL; gear 0 cools fastest). */
	public static final float[] GEAR_HEAT_PER_SECOND = { 0f, 1.0f, 2.5f, 5.0f };
	/** In gear but walking or standing: the engines idle and cool slowly (passive cooling -- there is no vent key). */
	public static final float IDLE_COOL_PER_SECOND = 2.5f;
	/** Out of gear. */
	public static final float COOL_PER_SECOND = 6f;
	/** In water or rain the engines cool this much faster. */
	public static final float WATER_COOL_MULT = 2f;
	public static final float STALL_COOL_PER_SECOND = 8f;
	/** At 100 heat: Stall -- Slowness III, no moves, back to gear 0. */
	public static final int STALL_TICKS = 120;
	public static final int STALL_SLOW_AMPLIFIER = 2;

	// ---------------------------------------------------------------- sliding turns / bowling over
	/** A turn of the run this sharp (degrees in a tick) at this much momentum is a slide. */
	public static final double SLIDE_ANGLE = 28.0;
	public static final float SLIDE_MIN_MOMENTUM = 0.35f;
	/** A slide costs this fraction of the momentum. */
	public static final float SLIDE_MOMENTUM_LOSS = 0.25f;
	/** Client: how far the run can turn in a tick at full momentum in each gear (degrees); lower = more drift. */
	public static final float[] GRIP_DEGREES = { 180f, 26f, 17f, 11f };
	/** Running into a mob at gear 2+ with this much momentum bowls it over. */
	public static final int BOWL_MIN_GEAR = 2;
	public static final float BOWL_MIN_MOMENTUM = 0.45f;
	/** Bowling damage: this + this much per block-a-second of his speed. */
	public static final float BOWL_DAMAGE = 3f;
	public static final float BOWL_DAMAGE_PER_SPEED = 0.35f;
	public static final double BOWL_KNOCKBACK = 1.3;
	public static final double BOWL_LIFT = 0.45;
	/** The same mob isn't bowled again for this long. */
	public static final int BOWL_REHIT = 15;
	public static final float BOWL_MOMENTUM_LOSS = 0.08f;


	// ---------------------------------------------------------------- Recipro (inside the mode: V at Gear 3)
	/** V at Gear 3: Recipro Burst -- 10 s at double the gear's speed. Shift+V ends it (no stall). */
	public static final double BURST_MULT = 2.0;
	public static final int BURST_TICKS = 200;
	public static final float BURST_HEAT = 10f;
	public static final float BURST_HEAT_PER_SECOND = 3f;
	public static final int BURST_COOLDOWN = 400;
	/** V again during Burst: Recipro Extend -- 15 s at 2.6x; when it ends (Shift+V ends it early) the engines stall 6 s (Slowness III). */
	public static final double EXTEND_MULT = 2.6;
	public static final int EXTEND_TICKS = 300;
	public static final float EXTEND_HEAT = 12f;
	public static final float EXTEND_HEAT_PER_SECOND = 2f;
	public static final int EXTEND_STALL_TICKS = 120;
	public static final int EXTEND_COOLDOWN = 900;

	// ---------------------------------------------------------------- X: Recipro Dash (the one dash)
	/** A single burst dash along the look (flat): this far + this much per gear, over this many ticks. */
	public static final double DASH_DISTANCE = 7.0;
	public static final double DASH_PER_GEAR = 2.0;
	public static final int DASH_TICKS = 5;
	public static final float DASH_HEAT = 5f;
	public static final int DASH_COOLDOWN = 40;

	// ---------------------------------------------------------------- Shift+X: Rising Kick
	/** An engine uppercut kick that launches the target straight up. */
	public static final double RISING_REACH = 3.5;
	public static final float RISING_DAMAGE = 10f;
	public static final float RISING_DAMAGE_PER_SPEED = 0.4f;
	public static final double RISING_LAUNCH = 1.4;
	public static final double RISING_SELF_HOP = 0.45;
	public static final float RISING_HEAT = 6f;
	public static final int RISING_COOLDOWN = 100;

	// ---------------------------------------------------------------- R: Recipro Kick / Shift+R: Spin Kick
	/** Recipro Kick: this + this much per block-a-second of his speed (standing 5, Gear 3 flat out ~12, Recipro ~20). */
	public static final float KICK_DAMAGE = 5f;
	public static final float KICK_DAMAGE_PER_SPEED = 0.55f;
	public static final float KICK_DAMAGE_MAX = 28f;
	public static final double KICK_REACH = 3.5;
	public static final double KICK_KNOCKBACK = 0.9;
	public static final double KICK_KNOCKBACK_PER_SPEED = 0.06;
	public static final float KICK_HEAT = 3f;
	public static final int KICK_COOLDOWN = 16;

	public static final float SPIN_DAMAGE = 7f;
	public static final float SPIN_DAMAGE_PER_SPEED = 0.3f;
	public static final double SPIN_RADIUS = 3.5;
	public static final double SPIN_KNOCKBACK = 1.5;
	public static final double SPIN_LIFT = 0.3;
	public static final float SPIN_HEAT = 6f;
	public static final int SPIN_COOLDOWN = 60;

	// ---------------------------------------------------------------- G: Kick Barrage / Shift+G: Recipro Extend Kick
	public static final int BARRAGE_HITS = 6;
	public static final int BARRAGE_INTERVAL = 3;
	public static final double BARRAGE_REACH = 3.2;
	public static final float BARRAGE_DAMAGE = 3.5f;
	public static final float BARRAGE_DAMAGE_PER_SPEED = 0.15f;
	public static final double BARRAGE_KNOCKBACK = 0.15;
	public static final double BARRAGE_FINAL_KNOCKBACK = 1.1;
	public static final float BARRAGE_HEAT = 8f;
	public static final int BARRAGE_COOLDOWN = 120;

	/** The engines wind up for this long (he slows), then one heavy kick in front. */
	public static final int EXTEND_KICK_CHARGE = 10;
	public static final double EXTEND_KICK_REACH = 4.0;
	public static final float EXTEND_KICK_DAMAGE = 14f;
	public static final float EXTEND_KICK_DAMAGE_PER_SPEED = 0.5f;
	public static final float EXTEND_KICK_DAMAGE_MAX = 32f;
	public static final double EXTEND_KICK_KNOCKBACK = 2.2;
	public static final double EXTEND_KICK_LIFT = 0.4;
	public static final float EXTEND_KICK_HEAT = 10f;
	public static final int EXTEND_KICK_COOLDOWN = 140;

	// ---------------------------------------------------------------- Z: Axe Kick / Shift+Z: Sweep Kick
	/** Axe Kick: the heel comes down this many ticks after the press, on the target in front (and a small area round it). */
	public static final int AXE_DELAY = 6;
	public static final double AXE_REACH = 4.0;
	public static final double AXE_RADIUS = 2.0;
	public static final float AXE_DAMAGE = 12f;
	public static final float AXE_DAMAGE_PER_SPEED = 0.3f;
	/** Slammed into the ground: pushed down, Slowness II. */
	public static final double AXE_SLAM = 0.8;
	public static final int AXE_SLOW_TICKS = 40;
	public static final float AXE_HEAT = 6f;
	public static final int AXE_COOLDOWN = 80;

	/** Sweep Kick: a low sweep through a half circle in front; everything caught is tripped (lifted, slowed). */
	public static final double SWEEP_RADIUS = 3.5;
	public static final float SWEEP_DAMAGE = 7f;
	public static final float SWEEP_DAMAGE_PER_SPEED = 0.3f;
	public static final double SWEEP_LIFT = 0.55;
	public static final double SWEEP_KNOCKBACK = 0.3;
	public static final int SWEEP_SLOW_TICKS = 30;
	public static final float SWEEP_HEAT = 5f;
	public static final int SWEEP_COOLDOWN = 70;

	// ---------------------------------------------------------------- C: Recipro Turbo / Shift+C: Ingenium Finisher
	/** The ultimate: a blinding straight-line strike along the look (flat), this far over this many ticks; everything in the path struck. */
	public static final double TURBO_DISTANCE = 28.0;
	public static final int TURBO_TICKS = 7;
	public static final double TURBO_WIDTH = 1.6;
	public static final float TURBO_DAMAGE = 18f;
	public static final double TURBO_KNOCKBACK = 1.4;
	public static final double TURBO_LIFT = 0.4;
	/** Afterwards the engines are at 100: the stall follows. */
	public static final int TURBO_COOLDOWN = 600;

	/** The finishing combo: a Recipro Kick, a spin kick, then an afterburner launch (at these ticks). */
	public static final int FINISHER_SPIN_AT = 7;
	public static final int FINISHER_LAUNCH_AT = 14;
	public static final float FINISHER_KICK_DAMAGE = 6f;
	public static final float FINISHER_SPIN_DAMAGE = 7f;
	public static final float FINISHER_LAUNCH_DAMAGE = 12f;
	public static final float FINISHER_DAMAGE_PER_SPEED = 0.5f;
	public static final double FINISHER_LAUNCH = 2.4;
	public static final double FINISHER_LIFT = 0.6;
	public static final double FINISHER_REACH = 4.0;
	public static final float FINISHER_HEAT = 14f;
	public static final int FINISHER_COOLDOWN = 240;
}
