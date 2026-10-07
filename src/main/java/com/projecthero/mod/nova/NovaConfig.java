package com.projecthero.mod.nova;

/**
 * v0.15.13: every Nova (Richard Rider) number in one place (static finals, like {@code KryptonianConfig}). Documented
 * for players in {@code docs/NOVA_REFERENCE.md}; keep the two in step.
 *
 * <p>Times are in ticks (20 a second), distances in blocks, damage in half-hearts (vanilla health points).
 */
public final class NovaConfig {
	private NovaConfig() {
	}

	// ---------------------------------------------------------------- the suit (H)

	/** How long the golden energy takes to wrap the body (suit-up). */
	public static final int SUIT_UP_TICKS = 24;
	/** How long the suit takes to unravel back into light (suit-down). */
	public static final int SUIT_DOWN_TICKS = 14;
	/** H debounce. */
	public static final int SUIT_TOGGLE_COOLDOWN = 10;

	// ---------------------------------------------------------------- the Nova Force bar

	public static final float FORCE_MAX = 100f;
	/** 4% of the bar a second, all the time (suited or not, flying or not). */
	public static final float FORCE_REGEN_PER_SECOND = 4f;
	/** Flying faster than {@link #FAST_FLIGHT_SPEED} drains this much a second (on top of the refill: net +2/s). */
	public static final float FAST_FLIGHT_DRAIN_PER_SECOND = 2f;
	/** Server-measured speed (blocks/tick) above which flight counts as "fast" (25 b/s, i.e. sprint flight). */
	public static final double FAST_FLIGHT_SPEED = 1.25;

	// ---------------------------------------------------------------- passives (while suited)

	/** 60% of every hit is shrugged off. */
	public static final float DAMAGE_REDUCTION = 0.60f;
	/** The Worldmind outlines every mob within this sphere, for the Nova alone. */
	public static final double WORLDMIND_RANGE = 32.0;

	// ---------------------------------------------------------------- flight (double-tap jump while suited)

	/** 20 blocks a second. */
	public static final double FLIGHT_SPEED = 1.0;
	/** 40 blocks a second sprinting. */
	public static final double FLIGHT_SPRINT_SPEED = 2.0;
	public static final double FLIGHT_VERTICAL_SPEED = 0.6;
	public static final double FLIGHT_ACCELERATION = 0.22;
	public static final double FLIGHT_BRAKE = 0.35;
	public static final double FLIGHT_IDLE = 0.25;
	/** Ticks after take-off before touching the ground lands him. */
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---------------------------------------------------------------- combat shared

	/** Anything with at least this much max health is a boss: capped damage per hit, never grabbed / lifted / pulled. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ---------------------------------------------------------------- R: Nova Blast (held beam)

	public static final float BLAST_DAMAGE_PER_SECOND = 8f;
	/** One hit every 10 ticks (so 4 a hit). */
	public static final int BLAST_HIT_INTERVAL = 10;
	public static final float BLAST_COST_PER_SECOND = 6f;
	public static final double BLAST_RANGE = 32.0;
	public static final int BLAST_MAX_TICKS = 160;
	public static final int BLAST_COOLDOWN = 30;

	// ---------------------------------------------------------------- Shift+R: Nova Bolt Volley

	public static final int VOLLEY_BOLTS = 5;
	public static final float VOLLEY_DAMAGE = 6f;
	public static final float VOLLEY_COST = 20f;
	public static final int VOLLEY_COOLDOWN = 120;
	/** Bolt speed (blocks/tick), how far they look for prey, and how long they live. */
	public static final double VOLLEY_SPEED = 1.2;
	public static final double VOLLEY_SEEK_RANGE = 24.0;
	public static final int VOLLEY_LIFETIME = 60;
	/** Fraction of the way the bolt turns toward its prey each tick. */
	public static final double VOLLEY_TURN = 0.45;

	// ---------------------------------------------------------------- G: Gravimetric Pulse

	public static final double PULSE_RADIUS = 6.0;
	public static final float PULSE_DAMAGE = 10f;
	public static final double PULSE_LIFT = 0.9;
	public static final double PULSE_KNOCKBACK = 0.6;
	public static final float PULSE_COST = 20f;
	public static final int PULSE_COOLDOWN = 160;

	// ---------------------------------------------------------------- Shift+G: Gravity Slam

	public static final double SLAM_RADIUS = 8.0;
	/** Damage = MIN + PER_BLOCK x drop height, capped at MAX. */
	public static final float SLAM_MIN_DAMAGE = 6f;
	public static final float SLAM_DAMAGE_PER_BLOCK = 0.6f;
	public static final float SLAM_MAX_DAMAGE = 18f;
	/** Must start at least this high above the ground. */
	public static final double SLAM_MIN_HEIGHT = 2.0;
	public static final double SLAM_DIVE_SPEED = 3.0;
	public static final int SLAM_MAX_TICKS = 100;
	public static final float SLAM_COST = 25f;
	public static final int SLAM_COOLDOWN = 240;

	// ---------------------------------------------------------------- Z: Force Shield

	public static final int SHIELD_TICKS = 80;
	public static final double SHIELD_RADIUS = 1.8;
	public static final float SHIELD_COST = 25f;
	public static final int SHIELD_COOLDOWN = 300;

	// ---------------------------------------------------------------- Shift+Z: NOVA OVERLOAD (ultimate)

	/** Needs a completely full bar (it spends all of it). */
	public static final float OVERLOAD_MIN_FORCE = FORCE_MAX;
	public static final int OVERLOAD_TICKS = 200;
	/** Every ability deals +50% while it runs (and costs nothing). */
	public static final float OVERLOAD_DAMAGE_MULTIPLIER = 1.5f;
	/** Cooldowns started during the Overload are halved. */
	public static final float OVERLOAD_COOLDOWN_MULTIPLIER = 0.5f;
	public static final float OVERLOAD_BURST_DAMAGE = 30f;
	public static final double OVERLOAD_BURST_RADIUS = 10.0;
	public static final int OVERLOAD_COOLDOWN = 1800;

	// ---------------------------------------------------------------- X: Comet Dash

	public static final double DASH_DISTANCE = 20.0;
	public static final double DASH_SPEED = 2.5;
	public static final int DASH_MAX_TICKS = 14;
	public static final double DASH_HIT_RADIUS = 1.5;
	public static final float DASH_DAMAGE = 14f;
	public static final double DASH_KNOCKBACK = 1.2;
	public static final float DASH_COST = 15f;
	public static final int DASH_COOLDOWN = 120;

	// ---------------------------------------------------------------- Shift+X: Orbital Launch

	public static final double LAUNCH_GRAB_RANGE = 8.0;
	public static final double LAUNCH_HEIGHT = 30.0;
	/** Climb speed (blocks/tick) while carrying the victim up. */
	public static final double LAUNCH_CLIMB_SPEED = 1.5;
	public static final double LAUNCH_SPIKE_SPEED = 3.0;
	/** Dealt on impact; the fall itself then hurts as well. */
	public static final float LAUNCH_SPIKE_DAMAGE = 20f;
	public static final double LAUNCH_IMPACT_RADIUS = 3.0;
	public static final float LAUNCH_COST = 30f;
	public static final int LAUNCH_COOLDOWN = 400;

	// ---------------------------------------------------------------- C: Gravity Well

	public static final double WELL_RANGE = 24.0;
	public static final int WELL_TICKS = 60;
	public static final double WELL_PULL_RADIUS = 8.0;
	public static final double WELL_PULL_STRENGTH = 0.28;
	public static final double WELL_CRUSH_RADIUS = 4.0;
	public static final float WELL_CRUSH_DAMAGE = 20f;
	public static final float WELL_COST = 30f;
	public static final int WELL_COOLDOWN = 280;

	// ---------------------------------------------------------------- Shift+C: Gravity Lock

	public static final double LOCK_RADIUS = 10.0;
	public static final double LOCK_LIFT = 3.0;
	public static final int LOCK_TICKS = 80;
	/** How long the lift up to the freeze takes. */
	public static final int LOCK_RISE_TICKS = 10;
	public static final float LOCK_COST = 35f;
	public static final int LOCK_COOLDOWN = 400;

	// ---------------------------------------------------------------- V: Worldmind Scan

	public static final double SCAN_RANGE = 48.0;
	public static final int SCAN_TICKS = 200;
	/** The marked (strongest) creature takes +25% damage from every source while the mark lasts. */
	public static final float SCAN_MARK_MULTIPLIER = 1.25f;
	public static final float SCAN_COST = 15f;
	public static final int SCAN_COOLDOWN = 400;

	// ---------------------------------------------------------------- Shift+V: Nova Force Transfer

	public static final double TRANSFER_RADIUS = 8.0;
	/** 6 hearts. */
	public static final float TRANSFER_HEAL = 12f;
	/** 40% of the bar. */
	public static final float TRANSFER_COST = 40f;
	public static final int TRANSFER_COOLDOWN = 400;

	// ---------------------------------------------------------------- the Centurion

	/** Players within this range hear the dying Centurion. */
	public static final double CENTURION_TALK_RANGE = 8.0;
	/** Per-player gap between his lines. */
	public static final int CENTURION_TALK_GAP = 200;
	/** How long the hand-off fade takes before he is gone. */
	public static final int CENTURION_FADE_TICKS = 40;
}
