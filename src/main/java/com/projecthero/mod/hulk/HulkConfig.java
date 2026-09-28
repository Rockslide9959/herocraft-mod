package com.projecthero.mod.hulk;

/**
 * Every Hulk tunable (v0.13.11, Phase 1 -- the core rage / transform loop). Static finals like
 * {@code AllMightConfig} / {@code WolverineConfig}; Phase 2 moves the ability numbers into a JSON config.
 */
public final class HulkConfig {
	private HulkConfig() {
	}

	// ---------------- rage ----------------
	public static final float RAGE_MAX = 100.0f;
	/** H transforms by hand from this much rage; at {@link #RAGE_MAX} the change is forced. */
	public static final float MANUAL_TRANSFORM_RAGE = 75.0f;
	/** Rage per point of damage TAKEN as Banner (10 hearts of damage = +50). */
	public static final float RAGE_PER_DAMAGE_TAKEN = 2.5f;
	/** Rage per point of damage DEALT to a mob as Banner (fighting builds it too, slower than getting hurt). */
	public static final float RAGE_PER_DAMAGE_DEALT = 0.8f;
	/** Rage per point of damage TAKEN as the Hulk -- the angrier he gets, the longer he stays. */
	public static final float HULK_RAGE_PER_DAMAGE_TAKEN = 1.5f;
	/** Rage the Hulk burns every second. 100 rage = 100 s of Hulk without being hurt. */
	public static final float HULK_DRAIN_PER_SECOND = 1.0f;
	/** Banner calms down when left alone: rage bleeds off this fast once {@link #CALM_DELAY_TICKS} pass without a fight. */
	public static final float CALM_DECAY_PER_SECOND = 0.5f;
	public static final int CALM_DELAY_TICKS = 15 * 20;

	// ---------------- the change ----------------
	/** Ticks the body takes to grow (or shrink back); damage-proof while it happens. */
	public static final int GROWTH_TICKS = 30;
	/** Anti-spam gate on H. */
	public static final int TOGGLE_DEBOUNCE_TICKS = 10;
	/** Health healed on top of the carried-over health percentage when the Hulk comes out. */
	public static final float TRANSFORM_HEAL = 20.0f;
	/** After changing back: Weakness + Slowness for this long, and no rage can build. */
	public static final int EXHAUSTED_TICKS = 8 * 20;

	// ---------------- Hulk stats (fixed-id transient attribute modifiers) ----------------
	/** Scale bonus: 1 + 0.8 = 1.8x (a 3.24-block Hulk). */
	public static final double SCALE_BONUS = 0.8;
	public static final double ATTACK_BONUS = 12.0;
	public static final double HEALTH_BONUS = 40.0;
	public static final double KNOCKBACK_RESISTANCE = 0.9;
	public static final double ARMOR_TOUGHNESS_BONUS = 8.0;
	/** A bigger stride: steps straight up a full block. */
	public static final double STEP_HEIGHT_BONUS = 0.5;
	/** Longer arms for a bigger body. */
	public static final double REACH_BONUS = 1.5;
	/** Fast regeneration: {@link #REGEN_AMOUNT} HP every {@link #REGEN_INTERVAL_TICKS} (2 HP a second). */
	public static final int REGEN_INTERVAL_TICKS = 10;
	public static final float REGEN_AMOUNT = 1.0f;
}
