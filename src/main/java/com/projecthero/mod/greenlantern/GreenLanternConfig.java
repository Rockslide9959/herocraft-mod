package com.projecthero.mod.greenlantern;

/**
 * Every tunable number for the Green Lantern Hero-Tier power in one place, grouped by system. All
 * charge figures are in Ring Charge units (the pool is {@link #MAX_RING_CHARGE}); all durations are
 * in ticks (20/sec) unless the field name says otherwise. Sourced from the user's Green Lantern build
 * brief.
 */
public final class GreenLanternConfig {
	private GreenLanternConfig() {
	}

	// ---------------- Ring Charge ----------------

	public static final float MAX_RING_CHARGE = 10000f;
	public static final float PASSIVE_REGEN_PER_SEC = 2f;
	/** No passive regen until this many ticks after the last ability use. */
	public static final int PASSIVE_REGEN_DELAY_TICKS = 8 * 20;
	public static final float BATTERY_RECHARGE_PER_SEC = 500f;
	/**
	 * The last {@link #MAX_RING_CHARGE} - this many points are reserved for {@link #EMERGENCY_CATCH_COST}
	 * only -- never spendable on abilities/constructs/flight. {@code getSpendableCharge()} subtracts
	 * this from the raw pool.
	 */
	public static final float EMERGENCY_RESERVE = 250f;
	public static final float LOW_CHARGE_WARN_25 = 0.25f;
	public static final float LOW_CHARGE_WARN_10 = 0.10f;
	public static final float LOW_CHARGE_WARN_5 = 0.05f;

	// ---------------- suit ----------------

	public static final int SUIT_UP_TICKS = 16; // 0.8s
	public static final float SUIT_UP_COST = 100f;
	public static final int SUIT_DOWN_DEBOUNCE_TICKS = 5; // 0.25s
	public static final float SUIT_ARMOR_POINTS = 18f;
	public static final float SUIT_ARMOR_TOUGHNESS = 6f;
	public static final float SUIT_KNOCKBACK_RESIST = 0.20f;
	public static final float EMERGENCY_CATCH_COST = 100f;
	public static final int EMERGENCY_CATCH_COOLDOWN_TICKS = 80; // 4s
	public static final double CONSTRUCT_AWARENESS_RANGE = 48.0;
	public static final double THREAT_PING_RANGE = 10.0;

	// ---------------- Ring Bolt (R) / Continuous Beam (Shift+R) ----------------

	public static final float BOLT_DAMAGE = 7f;
	public static final float BOLT_COST = 250f;
	public static final int BOLT_COOLDOWN_TICKS = 7; // 0.35s
	public static final double BOLT_RANGE = 40.0;
	public static final double BOLT_KNOCKBACK = 0.4;

	public static final float BEAM_DAMAGE_PER_TICK = 3f; // every 5 ticks (0.25s) = 12 dps
	public static final int BEAM_TICK_INTERVAL = 5;
	public static final float BEAM_COST_PER_TICK = 90f; // every BEAM_TICK_INTERVAL ticks = 360/sec
	public static final double BEAM_RANGE = 32.0;
	public static final int BEAM_MAX_CHANNEL_TICKS = 8 * 20;
	public static final int BEAM_FORCED_COOLDOWN_TICKS = 30; // 1.5s
	public static final float BEAM_MOVEMENT_PENALTY = 0.20f;

	// ---------------- Construct Fist (G) / War Hammer Slam (Shift+G) ----------------

	public static final float FIST_DAMAGE = 10f;
	public static final float FIST_COST = 350f;
	public static final int FIST_COOLDOWN_TICKS = 25; // 1.25s
	public static final double FIST_RANGE = 12.0;
	public static final double FIST_KNOCKBACK = 4.0;

	public static final float HAMMER_CENTER_DAMAGE = 14f;
	public static final float HAMMER_OUTER_DAMAGE = 9f;
	public static final float HAMMER_COST = 700f;
	public static final int HAMMER_COOLDOWN_TICKS = 100; // 5s
	public static final double HAMMER_RADIUS = 4.5;
	public static final double HAMMER_KNOCKUP = 0.55;

	// ---------------- Suit Up/Down (V) / Ring Scan (Shift+V) ----------------

	public static final float SCAN_COST = 300f;
	public static final int SCAN_COOLDOWN_TICKS = 240; // 12s
	public static final double SCAN_RADIUS = 24.0;
	public static final int SCAN_DURATION_TICKS = 120; // 6s
	public static final double SCAN_ITEM_RADIUS = 16.0;

	// ---------------- Flight (X) / Boost (Shift+X) ----------------

	public static final double FLIGHT_CRUISE_SPEED_BPS = 12.0;
	public static final double FLIGHT_VERTICAL_SPEED_BPS = 8.0;
	public static final float FLIGHT_CRUISE_COST_PER_SEC = 12f;
	public static final float FLIGHT_HOVER_COST_PER_SEC = 5f;
	public static final double BOOST_SPEED_BPS = 22.0;
	public static final double BOOST_VERTICAL_SPEED_BPS = 15.0;
	public static final float BOOST_COST_PER_SEC = 40f;
	public static final int EMERGENCY_DESCENT_TICKS = 60; // 3s

	// ---------------- Directional Shield (Z) / Protective Dome (Shift+Z) ----------------

	public static final float SHIELD_INITIAL_COST = 250f;
	public static final float SHIELD_UPKEEP_PER_SEC = 60f;
	public static final float SHIELD_HP = 80f;
	public static final int SHIELD_BREAK_COOLDOWN_TICKS = 80; // 4s
	public static final double SHIELD_ARC_DEGREES = 120.0;

	public static final float DOME_INITIAL_COST = 900f;
	public static final float DOME_UPKEEP_PER_SEC = 120f;
	public static final float DOME_HP = 250f;
	public static final double DOME_RADIUS = 5.0;
	public static final int DOME_MAX_DURATION_TICKS = 15 * 20;
	public static final int DOME_COOLDOWN_TICKS = 12 * 20;

	// ---------------- constructs (generic) ----------------

	public static final int CONSTRUCT_MAX_SLOTS = 6;
	public static final double CONSTRUCT_PLACE_RANGE = 24.0;
	public static final double CONSTRUCT_OWNER_OUTLINE_RANGE = 48.0;
	public static final int CONSTRUCT_WHEEL_HOLD_TICKS = 7; // 0.35s

	// ---------------- combat constructs ----------------

	public static final float ENERGY_BLADE_COST = 200f;
	public static final float ENERGY_BLADE_UPKEEP_PER_SEC = 18f;
	public static final float ENERGY_BLADE_DAMAGE = 9f;
	public static final double ENERGY_BLADE_REACH = 2.8;

	public static final float CAGE_COST = 600f;
	public static final float CAGE_UPKEEP_PER_SEC = 25f;
	public static final double CAGE_RANGE = 18.0;
	public static final int CAGE_MAX_DURATION_TICKS = 8 * 20;
	public static final int CAGE_COOLDOWN_TICKS = 8 * 20;
	public static final float CAGE_HP = 75f;

	public static final float TURRET_COST = 850f;
	public static final float TURRET_UPKEEP_PER_SEC = 45f;
	public static final float TURRET_DAMAGE = 4f;
	public static final int TURRET_FIRE_INTERVAL_TICKS = 10; // 2 shots/sec
	public static final double TURRET_TARGET_RADIUS = 20.0;
	public static final int TURRET_MAX_DURATION_TICKS = 12 * 20;
	public static final int TURRET_COOLDOWN_TICKS = 20 * 20;

	public static final float RAM_COST = 650f;
	public static final float RAM_DAMAGE = 12f;
	public static final double RAM_DISTANCE = 16.0;
	public static final int RAM_COOLDOWN_TICKS = 80; // 4s

	public static final float WALL_COST = 500f;
	public static final float WALL_UPKEEP_PER_SEC = 40f;
	public static final float WALL_HP = 160f;
	public static final int WALL_MAX_DURATION_TICKS = 15 * 20;
	public static final int WALL_COOLDOWN_TICKS = 5 * 20;

	// ---------------- utility / survival constructs ----------------

	public static final float PLATFORM_COST = 300f;
	public static final float PLATFORM_UPKEEP_PER_SEC = 20f;
	public static final int PLATFORM_MAX_DURATION_TICKS = 20 * 20;
	public static final int PLATFORM_SLOT_WEIGHT = 4;

	public static final float BRIDGE_BASE_COST = 120f;
	public static final float BRIDGE_COST_PER_SEGMENT = 35f;
	public static final float BRIDGE_UPKEEP_PER_SEC = 10f;
	public static final int BRIDGE_MAX_LENGTH = 20;
	public static final int BRIDGE_MAX_DURATION_TICKS = 30 * 20;
	public static final int BRIDGE_SLOT_WEIGHT = 2;

	public static final float RAMP_BASE_COST = 150f;
	public static final float RAMP_COST_PER_SEGMENT = 30f;
	public static final float RAMP_UPKEEP_PER_SEC = 10f;
	public static final int RAMP_MAX_SEGMENTS = 12;
	public static final int RAMP_MAX_DURATION_TICKS = 30 * 20;
	public static final int RAMP_SLOT_WEIGHT = 2;

	public static final float DRILL_START_COST = 200f;
	public static final float DRILL_COST_PER_BLOCK = 25f;
	public static final double DRILL_REACH = 5.0;
	public static final int DRILL_SLOT_WEIGHT = 1;

	public static final float LANTERN_LIGHT_COST = 100f;
	public static final float LANTERN_LIGHT_UPKEEP_PER_SEC = 5f;
	public static final int LANTERN_LIGHT_MAX_DURATION_TICKS = 60 * 20;
	public static final int LANTERN_LIGHT_SLOT_WEIGHT = 1;

	public static final float BUBBLE_COST = 450f;
	public static final float BUBBLE_UPKEEP_PER_SEC = 35f;
	public static final double BUBBLE_RADIUS = 2.5;
	public static final int BUBBLE_MAX_DURATION_TICKS = 30 * 20;
	public static final int BUBBLE_SLOT_WEIGHT = 2;

	public static final float TETHER_COST = 200f;
	public static final float TETHER_UPKEEP_PER_SEC = 15f;
	public static final double TETHER_RANGE = 24.0;
	public static final int TETHER_MAX_PULL_TICKS = 8 * 20;
	public static final int TETHER_SLOT_WEIGHT = 1;
	public static final double TETHER_PULL_SPEED_BPS = 5.0;

	public static final float CARRY_PLATFORM_COST = 600f;
	public static final float CARRY_PLATFORM_UPKEEP_PER_SEC = 30f;
	public static final int CARRY_PLATFORM_MAX_DURATION_TICKS = 30 * 20;
	public static final int CARRY_PLATFORM_SLOT_WEIGHT = 3;
	public static final double CARRY_PLATFORM_SPEED_CAP_BPS = 8.0;

	// ---------------- mastery / progression ----------------

	public static final long MASTERY_I_ENERGY = 20000L;
	public static final long MASTERY_II_ENERGY = 60000L;
	public static final float MASTERY_II_DAMAGE_BLOCKED = 500f;
	public static final long MASTERY_III_ENERGY = 140000L;
	public static final double MASTERY_III_FLIGHT_DISTANCE = 10000.0;
	public static final long MASTERY_IV_ENERGY = 250000L;
	public static final float EFFICIENT_FOCUS_UPKEEP_DISCOUNT = 0.10f;

	// ---------------- Will Trial ----------------

	public static final double TRIAL_RADIUS = 32.0;
	public static final int TRIAL_LEAVE_FAIL_TICKS = 8 * 20;
	public static final int TRIAL_FAIL_COOLDOWN_TICKS = 10 * 60 * 20;
	public static final int TRIAL_WAVE_1_COUNT = 6;
	public static final int TRIAL_WAVE_2_COUNT = 8;
	public static final int TRIAL_WAVE_3_ORDINARY_COUNT = 4;
}
