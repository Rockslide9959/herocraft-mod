package com.projecthero.mod.saiyan;

/** v0.17.3: the Saiyan's numbers (per-form stats are on {@link SaiyanForm}). Times in ticks. */
public final class SaiyanConfig {
	private static final int S = 20;
	private static final int MIN = 60 * S;

	/** The Ki bar. */
	public static final float KI_MAX = 100f;
	/** Hold Q: Ki a tick (12 a second, empty to full in about 8 s). */
	public static final float KI_CHARGE_PER_TICK = 12f / S;
	/** Base and Super Saiyan, not charging and not flying: Ki trickles back (0.5 a second). */
	public static final float KI_REGEN_PER_TICK = 0.5f / S;
	/** Flying costs Ki (0.25 a second), on top of the form's drain. */
	public static final float KI_FLIGHT_PER_TICK = 0.25f / S;
	/** Charging slows you to a walk-in-place (movement x0.3). */
	public static final double CHARGE_SLOW = -0.7;

	/** Ki hits 0 in a form: back to Base, exhausted -- slowed and unable to transform for 5 s. */
	public static final int EXHAUST_TICKS = 5 * S;
	public static final double EXHAUST_SLOW = -0.35;

	/** Between two transformations. */
	public static final int TRANSFORM_COOLDOWN = S;
	/** How long the transformation burst plays (for the client's animation and the server's particles). */
	public static final int TRANSFORM_BURST_TICKS = 16;

	/** A regenerating form heals 1 HP every 4 seconds (neither Base nor Super Saiyan does). */
	public static final float REGEN_AMOUNT = 1.0f;
	public static final int REGEN_INTERVAL = 4 * S;

	/** X: the Ki Dash. */
	public static final float DASH_COST = 6f;
	public static final int DASH_COOLDOWN = 25;
	public static final double DASH_SPEED = 1.6;
	/** No fall damage for this long after a dash. */
	public static final int DASH_FALL_GRACE = 2 * S;

	/** Shift+X: flight (Kryptonian-style directional flight), blocks a second, scaled by the form's speed. */
	public static final double FLIGHT_CRUISE_BPS = 18.0;
	public static final double FLIGHT_SPRINT_BPS = 36.0;
	public static final double FLIGHT_MAX_BPS = 55.0;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 10;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	/** How often the server re-sends the Ki bar while it is only ticking (structural changes go at once). */
	public static final int SYNC_INTERVAL = 5;

	private SaiyanConfig() {
	}
}
