package com.projecthero.mod.silversurfer;

/**
 * v0.17.1: every Silver Surfer number in one place (ticks unless noted; damage in half-hearts). Balanced at Kryptonian /
 * Cyborg level after the v0.17.1 Green Lantern nerf: big hits 14-20, beams and small hits 2-5.
 */
public final class SilverSurferConfig {
	private SilverSurferConfig() {
	}

	// ---------------------------------------------------------------- the Power Cosmic
	public static final float COSMIC_MAX = 100f;
	/** Refill a second under open sky. */
	public static final float COSMIC_REGEN_SKY = 6f;
	/** Refill a second underground / indoors. */
	public static final float COSMIC_REGEN_COVERED = 1.5f;
	/** Sprint-surfing burns this much a second (cruising is free). */
	public static final float SPRINT_SURF_COST_PER_SECOND = 1f;
	/** Cosmic Awareness burns this much a second while it is on. */
	public static final float AWARENESS_COST_PER_SECOND = 0.5f;

	// ---------------------------------------------------------------- flight (blocks / second)
	/** The fastest flight in the mod: 60 b/s cruising, 110 sprinting, 150 at Light-Speed. */
	public static final double CRUISE_BPS = 60.0;
	public static final double SPRINT_BPS = 110.0;
	public static final double LIGHT_SPEED_BPS = 150.0;
	public static final double VERTICAL_BPS = 20.0;
	/** Fraction of the gap to the wanted velocity closed a tick (high speeds want a quick, firm response). */
	public static final double FLIGHT_ACCELERATION = 0.14;
	public static final double FLIGHT_REVERSE = 0.30;
	public static final double FLIGHT_IDLE = 0.22;

	// ---------------------------------------------------------------- R: Power Cosmic Blast (tap bolt / hold beam)
	public static final float BOLT_DAMAGE = 7f;
	public static final float BOLT_COST = 4f;
	public static final int BOLT_COOLDOWN = 12;
	public static final double BOLT_SPEED = 3.0;
	public static final double BOLT_RANGE = 48.0;
	/** Holding R this long turns the tap into the channelled beam. */
	public static final int BEAM_HOLD_TICKS = 8;
	public static final float BEAM_DAMAGE = 3f;
	public static final int BEAM_INTERVAL = 5;
	public static final float BEAM_COST_PER_SECOND = 8f;
	public static final int BEAM_MAX_TICKS = 100;
	public static final double BEAM_RANGE = 40.0;
	public static final int BEAM_COOLDOWN = 30;

	// ---------------------------------------------------------------- Shift+R: Cosmic Barrage
	public static final int BARRAGE_ORBS = 8;
	public static final float BARRAGE_DAMAGE = 4f;
	public static final float BARRAGE_COST = 25f;
	public static final int BARRAGE_COOLDOWN = 200;
	public static final double BARRAGE_RANGE = 32.0;
	public static final double BARRAGE_SPEED = 1.1;
	public static final int BARRAGE_LIFE = 80;

	// ---------------------------------------------------------------- G: Board Strike
	public static final float BOARD_DAMAGE = 10f;
	public static final int BOARD_CHAIN = 5;
	public static final double BOARD_CHAIN_RANGE = 9.0;
	public static final double BOARD_RANGE = 24.0;
	public static final double BOARD_SPEED = 1.6;
	public static final int BOARD_MAX_LIFE = 120;
	public static final float BOARD_COST = 15f;
	public static final int BOARD_COOLDOWN = 120;
	/** In flight: the ram charge. */
	public static final float RAM_DAMAGE = 16f;
	public static final int RAM_TICKS = 10;
	public static final double RAM_SPEED = 3.5;

	// ---------------------------------------------------------------- Shift+G: Transmutation
	public static final int STATUE_TICKS = 100;
	/** Players (when hard crowd control on players is allowed) are only held half as long. */
	public static final int STATUE_PLAYER_TICKS = 50;
	public static final float SHATTER_BONUS = 16f;
	public static final float BOSS_TRANSMUTE_DAMAGE = 6f;
	public static final double TRANSMUTE_RANGE = 32.0;
	public static final float TRANSMUTE_COST = 30f;
	public static final int TRANSMUTE_COOLDOWN = 360;

	// ---------------------------------------------------------------- X: Cosmic Dash
	public static final double DASH_DISTANCE = 30.0;
	public static final float DASH_DAMAGE = 9f;
	public static final float DASH_COST = 15f;
	public static final int DASH_COOLDOWN = 80;

	// ---------------------------------------------------------------- Shift+X: Light-Speed
	/** Three seconds -- 450 blocks, capped so the burst can't outrun chunk loading for long. */
	public static final int LIGHT_SPEED_TICKS = 60;
	public static final float LIGHT_SPEED_COST = 35f;
	public static final int LIGHT_SPEED_COOLDOWN = 400;
	/** What anything in the way takes (once per burst). */
	public static final float LIGHT_SPEED_RAM_DAMAGE = 8f;

	// ---------------------------------------------------------------- Z: Cosmic Barrier (hold)
	public static final float BARRIER_HP = 40f;
	public static final float BARRIER_COST_PER_SECOND = 4f;
	public static final int BARRIER_MAX_TICKS = 160;
	public static final int BARRIER_COOLDOWN = 120;
	public static final int BARRIER_BREAK_COOLDOWN = 240;
	/** Cosine of the half-angle in front of him the barrier covers. */
	public static final double BARRIER_ARC = 0.0;
	public static final double REFLECT_RANGE = 3.5;

	// ---------------------------------------------------------------- Shift+Z: Molecular Phase
	public static final int PHASE_TICKS = 80;
	public static final float PHASE_COST = 30f;
	public static final int PHASE_COOLDOWN = 320;

	// ---------------------------------------------------------------- V: Cosmic Awareness (toggle) / Shift+V Healing Touch
	public static final double AWARENESS_RANGE = 100.0;
	public static final int AWARENESS_ORE_RADIUS = 24;
	public static final int AWARENESS_COOLDOWN = 20;
	public static final float HEAL_AMOUNT = 10f;
	public static final double HEAL_RADIUS = 8.0;
	public static final float HEAL_COST = 30f;
	public static final int HEAL_COOLDOWN = 500;

	// ---------------------------------------------------------------- C: Cosmic Nova / Shift+C Herald's Meteor Storm
	public static final double NOVA_RADIUS = 10.0;
	public static final float NOVA_DAMAGE_CENTRE = 18f;
	public static final float NOVA_DAMAGE_EDGE = 9f;
	public static final float NOVA_COST = 45f;
	public static final int NOVA_COOLDOWN = 500;
	public static final int METEOR_TICKS = 100;
	public static final int METEOR_INTERVAL = 4;
	public static final float METEOR_DAMAGE = 5f;
	public static final double METEOR_RADIUS = 2.5;
	public static final double METEOR_SPREAD = 6.0;
	public static final double METEOR_RANGE = 48.0;
	public static final float METEOR_COST = 50f;
	public static final int METEOR_COOLDOWN = 800;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	/** A single hit never takes more than this fraction of a boss's max health. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	/** Regeneration I is kept topped up to at least this many ticks. */
	public static final int REGEN_REFRESH = 60;
}
