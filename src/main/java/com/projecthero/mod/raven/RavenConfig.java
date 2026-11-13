package com.projecthero.mod.raven;

/**
 * v0.17.3 Raven (Teen Titans): every number of the power in one place ({@code docs/RAVEN_REVAMP_DESIGN.md}, the revamp the user
 * approved on 2026-11-13). Times are ticks, energy is Dark Energy points, damage is half-hearts.
 */
public final class RavenConfig {
	private RavenConfig() {
	}

	// ---------------------------------------------------------------- passives
	/** 30 health: ten extra. */
	public static final double HEALTH_BONUS = 10.0;
	public static final float DAMAGE_RESIST = 0.10f;
	/** Demonic Heritage: magic and supernatural hits are this much lighter (on top of the 10%). */
	public static final float HERITAGE_RESIST = 0.30f;
	/** Dark Empathy: every kill gives back one heart and this much Dark Energy. */
	public static final float EMPATHY_HEAL = 2f;
	public static final float EMPATHY_ENERGY = 5f;
	/** Mystical Awareness: every {@link #AWARE_PERIOD} ticks, hostiles within {@link #AWARE_RADIUS} glow for {@link #AWARE_TICKS}. */
	public static final int AWARE_PERIOD = 8 * 20;
	public static final int AWARE_TICKS = 2 * 20;
	public static final double AWARE_RADIUS = 24.0;

	// ---------------------------------------------------------------- Dark Energy (Slab)
	public static final float ENERGY_MAX = 100f;
	public static final float ENERGY_REGEN_PER_TICK = 5f / 20f;
	public static final int SYNC_INTERVAL = 5;

	// ---------------------------------------------------------------- Composure (Hairline): 100 = calm, 0 = Rage
	public static final float COMPOSURE_MAX = 100f;
	/** Below this she is Unsettled: spells +20% but wild (bolts drift, 1 in 8 wheel spells misfire), and her emotions leak. */
	public static final float UNSETTLED_BELOW = 40f;
	/** Below this: Rage -- four red eyes, spells +40%, shields absorb half, Trigon's Wrath unlocks. */
	public static final float RAGE_BELOW = 10f;
	public static final float UNSETTLED_MULT = 1.20f;
	public static final float RAGE_MULT = 1.40f;
	public static final float RAGE_SHIELD_MULT = 0.5f;
	/** One wheel spell in this many misfires while Unsettled. */
	public static final int MISFIRE_ONE_IN = 8;
	/** A misfire lands this far (at most) from where it was meant to. */
	public static final double MISFIRE_SPREAD = 6.0;
	public static final float MISFIRE_DAMAGE = 4f;
	/** An Unsettled bolt drifts up to this many degrees off her aim. */
	public static final float DRIFT_DEGREES = 7f;
	/** Composure lost per point of damage taken. */
	public static final float COMPOSURE_PER_DAMAGE = 1.2f;
	public static final double HOSTILE_RADIUS = 12.0;
	public static final int HOSTILE_GRACE_TICKS = 200;
	public static final float HOSTILE_DRAIN_PER_TICK = 0.5f / 20f;
	public static final float CALM_REGEN_PER_TICK = 1.5f / 20f;
	public static final int CALM_AFTER_TICKS = 100;
	/** The leak (Unsettled): the chance per tick that something nearby cracks or a mob is pushed. */
	public static final float LEAK_CHANCE_PER_TICK = 1f / 100f;
	public static final int LEAK_RADIUS = 8;
	/** Every cast costs a little calm; big moves cost more (each move's own number). */
	public static final float CAST_COMPOSURE = 1f;

	// ---------------------------------------------------------------- R: Dark Bolt
	public static final float BOLT_DAMAGE = 8f;
	public static final float BOLT_BURST_DAMAGE = 4f;
	public static final double BOLT_BURST_RADIUS = 2.0;
	public static final float BOLT_COST = 8f;
	public static final int BOLT_COOLDOWN = 6;
	public static final double BOLT_SPEED = 2.0;
	public static final double BOLT_RANGE = 40.0;
	public static final float BOLT_COMPOSURE = 0.5f;

	// ---------------------------------------------------------------- Shift+R: Telekinesis (hold)
	public static final float TK_COST = 10f;
	public static final float TK_DRAIN_PER_TICK = 4f / 20f;
	public static final double TK_RANGE = 24.0;
	public static final double TK_MIN_HOLD = 3.0;
	public static final double TK_MAX_HOLD = 12.0;
	public static final double TK_THROW_SPEED = 1.7;
	public static final float TK_THROW_DAMAGE = 10f;
	public static final float TK_IMPACT_PER_SPEED = 4f;
	public static final float TK_IMPACT_MAX = 6f;
	public static final int TK_THROWN_TICKS = 40;
	public static final int TK_COOLDOWN = 20;
	public static final int TK_MAX_HOLD_TICKS = 30 * 20;
	public static final float TK_COMPOSURE = 3f;

	// ---------------------------------------------------------------- G: Soul Shield
	public static final int GUARD_TICKS = 5 * 20;
	public static final float GUARD_ABSORB = 30f;
	public static final float GUARD_ABSORB_ASCENDED = 50f;
	/** Soaking this much makes it burst when it ends. */
	public static final float GUARD_BURST_AT = 20f;
	public static final float GUARD_BURST_DAMAGE = 8f;
	public static final double GUARD_BURST_RADIUS = 4.0;
	public static final double GUARD_RADIUS = 1.4;
	public static final double GUARD_KNOCKBACK = 1.1;
	public static final float GUARD_COST = 25f;
	public static final int GUARD_COOLDOWN = 16 * 20;

	// ---------------------------------------------------------------- Shift+G: Soul-Self (steered)
	public static final float SOUL_DAMAGE = 14f;
	public static final float SOUL_COST = 35f;
	public static final int SOUL_COOLDOWN = 18 * 20;
	/** She steers it with her look for this long, then it flies home. */
	public static final int SOUL_STEER_TICKS = 5 * 20;
	public static final double SOUL_SPEED = 0.8;
	public static final double SOUL_RETURN_SPEED = 1.4;
	public static final double SOUL_RADIUS = 1.8;
	/** It can't go further than this from her. */
	public static final double SOUL_LEASH = 40.0;
	public static final int SOUL_EFFECT_TICKS = 60;
	public static final float SOUL_COMPOSURE = 6f;
	/** Demonic Ascension: bigger and stronger. */
	public static final float SOUL_ASCENDED_MULT = 1.5f;

	// ---------------------------------------------------------------- Z: Telekinetic Crush
	public static final int CRUSH_MAX = 6;
	public static final double CRUSH_RANGE = 10.0;
	/** The cone in front of her: the dot of her look and the way to the creature. */
	public static final double CRUSH_CONE = 0.5;
	public static final double CRUSH_LIFT = 3.0;
	public static final int CRUSH_RISE_TICKS = 8;
	public static final int CRUSH_HOLD_TICKS = 32;
	public static final float CRUSH_DAMAGE = 14f;
	public static final int CRUSH_STUN_TICKS = 20;
	public static final float CRUSH_COST = 30f;
	public static final int CRUSH_COOLDOWN = 9 * 20;
	public static final float CRUSH_COMPOSURE = 4f;

	// ---------------------------------------------------------------- Shift+Z: Trigon's Wrath
	public static final int WRATH_TICKS = 15 * 20;
	public static final double WRATH_SCALE = 3.0;
	public static final double WRATH_RADIUS = 12.0;
	public static final float WRATH_DAMAGE = 30f;
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
	public static final int STEP_SINK_TICKS = 8;
	public static final double STEP_LAUNCH = 1.15;
	public static final float STEP_COMPOSURE = 3f;

	// ---------------------------------------------------------------- C: Dark Energy Wave (charged)
	public static final int WAVE_CHARGE_TICKS = 12;
	public static final double WAVE_RANGE = 12.0;
	/** The front moves this many blocks a tick. */
	public static final double WAVE_SPEED = 2.0;
	/** Half-width of the wave at its far end (it widens from 1.5 at her). */
	public static final double WAVE_HALF_WIDTH = 5.0;
	public static final float WAVE_DAMAGE = 12f;
	public static final double WAVE_KNOCKBACK = 1.2;
	public static final int WAVE_SLOW_TICKS = 60;
	public static final float WAVE_COST = 30f;
	public static final int WAVE_COOLDOWN = 11 * 20;
	public static final float WAVE_COMPOSURE = 5f;

	// ---------------------------------------------------------------- Shift+C: Demonic Ascension
	public static final int ASCEND_TICKS = 15 * 20;
	public static final float ASCEND_POWER = 1.35f;
	public static final float ASCEND_COST_MULT = 0.75f;
	public static final float ASCEND_REGEN_PER_TICK = 10f / 20f;
	public static final double ASCEND_SPEED = 0.20;
	public static final float ASCEND_COST = 40f;
	public static final int ASCEND_COOLDOWN = 60 * 20;
	public static final float ASCEND_COMPOSURE = 20f;

	// ---------------------------------------------------------------- V tap / hold: the Spell Wheel
	/** V held this long opens the wheel; a shorter press casts the selected spell. */
	public static final int WHEEL_HOLD_TICKS = 5;

	// spell: Void Vortex
	public static final double VORTEX_RANGE = 24.0;
	public static final int VORTEX_TICKS = 4 * 20;
	public static final double VORTEX_PULL_RADIUS = 7.0;
	public static final double VORTEX_PULL = 0.16;
	public static final float VORTEX_TICK_DAMAGE = 3f;
	public static final float VORTEX_BLAST_DAMAGE = 10f;
	public static final double VORTEX_BLAST_RADIUS = 4.0;
	public static final float VORTEX_COST = 30f;
	public static final int VORTEX_COOLDOWN = 15 * 20;

	// spell: Shadow Binding
	public static final double BIND_RANGE = 24.0;
	public static final double BIND_RADIUS = 4.0;
	public static final float BIND_DAMAGE = 6f;
	public static final int BIND_TICKS = 3 * 20;
	public static final float BIND_COST = 20f;
	public static final int BIND_COOLDOWN = 12 * 20;

	// spell: Soul Detonation
	public static final double DETONATE_RADIUS = 6.0;
	public static final int DETONATE_DELAY = 30;
	public static final float DETONATE_MAX = 24f;
	/** At the edge of the radius a mark is worth this fraction of the most. */
	public static final float DETONATE_EDGE = 0.5f;
	public static final float DETONATE_CANCEL_AT = 8f;
	public static final float DETONATE_COST = 40f;
	public static final int DETONATE_COOLDOWN = 18 * 20;

	// spell: Soul Devour (a beam)
	public static final double DEVOUR_RANGE = 16.0;
	public static final int DEVOUR_TICKS = 3 * 20;
	public static final int DEVOUR_PULSE = 10;
	public static final float DEVOUR_DAMAGE = 4f;
	public static final float DEVOUR_HEAL_SHARE = 0.5f;
	public static final float DEVOUR_COST = 35f;
	public static final int DEVOUR_COOLDOWN = 20 * 20;

	// spell: Debris Storm
	public static final int DEBRIS_COUNT = 8;
	public static final int DEBRIS_SEARCH = 6;
	public static final float DEBRIS_COST = 30f;
	public static final float DEBRIS_DAMAGE = 8f;
	public static final double DEBRIS_SPEED = 1.9;
	public static final double DEBRIS_ORBIT_RADIUS = 2.4;
	public static final int DEBRIS_ORBIT_TICKS = 20 * 20;
	public static final int RESTORE_TICKS = 10 * 20;
	public static final int DEBRIS_COOLDOWN = 20 * 20;
	public static final float DEBRIS_COMPOSURE = 5f;

	// spell: Dimensional Rift
	public static final double RIFT_RANGE = 20.0;
	public static final int RIFT_STEP_TICKS = 8;
	public static final float RIFT_COST = 25f;
	public static final int RIFT_COOLDOWN = 12 * 20;

	// spell: Soul Restoration
	public static final double HEAL_RANGE = 16.0;
	public static final float HEAL_TAKE_MAX = 10f;
	public static final int HEAL_CHANNEL_TICKS = 3 * 20;
	public static final float HEAL_SELF = 12f;
	public static final float HEAL_CALM = 30f;
	public static final float HEAL_COST = 45f;
	public static final int HEAL_COOLDOWN = 25 * 20;

	// spell: Telekinetic Pull
	public static final double PULL_RANGE = 20.0;
	/** A creature this light (max health) or lighter can be yanked. */
	public static final float PULL_LIGHT_HEALTH = 40f;
	public static final float PULL_COST = 15f;
	public static final int PULL_COOLDOWN = 8 * 20;

	// spell: Astral Sanctuary
	public static final double SANCTUARY_RADIUS = 5.0;
	public static final int SANCTUARY_TICKS = 8 * 20;
	public static final float SANCTUARY_REDUCTION = 0.40f;
	public static final float SANCTUARY_COST = 60f;
	public static final int SANCTUARY_COOLDOWN = 35 * 20;

	// spell: Shadow Veil
	public static final int VEIL_TICKS = 6 * 20;
	public static final float VEIL_COST = 20f;
	public static final int VEIL_COOLDOWN = 20 * 20;

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
	public static final int CAST_EYES_TICKS = 30;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
}
