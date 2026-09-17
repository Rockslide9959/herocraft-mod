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
	/**
	 * The Oath sequence (v0.11.4): right-clicking a Power Battery below max charge recites this many
	 * lines, {@link #OATH_LINE_TICKS} apart, then fills the ring instantly -- there is no other way to
	 * recharge (no passive regen, no per-second channel). Any movement/look/damage/ability-use during
	 * the recitation cancels it (see {@link GreenLanternBattery}).
	 */
	public static final int OATH_LINE_TICKS = 30; // 1.5s/line
	public static final double OATH_MOVE_EPSILON = 0.01;
	public static final float OATH_ROT_EPSILON = 0.5f;
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
	/** v0.11.5: cut from 100 -- summoning the suit is now a cheap gesture, not a real charge investment. */
	public static final float SUIT_UP_COST = 10f;
	/** v0.11.5: 1 charge every 5 seconds while worn -- the suit is no longer free to keep on. */
	public static final float SUIT_UPKEEP_COST = 1f;
	public static final int SUIT_UPKEEP_INTERVAL_TICKS = 100; // 5s
	public static final int SUIT_DOWN_DEBOUNCE_TICKS = 5; // 0.25s
	/** v0.11.4: diamond-level parity (see {@code ModArmorMaterials#GREEN_LANTERN}, mirrors MAX_STEEL's). */
	public static final float SUIT_ARMOR_POINTS = 20f;
	public static final float SUIT_ARMOR_TOUGHNESS = 2f;
	public static final float SUIT_KNOCKBACK_RESIST = 0.0f;
	/** v0.11.4: flat melee bonus while the suit is on ({@code GreenLanternSuitArmor}). */
	public static final float SUIT_MELEE_BONUS = 8f;
	public static final float EMERGENCY_CATCH_COST = 100f;
	public static final int EMERGENCY_CATCH_COOLDOWN_TICKS = 80; // 4s
	public static final double CONSTRUCT_AWARENESS_RANGE = 48.0;
	public static final double THREAT_PING_RANGE = 10.0;

	// ---------------- Ring Bolt (R) / Continuous Beam (Shift+R) ----------------

	public static final float BOLT_DAMAGE = 13f;
	public static final float BOLT_COST = 10f;
	public static final int BOLT_COOLDOWN_TICKS = 7; // 0.35s
	public static final double BOLT_RANGE = 40.0;
	public static final double BOLT_KNOCKBACK = 0.4;

	public static final float BEAM_DAMAGE_PER_TICK = 4f; // every 10 ticks (0.5s) = 8 dps
	public static final int BEAM_TICK_INTERVAL = 10;
	public static final float BEAM_COST_PER_TICK = 5f; // every BEAM_TICK_INTERVAL ticks = 10/sec
	public static final double BEAM_RANGE = 32.0;
	public static final int BEAM_MAX_CHANNEL_TICKS = 8 * 20;
	public static final int BEAM_FORCED_COOLDOWN_TICKS = 30; // 1.5s
	public static final float BEAM_MOVEMENT_PENALTY = 0.20f;

	// ---------------- Construct Fist (G) / War Hammer Slam (Shift+G) ----------------

	public static final float FIST_DAMAGE = 16f;
	public static final float FIST_COST = 30f;
	public static final int FIST_COOLDOWN_TICKS = 40; // 2s
	public static final double FIST_RANGE = 12.0;
	public static final double FIST_KNOCKBACK = 4.0;

	/** v0.11.4: flattened to one flat number -- the center/outer split was removed. */
	public static final float HAMMER_CENTER_DAMAGE = 17f;
	public static final float HAMMER_OUTER_DAMAGE = 17f;
	public static final float HAMMER_COST = 40f;
	public static final int HAMMER_COOLDOWN_TICKS = 100; // 5s
	public static final double HAMMER_RADIUS = 4.5;
	public static final double HAMMER_KNOCKUP = 0.55;
	/** Above this max health, War Hammer Slam still deals full damage but only 25% normal knockback. */
	public static final double HAMMER_BOSS_MAX_HEALTH_THRESHOLD = 200.0;

	// ---------------- Suit Up/Down (V) / Ring Scan (Shift+V) ----------------

	public static final float SCAN_COST = 300f;
	public static final int SCAN_COOLDOWN_TICKS = 240; // 12s
	public static final double SCAN_RADIUS = 24.0;
	public static final int SCAN_DURATION_TICKS = 120; // 6s
	public static final double SCAN_ITEM_RADIUS = 16.0;

	// ---------------- Flight (double-tap Space) / Boost (Shift+Sprint while flying) ----------------

	public static final double FLIGHT_CRUISE_SPEED_BPS = 12.0;
	public static final double FLIGHT_VERTICAL_SPEED_BPS = 8.0;
	/** v0.11.5: flat cost regardless of hovering or cruising -- replaces the old cruise/hover split. */
	public static final float FLIGHT_COST_PER_SEC = 1f;
	public static final double BOOST_SPEED_BPS = 22.0;
	public static final double BOOST_VERTICAL_SPEED_BPS = 15.0;
	public static final float BOOST_COST_PER_SEC = 40f;
	/** v0.11.4: the sprint-flying trail's own drain, added on top of {@link #BOOST_COST_PER_SEC}. */
	public static final float FLIGHT_TRAIL_COST_PER_SEC = 1f;
	public static final int EMERGENCY_DESCENT_TICKS = 60; // 3s

	// ---------------- Ring Grapple (X, v0.11.5 -- replaces the flight toggle that moved to double-tap Space) ----------------

	public static final float GRAPPLE_COST = 30f;
	public static final int GRAPPLE_COOLDOWN_TICKS = 60; // 3s
	public static final double GRAPPLE_RANGE = 28.0;
	/** Per-tick velocity magnitude cap on the pull, so a grapple onto a far wall doesn't fling the player. */
	public static final double GRAPPLE_MAX_SPEED = 2.2;

	// ---------------- Directional Shield (Z) / Protective Dome (Shift+Z) ----------------

	// v0.11.5: both cut to well under a fifth of their original cost -- "way less" per the user's request.
	public static final float SHIELD_INITIAL_COST = 45f;
	public static final float SHIELD_UPKEEP_PER_SEC = 10f;
	public static final float SHIELD_HP = 80f;
	public static final int SHIELD_BREAK_COOLDOWN_TICKS = 80; // 4s
	public static final double SHIELD_ARC_DEGREES = 120.0;

	public static final float DOME_INITIAL_COST = 160f;
	public static final float DOME_UPKEEP_PER_SEC = 20f;
	public static final float DOME_HP = 250f;
	public static final double DOME_RADIUS = 5.0;
	public static final int DOME_MAX_DURATION_TICKS = 15 * 20;
	public static final int DOME_COOLDOWN_TICKS = 12 * 20;

	// ---------------- constructs (generic) ----------------

	public static final int CONSTRUCT_MAX_SLOTS = 20;
	public static final double CONSTRUCT_PLACE_RANGE = 24.0;
	public static final double CONSTRUCT_OWNER_OUTLINE_RANGE = 48.0;
	public static final int CONSTRUCT_WHEEL_HOLD_TICKS = 10; // 0.5s

	// ---------------- combat constructs ----------------
	// v0.11.4: every deploy cost/upkeep in this section and the next cut to ~20% of its original value
	// ("make construct making and maintaining cost way less").

	public static final float ENERGY_BLADE_COST = 40f;
	public static final float ENERGY_BLADE_UPKEEP_PER_SEC = 4f;
	public static final float ENERGY_BLADE_DAMAGE = 9f;
	public static final double ENERGY_BLADE_REACH = 2.8;

	public static final float CAGE_COST = 120f;
	public static final float CAGE_UPKEEP_PER_SEC = 5f;
	public static final double CAGE_RANGE = 18.0;
	public static final int CAGE_MAX_DURATION_TICKS = 8 * 20;
	public static final int CAGE_COOLDOWN_TICKS = 8 * 20;
	public static final float CAGE_HP = 75f;

	public static final float TURRET_COST = 170f;
	public static final float TURRET_UPKEEP_PER_SEC = 9f;
	public static final float TURRET_DAMAGE = 4f;
	public static final int TURRET_FIRE_INTERVAL_TICKS = 10; // 2 shots/sec
	public static final double TURRET_TARGET_RADIUS = 20.0;
	public static final int TURRET_MAX_DURATION_TICKS = 12 * 20;
	public static final int TURRET_COOLDOWN_TICKS = 20 * 20;

	public static final float RAM_COST = 130f;
	public static final float RAM_DAMAGE = 12f;
	public static final double RAM_DISTANCE = 16.0;
	public static final int RAM_COOLDOWN_TICKS = 80; // 4s

	public static final float WALL_COST = 100f;
	public static final float WALL_UPKEEP_PER_SEC = 8f;
	public static final float WALL_HP = 160f;
	public static final int WALL_MAX_DURATION_TICKS = 15 * 20;
	public static final int WALL_COOLDOWN_TICKS = 5 * 20;

	// ---------------- utility / survival constructs ----------------

	public static final float PLATFORM_COST = 60f;
	public static final float PLATFORM_UPKEEP_PER_SEC = 4f;
	public static final int PLATFORM_MAX_DURATION_TICKS = 20 * 20;
	public static final int PLATFORM_SLOT_WEIGHT = 4;

	public static final float BRIDGE_BASE_COST = 24f;
	public static final float BRIDGE_COST_PER_SEGMENT = 7f;
	public static final float BRIDGE_UPKEEP_PER_SEC = 2f;
	public static final int BRIDGE_MAX_LENGTH = 20;
	public static final int BRIDGE_MAX_DURATION_TICKS = 30 * 20;
	public static final int BRIDGE_SLOT_WEIGHT = 2;

	public static final float RAMP_BASE_COST = 30f;
	public static final float RAMP_COST_PER_SEGMENT = 6f;
	public static final float RAMP_UPKEEP_PER_SEC = 2f;
	public static final int RAMP_MAX_SEGMENTS = 12;
	public static final int RAMP_MAX_DURATION_TICKS = 30 * 20;
	public static final int RAMP_SLOT_WEIGHT = 2;

	public static final float DRILL_START_COST = 40f;
	public static final float DRILL_COST_PER_BLOCK = 5f;
	public static final double DRILL_REACH = 5.0;
	public static final int DRILL_SLOT_WEIGHT = 1;

	public static final float LANTERN_LIGHT_COST = 20f;
	public static final float LANTERN_LIGHT_UPKEEP_PER_SEC = 1f;
	public static final int LANTERN_LIGHT_MAX_DURATION_TICKS = 60 * 20;
	public static final int LANTERN_LIGHT_SLOT_WEIGHT = 1;

	public static final float BUBBLE_COST = 90f;
	public static final float BUBBLE_UPKEEP_PER_SEC = 7f;
	public static final double BUBBLE_RADIUS = 2.5;
	public static final int BUBBLE_MAX_DURATION_TICKS = 30 * 20;
	public static final int BUBBLE_SLOT_WEIGHT = 2;

	// v0.11.5: Rescue Tether is now an instant grab (a one-shot pull), not a standing construct -- see
	// GreenLanternConstructs#rescueGrab. No more upkeep, slot weight or persisted pull duration.
	public static final float TETHER_COST = 40f;
	public static final double TETHER_RANGE = 24.0;
	public static final int TETHER_COOLDOWN_TICKS = 80; // 4s

	public static final float CARRY_PLATFORM_COST = 120f;
	public static final float CARRY_PLATFORM_UPKEEP_PER_SEC = 6f;
	public static final int CARRY_PLATFORM_MAX_DURATION_TICKS = 30 * 20;
	public static final int CARRY_PLATFORM_SLOT_WEIGHT = 3;
	public static final double CARRY_PLATFORM_SPEED_CAP_BPS = 8.0;

	/** v0.11.4: Hard-Light Tool Kit -- a diamond pickaxe/axe/shovel, dismiss-only (dropping any one ends it). */
	public static final float TOOL_KIT_COST = 60f;
	public static final float TOOL_KIT_UPKEEP_PER_SEC = 3f;
	public static final int TOOL_KIT_SLOT_WEIGHT = 2;

	// ---------------- Will Trial ----------------

	public static final double TRIAL_RADIUS = 32.0;
	public static final int TRIAL_LEAVE_FAIL_TICKS = 8 * 20;
	public static final int TRIAL_FAIL_COOLDOWN_TICKS = 10 * 60 * 20;
	public static final int TRIAL_WAVE_1_COUNT = 6;
	public static final int TRIAL_WAVE_2_COUNT = 8;
	public static final int TRIAL_WAVE_3_ORDINARY_COUNT = 4;
}
