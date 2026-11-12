package com.projecthero.mod.starfire;

/**
 * v0.17.3 Starfire (Koriand'r of Tamaran): every number of the power in one place ({@code docs/TEEN_TITANS_DESIGN.md}, the
 * Starfire section). Times are ticks, energy is Starbolt (0..100), the mood is one needle from -100 (full Joy) to +100 (full Fury).
 */
public final class StarfireConfig {
	private StarfireConfig() {
	}

	public static final int S = 20;

	// ---------------------------------------------------------------- passives
	/** 20 + 10 = 30 health. */
	public static final double HEALTH_BONUS = 10.0;
	/** Tamaranean strength: +6 melee. */
	public static final double MELEE_BONUS = 6.0;
	/** Her punches knock back this much harder (on top of vanilla's). */
	public static final double PUNCH_KNOCKBACK = 0.9;
	/** 20% off every hit. */
	public static final float DAMAGE_RESIST = 0.20f;

	// ---------------------------------------------------------------- the Starbolt bar (solar charge)
	public static final float STARBOLT_MAX = 100f;
	/** Refill per second in direct sunlight: 3x the shade rate. */
	public static final float REGEN_SUN = 9f;
	/** In daylight but in shade or rain. */
	public static final float REGEN_SHADE = 3f;
	/** Under the open sky at night. */
	public static final float REGEN_NIGHT = 1.5f;
	/** Underground, indoors in the dark, the Nether / End. */
	public static final float REGEN_DARK = 1f;
	/** Ticks between bar re-syncs. */
	public static final int SYNC_INTERVAL = 5;

	// ---------------------------------------------------------------- the Joy / Fury gauge
	public static final float MOOD_MAX = 100f;
	/** "Full Fury" (the eyes blaze, Supernova unlocks) from here: a little slack, so the sun's Joy doesn't knock her off it at once. */
	public static final float FURY_FULL = 95f;
	/** Joy per second while flying / in direct sun / with a squadmate in 16 blocks. */
	public static final float JOY_FLYING = 3f;
	public static final float JOY_SUN = 2f;
	public static final float JOY_SQUAD = 2f;
	public static final double SQUAD_RANGE = 16.0;
	/** Joy per food point eaten. */
	public static final float JOY_PER_FOOD = 4f;
	/** Fury per point of damage she takes / per point a squadmate in 16 blocks takes. */
	public static final float FURY_PER_DAMAGE = 4f;
	public static final float FURY_PER_SQUAD_DAMAGE = 2f;
	/** The needle drifts back toward the middle this much a second. */
	public static final float MOOD_SETTLE = 1f;
	/** Glorious Radiance: Joy builds twice as fast. */
	public static final float RADIANCE_JOY_MULT = 2f;
	/** Full Fury: +60% starbolt damage. */
	public static final float FURY_DAMAGE_BONUS = 0.6f;

	// ---------------------------------------------------------------- flight (double-tap Space)
	public static final double FLIGHT_CRUISE_BPS = 16.0;
	public static final double FLIGHT_SPRINT_BPS = 32.0;
	/** Full Joy: 40% faster. Full Fury: 30% slower (her flight comes from joy). */
	public static final double FLIGHT_JOY_SPEED = 0.4;
	public static final double FLIGHT_FURY_SLOW = 0.3;
	/** Starbolt a second while flying; full Joy takes 60% off, full Fury adds 60%. */
	public static final float FLIGHT_COST_PER_SECOND = 1f;
	public static final float FLIGHT_JOY_DISCOUNT = 0.6f;
	public static final float FLIGHT_FURY_SURCHARGE = 0.6f;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 6;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---------------------------------------------------------------- R / Shift+R
	public static final float BOLT_DAMAGE = 7f;
	public static final float BOLT_BLAST_RADIUS = 1.5f;
	/** The small blast hurts everyone else in it for half. */
	public static final float BOLT_SPLASH = 0.5f;
	public static final float BOLT_COST = 5f;
	public static final int BOLT_COOLDOWN = 4;
	public static final double BOLT_SPEED = 2.4;
	public static final int BOLT_LIFE = 40;

	public static final float BURST_MIN_DAMAGE = 10f;
	public static final float BURST_MAX_DAMAGE = 28f;
	public static final float BURST_MIN_RADIUS = 2f;
	public static final float BURST_RADIUS = 4f;
	public static final int BURST_CHARGE_TICKS = 2 * S;
	/** A hold this long fires on its own. */
	public static final int BURST_MAX_HOLD = 6 * S;
	public static final float BURST_COST = 25f;
	public static final int BURST_COOLDOWN = 20;
	public static final double BURST_SPEED = 1.8;

	// ---------------------------------------------------------------- G / Shift+G
	public static final double GRAB_REACH = 4.5;
	public static final float GRAB_COST = 15f;
	/** Nothing bigger than this (bosses) can be picked up. */
	public static final float GRAB_MAX_HEALTH = 150f;
	public static final int GRAB_MAX_TICKS = 15 * S;
	public static final double HURL_SPEED = 1.9;
	public static final int GRAB_COOLDOWN = 10;

	public static final double BEAM_RANGE = 32.0;
	/** Both beams together hit for 6 every {@link #BEAM_HIT_INTERVAL} ticks. */
	public static final float BEAM_DAMAGE = 6f;
	public static final int BEAM_HIT_INTERVAL = 5;
	public static final int BEAM_FIRE_SECONDS = 4;
	public static final float BEAM_COST_PER_SECOND = 8f;
	public static final int BEAM_MAX_TICKS = 15 * S;
	public static final int BEAM_COOLDOWN = 20;

	// ---------------------------------------------------------------- Z / Shift+Z
	public static final int BARRAGE_BOLTS = 12;
	public static final int BARRAGE_TICKS = 30;
	public static final float BARRAGE_ARC_DEGREES = 70f;
	public static final double BARRAGE_HOMING = 0.12;
	public static final double BARRAGE_SEEK_RANGE = 20.0;
	public static final float BARRAGE_COST = 35f;
	public static final int BARRAGE_COOLDOWN = 6 * S;

	public static final int NOVA_CHARGE_TICKS = 2 * S;
	public static final double NOVA_RADIUS = 10.0;
	public static final float NOVA_DAMAGE = 45f;
	public static final int NOVA_FIRE_SECONDS = 8;
	public static final int NOVA_BLIND_TICKS = 5 * S;
	public static final float NOVA_COST = 100f;
	public static final int NOVA_COOLDOWN = 120 * S;
	/** From the ground she rises this fast while it charges. */
	public static final double NOVA_RISE = 0.25;
	public static final int NOVA_RISE_TICKS = 12;

	// ---------------------------------------------------------------- X / Shift+X
	public static final double DASH_DISTANCE = 10.0;
	public static final int DASH_TICKS = 5;
	public static final int DASH_IFRAMES = 8;
	public static final float DASH_COST = 10f;
	public static final int DASH_COOLDOWN = S;

	public static final double DIVE_SPEED = 2.4;
	public static final int DIVE_LAUNCH_TICKS = 7;
	public static final double DIVE_LAUNCH_SPEED = 1.5;
	public static final int DIVE_MAX_TICKS = 30;
	public static final float DIVE_DAMAGE = 18f;
	public static final double DIVE_RADIUS = 3.0;
	public static final float DIVE_COST = 20f;
	public static final int DIVE_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- C / Shift+C
	public static final double CHARGE_DISTANCE = 8.0;
	public static final int CHARGE_TICKS = 6;
	public static final float CHARGE_DAMAGE = 8f;
	public static final float CHARGE_WALL_BONUS = 10f;
	public static final float CHARGE_COST = 20f;
	public static final int CHARGE_COOLDOWN = 3 * S;

	public static final float POUND_DAMAGE = 12f;
	public static final double POUND_RADIUS = 5.0;
	public static final double POUND_LAUNCH = 0.9;
	public static final double POUND_DIVE_SPEED = 2.0;
	public static final int POUND_MAX_FALL_TICKS = 40;
	public static final float POUND_COST = 25f;
	public static final int POUND_COOLDOWN = 4 * S;

	// ---------------------------------------------------------------- V / Shift+V
	public static final double KISS_REACH = 4.0;
	public static final int KISS_COOLDOWN = 5 * S;
	public static final int BOND_TICKS = 60 * S;
	/** "Learning their tongue": the villager's gossip about her, the same kind curing a zombie villager gives. */
	public static final int KISS_GOSSIP = 20;
	public static final long DAY_TICKS = 24000L;

	public static final float RADIANCE_BOLT_SURCHARGE = 0.25f;
	public static final int RADIANCE_COOLDOWN = 10;
}
