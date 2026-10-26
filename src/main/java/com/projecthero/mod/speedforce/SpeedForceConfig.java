package com.projecthero.mod.speedforce;

/**
 * v0.17.1: every Speed Force Speedster number in one place (blocks a second, ticks, hearts x2). The speed curve is keyed off
 * {@link #TOP_SPEED_BPS} and {@link #SUIT_SPEED_MULTIPLIER} only, so the whole thing retunes from those two.
 */
public final class SpeedForceConfig {
	private SpeedForceConfig() {
	}

	// ---------------------------------------------------------------- speed

	/** Full momentum in the full Flash Suit -- the fastest runner in the mod (user: 250). */
	public static final double TOP_SPEED_BPS = 250.0;
	/** v0.17.1 (user): every Speed Force Speedster blow is 50% stronger than the plain Speedster's (no Overdrive to double it). */
	public static final float DAMAGE_BONUS = 1.5f;
	/** v0.17.1 (user): momentum builds only while sprinting; from this much, letting go of Ctrl holds the speed. */
	public static final float CRUISE_MOMENTUM = 0.30f;
	/** v0.17.1 (user): Speed Mode breaks blocks 75% faster. */
	public static final double SPEED_MODE_MINING = 0.75;
	/** The Flash Suit makes the Speed Force Speedster this much faster (user: 50%); without it full momentum is 250 / 1.5. */
	public static final double SUIT_SPEED_MULTIPLIER = 1.5;
	/**
	 * The speed curve as fractions of the top speed at momentum 0, 25%, 60% and 100% (v0.17.1, user: Speed Mode starts at
	 * 20 b/s -- no suit: 20 / 40 / 80 / 167 b/s; the suit: 30 / 60 / 120 / 250 b/s).
	 */
	public static final double[] CURVE_MOMENTUM = { 0.0, 0.25, 0.60, 1.00 };
	public static final double[] CURVE_FRACTION = { 0.12, 0.24, 0.48, 1.00 };
	/** Walking (not sprinting) in Speed Mode: the 20 b/s base (user), whatever the momentum. */
	public static final double WALK_BPS = 20.0;
	/** Passing this speed fires the sonic boom ring (re-armed below {@link #SONIC_REARM_BPS}). */
	public static final double SONIC_BOOM_BPS = 100.0;
	public static final double SONIC_REARM_BPS = 60.0;
	/** Ticks the run must stay past {@link #SONIC_BOOM_BPS} before the boom (a slope pull is one or two ticks). */
	public static final int SONIC_HOLD_TICKS = 6;
	public static final double SONIC_RADIUS = 6.0;
	public static final float SONIC_DAMAGE = 2.0f;
	/**
	 * Step assist in Speed Mode -- v0.17.1 (user: "increase speed force speedsters step assist please while in speed mode"): 6
	 * blocks as standard, rising past 50% momentum to 8 at full momentum (max(6, 4 + 4 x momentum)); off, vanilla.
	 */
	public static final double STEP_SPEED_MODE = 6.0;
	public static final double STEP_BASE = 4.0;
	public static final double STEP_PER_MOMENTUM = 4.0;

	// ---------------------------------------------------------------- momentum

	/** Momentum gained a second while running flat out: 0-25% in 3 s, 25-60% in 7 more, 60-100% in 10 more (20 s total). */
	public static final double BUILD_LOW = 0.25 / 3.0;
	public static final double BUILD_MID = 0.35 / 7.0;
	public static final double BUILD_HIGH = 0.40 / 10.0;
	/** Standing still: all of it gone in 1.5 s. */
	public static final double STOP_DECAY_PER_TICK = 1.0 / 30.0;
	/** Walking (not sprinting): bleeds away over 10 s. */
	public static final double WALK_DECAY_PER_TICK = 0.005;
	/** A hit that does at least {@link #HIT_MIN_DAMAGE}. */
	public static final double HIT_LOSS = 0.15;
	public static final float HIT_MIN_DAMAGE = 1.0f;
	/** Slamming into a wall keeps only this much. */
	public static final double WALL_KEEP = 0.30;
	/** A U-turn costs at most this (scaled from 45 degrees up). */
	public static final double TURN_LOSS_MAX = 0.10;
	/** Momentum only builds while moving at least this fraction of the current target speed. */
	public static final double BUILD_SPEED_FRACTION = 0.5;
	/** Below 25% no fall damage protection; above it none at all. */
	public static final double NO_FALL_MOMENTUM = 0.25;
	/** Non-Flash armour burns above this momentum (10 durability a second, like Overdrive). */
	public static final double ARMOUR_BURN_MOMENTUM = 0.60;
	/** Wall running needs a little momentum. */
	public static final double WALL_RUN_MOMENTUM = 0.10;

	// ---------------------------------------------------------------- the Speed Force meter

	public static final float METER_MAX = 100f;
	/** One point for every this many blocks run. */
	public static final double BLOCKS_PER_POINT = 20.0;
	public static final float LIGHTNING_GAIN = 25f;
	public static final float COPPER_GAIN = 15f;
	public static final int COPPER_COOLDOWN = 40;

	// ---------------------------------------------------------------- passives

	public static final int REGEN_AMPLIFIER = 1;
	/** v0.17.1 (user): in Speed Mode the plain Speedster's passives -- Regeneration III and +30% swimming. */
	public static final int SPEED_MODE_REGEN_AMPLIFIER = 2;
	public static final double SPEED_MODE_SWIM_EFFICIENCY = 0.10; // the plain Speedster's own value
	public static final int REGEN_REFRESH = 60;

	// ---------------------------------------------------------------- bosses

	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ---------------------------------------------------------------- R: Rapid Assault / Shift+R Mach Punch / hold R Infinite Mass Punch

	/** v0.17.1 (user): tap / Shift / HOLD -- a key held this long (0.6 s, as the mutations' HoldTap) is its hold move. */
	public static final int HOLD_TICKS = 12;

	/** Rapid Assault -- the plain Speedster's numbers (4 punches of 5), its 1.5 s cooldown. */
	public static final int RAPID_COOLDOWN = 30;
	/** Mach Punch -- the plain move (a flat 20, a shockwave of 6), its 8 s cooldown. */
	public static final int MACH_COOLDOWN = 8 * 20;

	public static final float MASS_MIN = 10f;
	public static final float MASS_MOMENTUM = 60f;
	/** v0.17.1 (user): on release the punch locks onto the nearest foe within this many blocks (the crosshair cone first). */
	public static final double MASS_TRACK_RANGE = 8.0;
	/** The cone that counts as "under the crosshair" for the lock (cosine of the half-angle: about 35 degrees). */
	public static final double MASS_TRACK_CONE = 0.82;
	public static final double MASS_SHOCK_RADIUS = 4.0;
	public static final float MASS_SHOCK_MIN = 4f;
	public static final float MASS_SHOCK_MOMENTUM = 10f;
	public static final float MASS_COST = 15f;
	public static final int MASS_COOLDOWN = 10 * 20;

	// ---------------------------------------------------------------- G: Blitz / Shift+G Speed Vortex / hold G Lightning Throw

	/** Blitz -- the plain move (zip to the foe under the crosshair, 20 and a shockwave of 10), its 4 s cooldown. */
	public static final int BLITZ_COOLDOWN = 80;

	/** Hold G: the charge is full after this long held (past the hold itself); the bolt does up to +50% with it. */
	public static final int THROW_CHARGE_TICKS = 20;
	public static final float THROW_CHARGE_BONUS = 0.5f;
	public static final double THROW_RANGE = 48.0;
	public static final float THROW_MIN = 8f;
	public static final float THROW_MOMENTUM = 12f;
	public static final int THROW_CHAINS = 2;
	public static final double THROW_CHAIN_RADIUS = 6.0;
	public static final float THROW_COST = 10f;
	public static final int THROW_COOLDOWN = 3 * 20;

	/** Shift+G Speed Vortex (the momentum-scaled Speed Tornado). */
	public static final int TORNADO_TICKS = 4 * 20;
	public static final double TORNADO_RADIUS = 7.0;
	public static final float TORNADO_TICK_MIN = 2f;
	public static final float TORNADO_TICK_MOMENTUM = 2f;
	public static final float TORNADO_FINAL_MIN = 6f;
	public static final float TORNADO_FINAL_MOMENTUM = 8f;
	public static final float TORNADO_COST = 25f;
	public static final int TORNADO_COOLDOWN = 16 * 20;

	// ---------------------------------------------------------------- X: Momentum Dash / Shift+X Speed Sweep / hold X Tornado Arms

	/** Momentum Dash: the plain dash along the full look, and (like the old Speed Force Step) +15% momentum. */
	public static final double DASH_LAUNCH = 1.7;
	public static final double DASH_MOMENTUM_GAIN = 0.15;
	public static final float DASH_COST = 5f;
	public static final int DASH_COOLDOWN = 34;
	/** Speed Sweep -- the plain move (every foe within 50 blocks, 15 each, then home), its 10 s cooldown. */
	public static final int SWEEP_COOLDOWN = 10 * 20;

	/** v0.17.1 (user) hold X Tornado Arms: a cone of wind 10 blocks long in front, about 30 degrees either side. */
	public static final double ARMS_RANGE = 10.0;
	public static final double ARMS_CONE = 0.86;
	/** What the wind does a tick to a mob in it: pushed back along the cone, lifted. */
	public static final double ARMS_PUSH = 0.75;
	public static final double ARMS_LIFT = 0.18;
	public static final float ARMS_COST_PER_SECOND = 8f;
	public static final float ARMS_MIN = 4f;
	public static final int ARMS_COOLDOWN = 2 * 20;

	// ---------------------------------------------------------------- Shift+C: Phase (held)

	public static final float PHASE_MIN = 5f;
	public static final float PHASE_COST_PER_SECOND = 5f;
	public static final int PHASE_COOLDOWN = 2 * 20;

	// ---------------------------------------------------------------- Z: hold Flash Time (+ the Speed Force portal) / Shift+Z Time Remnant

	/** v0.17.1 (user: "a held down ability that drains speed force while active"): Speed Force a second while Z is held. */
	public static final float FLASH_COST_PER_SECOND = 8f;
	/** Needed to start it. */
	public static final float FLASH_MIN = 5f;
	/** The cooldown after letting go. */
	public static final int FLASH_TIME_COOLDOWN = 3 * 20;
	/** The slow's own ceiling (Z is let go long before; a full meter runs out at 12.5 s). */
	public static final int FLASH_TIME_MAX = 60 * 20;

	/** v0.17.1 (user): Z held at 100% momentum for 5 s opens the Speed Force (caster ticks -- Flash Time runs meanwhile). */
	public static final int PORTAL_TICKS = 5 * 20;
	/** "Running at max": horizontally at least this fraction of the top speed (the momentum itself is frozen in Flash Time). */
	public static final double PORTAL_RUN_FRACTION = 0.4;
	/** The portal forms this far in front, and grows to this radius. */
	public static final double PORTAL_DISTANCE = 4.0;
	public static final double PORTAL_RADIUS = 1.8;
	public static final int PORTAL_COOLDOWN = 3 * 60 * 20;

	public static final int REMNANT_TICKS = 8 * 20;
	public static final int REMNANT_LOOKBACK = 20;
	public static final float REMNANT_DAMAGE = 5f;
	public static final int REMNANT_HIT_EVERY = 10;
	public static final double REMNANT_RANGE = 12.0;
	public static final float REMNANT_COST = 30f;
	public static final int REMNANT_COOLDOWN = 30 * 20;

	// ---------------------------------------------------------------- V: Afterimage Decoys / Shift+V Speed Heal / hold V Rewind (N: Rescue Run)

	/** Afterimage Decoys -- the plain move (three images that hold every foe's attention for 6 s), its 20 s cooldown. */
	public static final int DECOY_COOLDOWN = 20 * 20;

	public static final double RESCUE_RANGE = 6.0;
	public static final int RESCUE_COOLDOWN = 10;
	public static final int RESCUE_GRACE = 3 * 20;

	public static final float HEAL_COST = 35f;
	public static final float HEAL_AMOUNT = 10f;
	public static final int HEAL_REGEN_TICKS = 4 * 20;
	public static final int HEAL_COOLDOWN = 25 * 20;

	public static final int REWIND_TICKS = 5 * 20;
	public static final float REWIND_COST = 60f;
	public static final int REWIND_COOLDOWN = 60 * 20;

	// ---------------------------------------------------------------- C: Speed Mode / Shift+C Phase / hold C the charge

	/** v0.17.1 (user): hold C -- 3 s in the charge stance, then let go to run at full speed at once. */
	public static final int CHARGE_TICKS = 3 * 20;
	/** After the launch the momentum is held at 100% this long (time to get running). */
	public static final int LAUNCH_HOLD_TICKS = 2 * 20;
	public static final int LAUNCH_COOLDOWN = 10 * 20;

	// ---------------------------------------------------------------- the Speed Force (the dimension)

	/** v0.17.1 (user design): one block run inside carries you this many across the real world. */
	public static final double REALM_TRAVEL_SCALE = 100.0;
	/** ... up to this far. */
	public static final double REALM_TRAVEL_MAX = 30000.0;
	/** Thrown out after this long inside. */
	public static final int REALM_MAX_TICKS = 60 * 20;
	/** Sneak held this long inside: out. */
	public static final int REALM_SNEAK_TICKS = 20;
	/** The exit portal sits this far up the road (the travel cap: 300 x 100 = 30,000). */
	public static final int REALM_EXIT_DISTANCE = 300;
	/** v0.17.1 (user): out of the Speed Force -- 2 minutes with no Speed Force gain, 50% slower and 50% weaker. */
	public static final int DRAINED_TICKS = 2 * 60 * 20;
	public static final double DRAINED_SPEED = -0.5;
	public static final double DRAINED_DAMAGE = -0.5;

	/** The old Surge's cooldown (no move uses it now; older saves' keys are cleaned by the join check). */
	public static final int SURGE_COOLDOWN = 90 * 20;

	// ---------------------------------------------------------------- the speed curve

	/** The flat-ground sprint speed (blocks a second) at {@code momentum}, with or without the full Flash Suit. */
	public static double sprintBps(double momentum, boolean suit) {
		double m = Math.max(0.0, Math.min(1.0, momentum));
		double frac = CURVE_FRACTION[CURVE_FRACTION.length - 1];
		for (int i = 1; i < CURVE_MOMENTUM.length; i++) {
			if (m <= CURVE_MOMENTUM[i]) {
				double t = (m - CURVE_MOMENTUM[i - 1]) / (CURVE_MOMENTUM[i] - CURVE_MOMENTUM[i - 1]);
				frac = CURVE_FRACTION[i - 1] + (CURVE_FRACTION[i] - CURVE_FRACTION[i - 1]) * t;
				break;
			}
		}
		return TOP_SPEED_BPS * frac / (suit ? 1.0 : SUIT_SPEED_MULTIPLIER);
	}

	/** Top speed (full momentum): 250 in the full suit, 167 without. */
	public static double topBps(boolean suit) {
		return sprintBps(1.0, suit);
	}

	/** Momentum gained per tick of flat-out running at {@code momentum}. */
	public static double buildPerTick(double momentum) {
		double perSecond = momentum < 0.25 ? BUILD_LOW : momentum < 0.60 ? BUILD_MID : BUILD_HIGH;
		return perSecond / 20.0;
	}
}
