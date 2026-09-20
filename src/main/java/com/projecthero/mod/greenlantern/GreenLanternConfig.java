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
	 * The last {@link #MAX_RING_CHARGE} - this many points are reserved for an emergency flight descent
	 * only -- never spendable on abilities/constructs/flight upkeep. {@code getSpendableCharge()}
	 * subtracts this from the raw pool.
	 */
	public static final float EMERGENCY_RESERVE = 250f;
	/**
	 * v0.11.7: eight escalating low-charge warnings (was three) -- descending so
	 * {@link GreenLanternEnergy#triggerLowChargeFeedback} can find the lowest (most severe) threshold
	 * crossed since the last check in one pass. Each is a percentage of {@link #MAX_RING_CHARGE}.
	 */
	public static final float[] LOW_CHARGE_WARN_THRESHOLDS = {0.40f, 0.35f, 0.30f, 0.25f, 0.20f, 0.15f, 0.10f, 0.05f};

	/** v0.11.7: a flat passive Resistance I while the ring is bonded (not suit-gated). */
	public static final int RING_RESISTANCE_AMPLIFIER = 0; // Resistance I

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
	/** v0.11.6: explicit user request -- a 10-block radius from the impact point, up from 4.5. */
	public static final double HAMMER_RADIUS = 10.0;
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

	// ---------------- Green Lantern's Light (X, v0.11.7 -- replaces Ring Grapple) ----------------

	/** Ticks per oath line while reciting -- reuses the Power Battery Oath's own cadence (30 = 1.5s/line). */
	public static final int OATH_MODE_LINE_TICKS = OATH_LINE_TICKS;
	public static final int OATH_MODE_LINE_COUNT = 4;
	public static final int OATH_MODE_RECITE_TICKS = OATH_MODE_LINE_TICKS * OATH_MODE_LINE_COUNT; // 6s
	public static final int OATH_MODE_DURATION_TICKS = 22 * 20; // 22s
	public static final int OATH_MODE_COOLDOWN_TICKS = 80 * 20; // 80s, applied once the mode ends
	/** Flat drain for simply being in the mode, on top of every ability/construct/upkeep cost doubling. */
	public static final float OATH_MODE_UPKEEP_PER_SEC = 10f;
	public static final float OATH_MODE_MULTIPLIER = 2f;

	// ---------------- Directional Shield (Z) / Protective Dome (Shift+Z) ----------------

	// v0.11.5: both cut to well under a fifth of their original cost -- "way less" per the user's request.
	public static final float SHIELD_INITIAL_COST = 45f;
	public static final float SHIELD_UPKEEP_PER_SEC = 10f;
	public static final float SHIELD_HP = 80f;
	public static final int SHIELD_BREAK_COOLDOWN_TICKS = 80; // 4s
	public static final double SHIELD_ARC_DEGREES = 120.0;

	public static final float DOME_INITIAL_COST = 160f;
	/** v0.11.7: cut from 20 -- explicit user request ("cost 5 energy per second to maintain"). */
	public static final float DOME_UPKEEP_PER_SEC = 5f;
	public static final float DOME_HP = 250f;
	/** v0.11.7: expands from the caster out to this radius, up from a fixed 5 -- explicit user request. */
	public static final double DOME_RADIUS = 10.0;
	/** How long the dome takes to grow from 0 to {@link #DOME_RADIUS} once deployed. */
	public static final int DOME_EXPAND_TICKS = 30; // 1.5s
	/** v0.11.7 break/collapse cooldown (HP hitting 0, or running out of charge) -- kept distinct from
	 *  {@link #BARRIER_TOGGLE_COOLDOWN_TICKS}, the new v0.11.8 cooldown for choosing to end it early. */
	public static final int DOME_COOLDOWN_TICKS = 12 * 20;
	/** Outward push speed (blocks/tick) applied to a non-squadmate caught inside the dome's radius. */
	public static final double DOME_PUSH_SPEED = 0.5;

	/**
	 * v0.11.8: the Directional Shield/Protective Dome's shared "uptime" meter -- explicit user request
	 * ("the dome bar doesn't deplete as the dome usage goes up ... the player can only have the dome up
	 * for 22 seconds maximum ... the bar passively recharges as the player stops using the dome"). Drains
	 * to 0 over this many ticks of continuous active use (shield or dome, they share one meter), refills
	 * at the same rate while neither is up. Replaces the old fixed {@code DOME_MAX_DURATION_TICKS} cap --
	 * the meter reaching 0 now IS the max-duration expiry.
	 */
	public static final int BARRIER_METER_MAX_TICKS = 22 * 20; // 22s
	/** v0.11.8: explicit user request -- "when they toggle off the dome it goes on an 8 second cooldown". */
	public static final int BARRIER_TOGGLE_COOLDOWN_TICKS = 8 * 20; // 8s

	// ---------------- constructs (generic) ----------------

	// v0.11.7: the weighted-slot cap is gone outright -- explicit user request ("remove construct
	// limit"). ConstructType#slotWeight() values are kept (still read by the HUD/accounting helpers)
	// but nothing compares the total against a ceiling any more.
	public static final double CONSTRUCT_PLACE_RANGE = 24.0;
	public static final double CONSTRUCT_OWNER_OUTLINE_RANGE = 48.0;
	public static final int CONSTRUCT_WHEEL_HOLD_TICKS = 10; // 0.5s

	// ---------------- combat constructs ----------------
	// v0.11.7: every construct's cost/upkeep/range renumbered again to the user's explicit per-construct
	// figures below (this pass replaces the old v0.11.4 "~20% of original" table entirely).

	/** v0.11.7: deploying now just equips the stance for free -- press C again (while it's the active
	 *  instance) to toggle the blade itself on, which is what actually costs anything. */
	public static final float ENERGY_BLADE_COST = 0f;
	public static final float ENERGY_BLADE_UPKEEP_PER_SEC = 2f; // only drains while toggled on
	public static final float ENERGY_BLADE_DAMAGE = 9f;
	public static final double ENERGY_BLADE_REACH = 2.8;

	public static final float CAGE_COST = 20f;
	public static final float CAGE_UPKEEP_PER_SEC = 1f;
	public static final double CAGE_RANGE = 30.0;
	/** v0.11.9: 15s, up from 8 -- explicit user request. No post-collapse cooldown any more either
	 *  (also explicit user request, "remove cooldowns and just make it disappear after 15 seconds") --
	 *  see {@code GreenLanternConstructs#cooldownIdFor}. */
	public static final int CAGE_MAX_DURATION_TICKS = 15 * 20;
	public static final float CAGE_HP = 75f;

	public static final float TURRET_COST = 20f;
	public static final float TURRET_UPKEEP_PER_SEC = 1f;
	public static final float TURRET_DAMAGE = 4f;
	public static final int TURRET_FIRE_INTERVAL_TICKS = 10; // 2 shots/sec
	public static final double TURRET_TARGET_RADIUS = 20.0;
	public static final int TURRET_MAX_DURATION_TICKS = 12 * 20;
	public static final int TURRET_COOLDOWN_TICKS = 20 * 20;
	/** v0.11.7: a hard per-player cap on live turrets specifically -- separate from the (now removed) generic
	 *  construct-slot limit. v0.11.8: cut from 10 to 5 -- explicit user request. */
	public static final int TURRET_MAX_LIVE = 5;

	public static final float RAM_COST = 20f;
	public static final float RAM_DAMAGE = 12f;
	public static final double RAM_DISTANCE = 16.0;
	public static final int RAM_COOLDOWN_TICKS = 80; // 4s

	public static final float WALL_COST = 20f;
	public static final float WALL_UPKEEP_PER_SEC = 1f;
	public static final float WALL_HP = 160f;
	/** v0.11.7: 4 blocks tall, up from 3 -- explicit user request. */
	public static final int WALL_HEIGHT = 4;
	public static final int WALL_MAX_DURATION_TICKS = 15 * 20;
	public static final int WALL_COOLDOWN_TICKS = 5 * 20;

	// ---------------- utility / survival constructs ----------------

	public static final float PLATFORM_COST = 20f;
	public static final float PLATFORM_UPKEEP_PER_SEC = 1f;
	/** v0.11.7: a 4x4 footprint, up from 3x3 -- explicit user request. */
	public static final int PLATFORM_SIZE = 4;
	/** v0.11.7: its own placement range (was the generic 24) -- explicit user request. */
	public static final double PLATFORM_RANGE = 30.0;
	public static final int PLATFORM_MAX_DURATION_TICKS = 20 * 20;
	public static final int PLATFORM_SLOT_WEIGHT = 4;

	/** v0.11.7: 3 blocks wide, 20 long, flat 20-energy deploy + 1/sec -- explicit user request (was a
	 *  per-segment scaling cost for a variable-length bridge; length is now always the full 20). */
	public static final float BRIDGE_COST = 20f;
	public static final float BRIDGE_UPKEEP_PER_SEC = 1f;
	public static final int BRIDGE_LENGTH = 20;
	public static final int BRIDGE_WIDTH = 3;
	public static final int BRIDGE_MAX_DURATION_TICKS = 30 * 20;
	public static final int BRIDGE_SLOT_WEIGHT = 2;

	/** v0.11.7: 3 blocks wide, flat 20-energy deploy + 1/sec -- explicit user request. */
	public static final float RAMP_COST = 20f;
	public static final float RAMP_UPKEEP_PER_SEC = 1f;
	public static final int RAMP_WIDTH = 3;
	public static final int RAMP_MAX_SEGMENTS = 12;
	public static final int RAMP_MAX_DURATION_TICKS = 30 * 20;
	public static final int RAMP_SLOT_WEIGHT = 2;

	/** v0.11.7: same free-to-equip/pay-while-on model as Energy Blade -- explicit user request. */
	public static final float DRILL_START_COST = 0f;
	public static final float DRILL_UPKEEP_PER_SEC = 2f; // only drains while toggled on
	public static final double DRILL_REACH = 5.0;
	public static final int DRILL_SLOT_WEIGHT = 1;

	/** v0.11.7: 1 to deploy, 1 charge every 5 seconds (0.2/sec) to maintain -- explicit user request. */
	public static final float LANTERN_LIGHT_COST = 1f;
	public static final float LANTERN_LIGHT_UPKEEP_PER_SEC = 0.2f;
	public static final int LANTERN_LIGHT_MAX_DURATION_TICKS = 60 * 20;
	public static final int LANTERN_LIGHT_SLOT_WEIGHT = 1;

	public static final float BUBBLE_COST = 90f;
	public static final float BUBBLE_UPKEEP_PER_SEC = 7f;
	/** v0.11.7: big enough to cover a small room, up from 2.5 -- explicit user request. */
	public static final double BUBBLE_RADIUS = 8.0;
	public static final int BUBBLE_MAX_DURATION_TICKS = 30 * 20;
	public static final int BUBBLE_SLOT_WEIGHT = 2;

	// v0.11.5: Rescue Tether is now a grab (not a standing construct) -- see
	// GreenLanternConstructs#rescueGrab. No upkeep or slot weight.
	// v0.11.6: reworked from an instant yank into an actual hold -- explicit user request ("press c and
	// pick up the target, they can press c again to throw the target or shift+C to let them down
	// safely"). The cost/cooldown below now gate the grab itself; the throw and the safe-set-down are
	// both free follow-ups to an existing hold.
	// v0.11.7: cost 20 to cast + 1/sec to maintain the hold, 30-block grab range -- explicit user request.
	public static final float TETHER_COST = 20f;
	public static final float TETHER_UPKEEP_PER_SEC = 1f;
	public static final double TETHER_RANGE = 30.0;
	public static final int TETHER_COOLDOWN_TICKS = 80; // 4s
	/** Blocks in front of the caster's eyes the held target is glued to each tick. */
	public static final double TETHER_HOLD_DISTANCE = 2.5;
	/** Blocks/tick launch speed given to the held target on a throw (C again while holding). */
	public static final double TETHER_THROW_SPEED = 1.6;
	/** A held target further than this (blocks, squared) from the caster is released automatically. */
	public static final double TETHER_MAX_HOLD_RANGE_SQR = 400.0;

	public static final float CARRY_PLATFORM_COST = 20f;
	public static final float CARRY_PLATFORM_UPKEEP_PER_SEC = 1f;
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
