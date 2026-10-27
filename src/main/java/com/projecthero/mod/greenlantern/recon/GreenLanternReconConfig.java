package com.projecthero.mod.greenlantern.recon;

/**
 * v0.17.1: the balance numbers of the Hard-Light Drone ({@link GreenLanternDrone}) and the Hard-Light Gateway with its
 * ring waypoints ({@link GreenLanternGateway}, {@link GreenLanternWaypoints}). Ring Charge is out of 10 000.
 */
public final class GreenLanternReconConfig {
	private GreenLanternReconConfig() {
	}

	// ---------------------------------------------------------------- Hard-Light Drone

	/** Ring Charge to shape the drone. */
	public static final float DRONE_COST = 80f;
	/** Ring Charge a second while it is out. */
	public static final float DRONE_UPKEEP_PER_SEC = 2f;
	/** Its hit points (it can be shot down). */
	public static final float DRONE_HP = 40f;
	/** How often it sweeps for hostiles, and the radius it sweeps. */
	public static final int DRONE_SCAN_INTERVAL_TICKS = 60;
	public static final double DRONE_SCAN_RADIUS = 32.0;
	/** How long a sweep's outlines last on the bearer's screen (a little longer than the interval, so they never blink). */
	public static final int DRONE_MARK_TICKS = 90;
	/** Dropped items and XP orbs within this many blocks of the bearer are fetched. */
	public static final double DRONE_FETCH_RADIUS = 8.0;
	/** Flight speed while fetching / scouting (blocks per tick). */
	public static final double DRONE_FETCH_SPEED = 0.7;
	public static final double DRONE_SCOUT_SPEED = 1.6;
	/** Hostiles targeting the bearer within this range are zapped. */
	public static final double DRONE_ZAP_RANGE = 16.0;
	public static final float DRONE_ZAP_DAMAGE = 3f;
	public static final int DRONE_ZAP_INTERVAL_TICKS = 30;
	/** Shift + key: the scout run's reach, hover time and sweep radius. */
	public static final double SCOUT_RANGE = 64.0;
	public static final int SCOUT_HOVER_TICKS = 200;
	public static final double SCOUT_RADIUS = 24.0;
	/** Further than this from the bearer (not scouting) and the drone blinks back to the shoulder. */
	public static final double DRONE_LEASH = 28.0;

	// ---------------------------------------------------------------- Hard-Light Gateway + waypoints

	/** The ring remembers this many waypoints per Lantern. */
	public static final int MAX_WAYPOINTS = 5;
	public static final int WAYPOINT_NAME_MAX = 24;
	/** Gateway cost: base + per block, capped. */
	public static final float GATEWAY_BASE_COST = 100f;
	public static final float GATEWAY_COST_PER_BLOCK = 0.2f;
	public static final float GATEWAY_MAX_COST = 600f;
	/** The channel (the frame tracing itself out of light). */
	public static final int GATEWAY_CHANNEL_TICKS = 60;
	/** Both doors stay open this long. */
	public static final int GATEWAY_OPEN_TICKS = 200;
	/** How long a door takes to unfold (and to fold away). */
	public static final int GATEWAY_UNFOLD_TICKS = 12;
	/** After a successful gateway. */
	public static final int GATEWAY_COOLDOWN_TICKS = 5 * 60 * 20;
	/** A waypoint closer than this is refused (just walk). */
	public static final double GATEWAY_MIN_DISTANCE = 8.0;
	/** The door stands this far in front of the bearer. */
	public static final double GATEWAY_DOOR_DISTANCE = 2.6;
	/** Door opening size (blocks): half the width, and the height. */
	public static final double DOOR_HALF_WIDTH = 0.95;
	public static final double DOOR_HEIGHT = 2.9;
	/** A traveller can't take a door again for this long (no bouncing straight back). */
	public static final int GATEWAY_PASS_COOLDOWN = 30;
	public static final String GATEWAY_COOLDOWN_ID = "construct_gateway";

	/** Ring Charge for a gateway across {@code blocks}. */
	public static float gatewayCost(double blocks) {
		return Math.min(GATEWAY_MAX_COST, GATEWAY_BASE_COST + (float) (Math.max(0.0, blocks) * GATEWAY_COST_PER_BLOCK));
	}
}
