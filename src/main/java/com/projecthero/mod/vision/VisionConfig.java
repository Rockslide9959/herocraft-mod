package com.projecthero.mod.vision;

/**
 * v0.17.3 Vision: every number of the power in one place ({@code docs/VISION_DESIGN.md}). Ticks unless said otherwise; energy is
 * the 0..100 Mind Stone bar.
 */
public final class VisionConfig {
	private VisionConfig() {
	}

	public static final int S = 20;

	// ---------------------------------------------------------------- passives (the synthezoid body)
	/** 20 + 25 = 45 health. */
	public static final double HEALTH_BONUS = 25.0;
	/** Off every hit. */
	public static final float DAMAGE_RESIST = 0.30f;
	public static final double MELEE_BONUS = 6.0;
	/** No hunger: the food bar is held here with no saturation (a slow self-repair, never starving). */
	public static final int FOOD_LEVEL = 18;

	// ---------------------------------------------------------------- the Mind Stone bar (Meter)
	public static final float ENERGY_MAX = 100f;
	/** Regeneration a second: direct sunlight (the solar jewel), daylight in shade or night, the dark. */
	public static final float REGEN_SUN = 4f;
	public static final float REGEN_SHADE = 2.5f;
	public static final float REGEN_DARK = 1.5f;
	/** The bar ticks in place and is re-sent this often. */
	public static final int SYNC_INTERVAL = 5;

	// ---------------------------------------------------------------- flight (double-tap Space, free)
	public static final double FLIGHT_CRUISE_BPS = 20.0;
	public static final double FLIGHT_SPRINT_BPS = 36.0;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 10;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---------------------------------------------------------------- R (hold): Mind Stone Beam
	public static final float BEAM_START_COST = 6f;
	public static final float BEAM_COST_PER_SECOND = 12f;
	public static final double BEAM_RANGE = 32.0;
	public static final float BEAM_DAMAGE = 7f;
	public static final int BEAM_HIT_INTERVAL = 5;
	public static final int BEAM_MAX_TICKS = 8 * S;
	public static final double BEAM_PUSH = 0.18;
	public static final int BEAM_COOLDOWN = 30;

	// ---------------------------------------------------------------- Shift+R: Solar Lance
	public static final float LANCE_COST = 30f;
	public static final int LANCE_CHARGE_TICKS = 12;
	public static final float LANCE_DAMAGE = 20f;
	public static final double LANCE_RANGE = 48.0;
	public static final double LANCE_WIDTH = 0.9;
	public static final int LANCE_COOLDOWN = 6 * S;

	// ---------------------------------------------------------------- G: Density Punch
	public static final float PUNCH_COST = 10f;
	public static final float PUNCH_DAMAGE = 18f;
	public static final double PUNCH_REACH = 5.0;
	public static final double PUNCH_LAUNCH = 1.8;
	public static final double PUNCH_LIFT = 0.4;
	public static final float PUNCH_WALL_DAMAGE = 6f;
	public static final int PUNCH_WALL_TICKS = 30;
	public static final int PUNCH_COOLDOWN = 30;

	// ---------------------------------------------------------------- Shift+G: Phase Strike
	public static final float STRIKE_COST = 15f;
	/** Ignores armour. */
	public static final float STRIKE_DAMAGE = 14f;
	public static final double STRIKE_REACH = 5.0;
	public static final int STRIKE_STUN_TICKS = 2 * S;
	public static final int STRIKE_COOLDOWN = 4 * S;

	// ---------------------------------------------------------------- Z: Mind Stone Pulse
	public static final float PULSE_COST = 25f;
	public static final float PULSE_DAMAGE = 12f;
	public static final double PULSE_RADIUS = 7.0;
	public static final double PULSE_KNOCKBACK = 1.5;
	public static final int PULSE_GLOW_TICKS = 5 * S;
	public static final int PULSE_COOLDOWN = 8 * S;

	// ---------------------------------------------------------------- Shift+Z: Mind Stone Unleashed
	public static final float UNLEASH_COST = 80f;
	public static final int UNLEASH_TICKS = 3 * S;
	public static final float UNLEASH_DAMAGE = 7f;
	public static final int UNLEASH_HIT_INTERVAL = 4;
	public static final double UNLEASH_RANGE = 48.0;
	/** The wide beam's reach either side of its line. */
	public static final double UNLEASH_RADIUS = 1.6;
	public static final int UNLEASH_COOLDOWN = 2 * 60 * S;

	// ---------------------------------------------------------------- X: Phase Dash
	public static final float DASH_COST = 8f;
	public static final double DASH_DISTANCE = 10.0;
	public static final int DASH_TICKS = 5;
	/** Intangible this long (a little past the dash). */
	public static final int DASH_IFRAMES = 8;
	public static final int DASH_COOLDOWN = S;

	// ---------------------------------------------------------------- Shift+X: Density Dive
	public static final float DIVE_COST = 25f;
	public static final double DIVE_RANGE = 24.0;
	/** Blocks a tick rushing at the target. */
	public static final double DIVE_SPEED = 2.6;
	public static final int DIVE_MAX_TICKS = 14;
	public static final float DIVE_DAMAGE = 22f;
	public static final double DIVE_KNOCKBACK = 1.8;
	public static final int DIVE_STUN_TICKS = S;
	/** Where he ends: this far past the target along his path. */
	public static final double DIVE_BEHIND = 2.5;
	/** The meteor drop: needs this much air below him. */
	public static final double METEOR_MIN_HEIGHT = 4.0;
	public static final double METEOR_SPEED = 2.4;
	public static final int METEOR_MAX_TICKS = 60;
	public static final float METEOR_DAMAGE = 14f;
	public static final float METEOR_PER_BLOCK = 0.6f;
	public static final float METEOR_BONUS_MAX = 16f;
	public static final double METEOR_RADIUS = 4.5;
	/** No target and no air: a short forward rush that hits the first creature. */
	public static final int RUSH_TICKS = 6;
	public static final double RUSH_SPEED = 1.6;
	public static final int DIVE_COOLDOWN = 5 * S;

	// ---------------------------------------------------------------- C: Synthezoid Grip
	public static final float GRIP_COST = 12f;
	public static final double GRIP_REACH = 6.0;
	public static final float GRIP_MAX_HEALTH = 120f;
	public static final int GRIP_MAX_TICKS = 4 * S;
	public static final double GRIP_THROW_SPEED = 2.4;
	public static final float GRIP_IMPACT_DAMAGE = 10f;
	/** A hurled creature hurts itself on what it hits within this long. */
	public static final int GRIP_IMPACT_TICKS = 2 * S;
	public static final int GRIP_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- Shift+C: Diamond Density (toggle)
	public static final float DENSITY_DRAIN_PER_SECOND = 3f;
	/** Off every hit, on top of the passive (0.7 x 0.5 = 65% off in all). */
	public static final float DENSITY_RESIST = 0.5f;
	public static final double DENSITY_MELEE = 6.0;
	public static final double DENSITY_SLOW = -0.35;
	public static final int DENSITY_TOGGLE_COOLDOWN = S;

	// ---------------------------------------------------------------- V: Human Hologram (toggle)
	/** The hologram builds over him top-down / drains feet-first over this long. */
	public static final int HOLOGRAM_SHIMMER_TICKS = 16;
	public static final int HOLOGRAM_TOGGLE_COOLDOWN = 30;

	// ---------------------------------------------------------------- Shift+V: Intangibility (toggle)
	public static final float PHASE_START_COST = 10f;
	public static final float PHASE_DRAIN_PER_SECOND = 8f;
	public static final int PHASE_TOGGLE_COOLDOWN = S;
	/** How far the push-out looks for open space when the bar empties inside a wall. */
	public static final int PHASE_PUSH_RADIUS = 6;
}
