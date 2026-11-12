package com.projecthero.mod.raven;

/**
 * v0.17.3 Raven (Teen Titans): every number of the power in one place ({@code docs/TEEN_TITANS_DESIGN.md}, the Raven section).
 * Times are ticks, energy is Dark Energy points, damage is half-hearts.
 */
public final class RavenConfig {
	private RavenConfig() {
	}

	// ---------------------------------------------------------------- passives
	/** 20 health: no extra hearts. */
	public static final double HEALTH_BONUS = 0.0;
	public static final float DAMAGE_RESIST = 0.10f;

	// ---------------------------------------------------------------- Dark Energy (Slab)
	public static final float ENERGY_MAX = 100f;
	public static final float ENERGY_REGEN_PER_TICK = 5f / 20f;
	public static final int SYNC_INTERVAL = 5;

	// ---------------------------------------------------------------- Composure (Hairline): 100 = calm, 0 = Rage
	public static final float COMPOSURE_MAX = 100f;
	/** Below this her emotions leak (cracks, pushes) and bolts hit harder. */
	public static final float LEAK_BELOW = 30f;
	/** Below this: four red eyes, Trigon's Wrath unlocks. */
	public static final float RAGE_BELOW = 10f;
	/** Composure lost per point of damage taken. */
	public static final float COMPOSURE_PER_DAMAGE = 1.2f;
	/** Hostile mobs within this many blocks for {@link #HOSTILE_GRACE_TICKS} wear her down by {@link #HOSTILE_DRAIN_PER_TICK}. */
	public static final double HOSTILE_RADIUS = 12.0;
	public static final int HOSTILE_GRACE_TICKS = 200;
	public static final float HOSTILE_DRAIN_PER_TICK = 0.5f / 20f;
	/** Calm returns by itself this fast once nothing has hurt her for {@link #CALM_AFTER_TICKS} and nothing hostile is near. */
	public static final float CALM_REGEN_PER_TICK = 1.5f / 20f;
	public static final int CALM_AFTER_TICKS = 100;
	/** Leak: the chance per tick (below {@link #LEAK_BELOW}) that something nearby cracks or a mob is pushed. */
	public static final float LEAK_CHANCE_PER_TICK = 1f / 100f;
	public static final int LEAK_RADIUS = 8;
	public static final float LEAK_BOLT_BONUS = 1.25f;

	// ---------------------------------------------------------------- R: Dark Bolt
	public static final float BOLT_DAMAGE = 8f;
	public static final float BOLT_COST = 8f;
	public static final int BOLT_COOLDOWN = 6;
	public static final double BOLT_SPEED = 2.0;
	public static final double BOLT_RANGE = 40.0;
	/** It passes through this many creatures and stops in the next. */
	public static final int BOLT_PIERCE = 1;
	public static final float BOLT_COMPOSURE = 0.5f;

	// ---------------------------------------------------------------- Shift+R: Telekinesis (hold)
	public static final float TK_COST = 10f;
	public static final float TK_DRAIN_PER_TICK = 4f / 20f;
	public static final double TK_RANGE = 24.0;
	public static final double TK_MIN_HOLD = 3.0;
	public static final double TK_MAX_HOLD = 12.0;
	public static final double TK_THROW_SPEED = 1.7;
	public static final float TK_THROW_DAMAGE = 10f;
	/** The impact bonus on top: blocks a tick of speed x this, at most {@link #TK_IMPACT_MAX}. */
	public static final float TK_IMPACT_PER_SPEED = 4f;
	public static final float TK_IMPACT_MAX = 6f;
	public static final int TK_THROWN_TICKS = 40;
	public static final int TK_COOLDOWN = 20;
	public static final int TK_MAX_HOLD_TICKS = 30 * 20;
	public static final float TK_COMPOSURE = 3f;

	// ---------------------------------------------------------------- G: Shadow Grasp
	public static final float GRASP_DAMAGE = 6f;
	public static final float GRASP_COST = 15f;
	public static final int GRASP_HOLD_TICKS = 40;
	public static final int GRASP_COOLDOWN = 40;
	public static final double GRASP_RANGE = 24.0;
	public static final float GRASP_COMPOSURE = 2f;

	// ---------------------------------------------------------------- Shift+G: Soul-Self
	public static final float SOUL_DAMAGE = 16f;
	public static final float SOUL_COST = 35f;
	public static final int SOUL_COOLDOWN = 8 * 20;
	public static final double SOUL_RANGE = 25.0;
	public static final double SOUL_SPEED = 1.0;
	public static final double SOUL_RADIUS = 1.8;
	public static final int SOUL_EFFECT_TICKS = 60;
	public static final float SOUL_COMPOSURE = 6f;

	// ---------------------------------------------------------------- Z: Dark Shield (hold)
	public static final double SHIELD_RADIUS = 3.0;
	public static final float SHIELD_ABSORB = 40f;
	public static final float SHIELD_DRAIN_PER_TICK = 5f / 20f;
	/** After the dome breaks (40 absorbed) it can't be raised for this long. */
	public static final int SHIELD_BROKEN_COOLDOWN = 60;

	// ---------------------------------------------------------------- Shift+Z: Trigon's Wrath
	public static final int WRATH_TICKS = 15 * 20;
	public static final double WRATH_SCALE = 3.0;
	public static final double WRATH_RADIUS = 12.0;
	public static final float WRATH_DAMAGE = 30f;
	/** The tentacles grab and slam at these ticks into the Wrath. */
	public static final int[] WRATH_SLAMS = { 10, 110, 210 };
	public static final int WRATH_LIFT_TICKS = 12;
	public static final int WRATH_FEAR_TICKS = 120;
	public static final int WRATH_COOLDOWN = 3 * 60 * 20;

	// ---------------------------------------------------------------- X: Shadow Dash
	public static final double DASH_DISTANCE = 10.0;
	public static final int DASH_TICKS = 5;
	public static final float DASH_COST = 12f;
	public static final int DASH_COOLDOWN = 20;

	// ---------------------------------------------------------------- Shift+X: Shadow Step Strike
	public static final double STEP_RANGE = 20.0;
	public static final float STEP_DAMAGE = 12f;
	public static final float STEP_COST = 20f;
	public static final int STEP_COOLDOWN = 4 * 20;
	/** Ticks spent sunk in her shadow before she erupts. */
	public static final int STEP_SINK_TICKS = 8;
	public static final double STEP_LAUNCH = 1.15;
	public static final float STEP_COMPOSURE = 3f;

	// ---------------------------------------------------------------- C: Telekinetic Wave
	public static final double WAVE_RADIUS = 8.0;
	public static final double WAVE_LIFT = 4.0;
	public static final int WAVE_RISE_TICKS = 10;
	public static final int WAVE_HOLD_TICKS = 20;
	public static final float WAVE_DAMAGE = 12f;
	public static final float WAVE_COST = 30f;
	public static final int WAVE_COOLDOWN = 60;
	public static final float WAVE_COMPOSURE = 5f;

	// ---------------------------------------------------------------- Shift+C: Debris Storm
	public static final int DEBRIS_COUNT = 8;
	public static final int DEBRIS_SEARCH = 6;
	public static final float DEBRIS_COST = 30f;
	public static final float DEBRIS_DAMAGE = 8f;
	public static final double DEBRIS_SPEED = 1.9;
	public static final double DEBRIS_ORBIT_RADIUS = 2.4;
	public static final int DEBRIS_ORBIT_TICKS = 20 * 20;
	/** Real blocks go back where they came from this long after they were torn up. */
	public static final int RESTORE_TICKS = 10 * 20;
	public static final int DEBRIS_COOLDOWN = 100;
	public static final float DEBRIS_COMPOSURE = 5f;

	// ---------------------------------------------------------------- V: Empath Heal
	public static final double HEAL_RANGE = 16.0;
	public static final float HEAL_TAKE_MAX = 10f;
	public static final float HEAL_SELF = 4f;
	public static final float HEAL_COST = 20f;
	public static final int HEAL_COOLDOWN = 10 * 20;

	// ---------------------------------------------------------------- Shift+V: Meditate (hold)
	public static final float MEDITATE_COMPOSURE_PER_TICK = 12f / 20f;
	public static final float MEDITATE_ENERGY_PER_TICK = 20f / 20f;
	public static final double MEDITATE_FLOAT = 0.6;

	// ---------------------------------------------------------------- flight (double-tap Space)
	public static final double FLIGHT_CRUISE_BPS = 9.0;
	public static final double FLIGHT_SPRINT_BPS = 16.0;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 6;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---------------------------------------------------------------- the look
	/** The white eyes show for this long after a move. */
	public static final int CAST_EYES_TICKS = 30;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
}
