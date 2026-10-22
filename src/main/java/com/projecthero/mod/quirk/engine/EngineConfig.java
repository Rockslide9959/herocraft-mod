package com.projecthero.mod.quirk.engine;

/**
 * v0.16.7 (revamp): every Engine (Tenya Iida / Ingenium) number. Ticks unless named otherwise; heat is the 0..100 meter.
 * Run speeds are blocks a second sprinting on flat ground ({@link #GEAR_TOP_BPS}); {@code EngineMoves.speedBonus} turns
 * them into the movement-speed modifier.
 *
 * <p>These are code constants, not a config file, so there is nothing on disk to migrate: a world that ran the old kit
 * just reads the new numbers. Old save data (the pre-revamp gear timer and kick keys) is dropped by
 * {@link EngineMoves#tick} the first time it sees them.
 */
public final class EngineConfig {
	private EngineConfig() {
	}

	// ---------------------------------------------------------------- ability ids (kept ids keep their cooldown saves)
	// V / Shift+V: the gearbox (v0.16.7 user: Recipro Burst / Extend on V at Gear 3 are gone -- Recipro Turbo, C, replaced them)
	public static final String SHIFT_UP = "quirk_engine_shift_up";
	public static final String SHIFT_DOWN = "quirk_engine_shift_down";
	// X: the one dash (in Recipro Turbo it is the Turbo Strike)
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
	/**
	 * v0.16.7 (user): each gear's top speed sprinting with the run fully built, blocks a second -- Gear 1 = 20, Gear 2 = 30,
	 * Gear 3 = 40 (Recipro Turbo doubles it). Driven the way Super Speed is: a big ADD_MULTIPLIED_BASE movement-speed
	 * modifier (vanilla's Speed effect tops out far below this) plus the client's run handling ({@code EngineRunClient}).
	 */
	public static final double[] GEAR_TOP_BPS = { 0.0, 20.0, 30.0, 40.0 };
	/** Vanilla sprinting on flat ground, blocks a second (0.1 x 1.3 x 43.17, see {@code SuperSpeedHandlers.BLOCKS_PER_SECOND_PER_SPEED}). */
	public static final double VANILLA_SPRINT_BPS = 0.1 * 1.3 * (0.98 / (1.0 - 0.6 * 0.91) * 20.0);
	/** A fresh gear starts at this fraction of its top speed; momentum builds the rest. */
	public static final double MOMENTUM_FLOOR = 0.4;
	/** Momentum gained a tick while sprinting forward in each gear (0 -> top in 0.85 s, 1.1 s, 1.4 s: it should feel fast). */
	public static final float[] MOMENTUM_BUILD = { 0f, 0.06f, 0.045f, 0.035f };
	/** Walking (not sprinting) only builds up to this much. */
	public static final float MOMENTUM_WALK_CAP = 0.55f;
	/** Momentum lost a tick when he stops (nearly still). */
	public static final float MOMENTUM_BLEED = 0.08f;
	/** Recipro Turbo builds momentum this many times faster. */
	public static final float TURBO_BUILD_MULT = 3f;
	/** Shifting down keeps this fraction of the momentum (the engine braking). */
	public static final float DOWNSHIFT_KEEP = 0.85f;
	/** The speed modifier is updated in steps this fine (a few attribute syncs a second, not one a tick). */
	public static final double SPEED_STEP = 0.05;
	/** Client: in the air the run eases back up toward its take-off speed (so a jump doesn't kill a 40 b/s run). */
	public static final double AIR_CARRY = 0.5;
	/** ...up to this many blocks a tick (Turbo at Gear 3 is 4). */
	public static final double AIR_CARRY_MAX = 4.5;
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
	/** v0.16.7 (user): wearing the Ingenium suit (chestplate, leggings, boots) the engines heat 50% slower. */
	public static final float SUIT_HEAT_MULT = 0.5f;
	/** A stalled engine coughs (steam, a sputter) every this many ticks, give or take. */
	public static final int SPUTTER_EVERY = 14;

	// ---------------------------------------------------------------- sliding turns / bowling over
	/** A turn of the run this sharp (degrees in a tick) at this much momentum is a slide. */
	public static final double SLIDE_ANGLE = 28.0;
	public static final float SLIDE_MIN_MOMENTUM = 0.35f;
	/** A slide costs this fraction of the momentum. */
	public static final float SLIDE_MOMENTUM_LOSS = 0.25f;
	/**
	 * Client: how far the run can turn in a tick at full momentum in each gear (degrees); lower = more drift. v0.16.7:
	 * re-tuned for 20 / 30 / 40 b/s (was 26 / 17 / 11 for ~13 b/s) -- at Gear 3 flat out the tightest turn is ~6 blocks
	 * across, in Turbo (x {@link #TURBO_GRIP}) ~16; at lower momentum it grips more ({@link #grip}).
	 */
	public static final float[] GRIP_DEGREES = { 180f, 32f, 25f, 19f };
	public static final float TURBO_GRIP = 0.8f;

	/** The grip (degrees a tick) of the run in a gear at a momentum, Turbo or not. */
	public static float grip(int gear, float momentum, boolean turbo) {
		int g = Math.max(0, Math.min(MAX_GEAR, gear));
		float m = Math.max(0f, Math.min(1f, momentum));
		return GRIP_DEGREES[g] * (1.6f - 0.6f * m) * (turbo ? TURBO_GRIP : 1f);
	}
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
	public static final float BOWL_DAMAGE_MAX = 20f;
	/** Speed-scaled damage counts his speed only up to this (blocks a second): a Gear 3 run already hits as hard as it gets. */
	public static final double DAMAGE_BPS_CAP = 40.0;

	// ---------------------------------------------------------------- C: Recipro Turbo -- the mode (v0.16.7 user)
	/**
	 * C: 30 s at double the gear's speed (Gear 3 = 80 b/s), heat frozen (no build, no stall), every hit +50%. C again ends
	 * it early. When it ends the engines stall for a full minute: no gears, no moves, a slow walk, steam and sputtering.
	 */
	public static final int TURBO_TICKS = 600;
	public static final double TURBO_MULT = 2.0;
	public static final float TURBO_DAMAGE_MULT = 1.5f;
	public static final int TURBO_STALL_TICKS = 1200;
	/** From the moment it's engaged: the 30 s run + the 60 s stall + 30 s more. */
	public static final int TURBO_COOLDOWN = 2400;

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

	// ---------------------------------------------------------------- X in Recipro Turbo: the Turbo Strike / Shift+C: Ingenium Finisher
	/**
	 * X while Recipro Turbo runs: the old ultimate strike rides on the dash -- a blinding straight-line rush along the look
	 * (flat), this far over this many ticks, everything in the path struck (x{@link #TURBO_DAMAGE_MULT} like every Turbo hit).
	 */
	public static final double STRIKE_DISTANCE = 28.0;
	public static final int STRIKE_TICKS = 7;
	public static final double STRIKE_WIDTH = 1.6;
	public static final float STRIKE_DAMAGE = 18f;
	public static final double STRIKE_KNOCKBACK = 1.4;
	public static final double STRIKE_LIFT = 0.4;
	/** The dash key's cooldown after a Turbo Strike. */
	public static final int STRIKE_COOLDOWN = 100;

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
