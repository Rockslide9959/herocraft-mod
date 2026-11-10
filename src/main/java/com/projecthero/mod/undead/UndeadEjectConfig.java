package com.projecthero.mod.undead;

/**
 * v0.17.3: the numbers for Undead's ejection moves ({@link UndeadEject}): the flight, Blood Spray, Blood Whirlwind, Eject Feet,
 * Combustion Punch and Deadline. Ticks unless said otherwise.
 */
public final class UndeadEjectConfig {
	private UndeadEjectConfig() {
	}

	// ---------------------------------------------------------------- what comes off
	/**
	 * Rows (model px, of 12 from the shoulder / hip) of an arm or leg left after an ejection: the hand / foot (the last 4 rows) and
	 * half the forearm / calf above it go, so the cut sits at 7 -- a little below the elbow / knee.
	 */
	public static final float EJECT_ROWS = 7f;
	/** Combustion Punch: rows of the torso (of 12, from the neck) left on him -- its top third. */
	public static final float COMBUSTION_BODY_ROWS = 4f;
	/** How long an ejected piece lies about before it is gone (it stands / falls, then sinks away over the last 20 ticks). */
	public static final int PIECE_LIFE = 70;
	public static final int PIECE_FADE = 20;

	// ---------------------------------------------------------------- double-tap jump: the flight
	/** Blocks a second on the shared directional flight: cruising, sprinting (the Saiyan's numbers). */
	public static final double FLIGHT_CRUISE_BPS = 18.0;
	public static final double FLIGHT_SPRINT_BPS = 36.0;
	/** A second double-tap within this long of the first does nothing (one double-tap is one toggle). */
	public static final int FLIGHT_TOGGLE_COOLDOWN = 10;
	/** Ticks after take-off during which the vanilla flying flag may still be catching up on the client. */
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---------------------------------------------------------------- G: Blood Spray
	/** How far the stream reaches (blocks; less where a wall stops it). */
	public static final double SPRAY_RANGE = 11.0;
	/** Half-width of the stream at its far end (2 blocks wide) and at the stumps. */
	public static final double SPRAY_RADIUS = 1.0;
	public static final double SPRAY_RADIUS_NEAR = 0.35;
	public static final float SPRAY_DAMAGE = 10f;
	/** One damage tick every this many ticks of contact (2 a second). */
	public static final int SPRAY_HIT_EVERY = 10;
	/** Sprayed continuously this long: Slowness and Blindness while it lasts. */
	public static final int SPRAY_AFFLICT_AFTER = 30;
	/** A gap in the contact up to this long still counts as continuous. */
	public static final int SPRAY_CONTACT_GAP = 10;
	public static final int SPRAY_EFFECT_TICKS = 40;
	public static final int SPRAY_SLOWNESS_LEVEL = 1;
	/** Let go of G: the cooldown. */
	public static final int SPRAY_COOLDOWN = 120;

	// ---------------------------------------------------------------- Shift+G: Blood Whirlwind
	public static final int WHIRL_TICKS = 36;
	/** He rises over this long first, then spins at the top for the rest. */
	public static final int WHIRL_RISE_TICKS = 8;
	public static final double WHIRL_RISE = 2.0;
	public static final double WHIRL_RADIUS = 10.0;
	public static final float WHIRL_DAMAGE = 25f;
	/** Full turns over the spin. */
	public static final float WHIRL_TURNS = 3.0f;
	public static final int WHIRL_COOLDOWN = 300;

	// ---------------------------------------------------------------- tap X: Eject Feet
	/** A flat launch lands about this far away (one impulse; vanilla drag and gravity after it). */
	public static final double EJECT_FEET_DISTANCE = 10.0;
	public static final int EJECT_FEET_COOLDOWN = 40;
	/** No fall damage until this long after he touches down (or this long at most in the air). */
	public static final int EJECT_FEET_SAFE_AFTER_LANDING = 10;
	public static final int EJECT_FEET_MAX_AIR = 200;

	// ---------------------------------------------------------------- Shift+X: Combustion Punch
	public static final double COMBUSTION_RANGE = 15.0;
	/** Blocks a tick he flies at the target (32 b/s). */
	public static final double COMBUSTION_SPEED = 1.6;
	/** He punches once his fist is this close to the target's box. */
	public static final double COMBUSTION_REACH = 1.4;
	public static final int COMBUSTION_MAX_TICKS = 25;
	public static final float COMBUSTION_DAMAGE = 22f;
	/** The target is knocked back about this far. */
	public static final double COMBUSTION_KNOCKBACK = 5.0;
	public static final int COMBUSTION_COOLDOWN = 200;
	/** The punch pose holds this long before the body starts to regrow. */
	public static final int COMBUSTION_PUNCH_TICKS = 6;

	// ---------------------------------------------------------------- C: Deadline
	public static final double DEADLINE_RANGE = 24.0;
	public static final float DEADLINE_DAMAGE = 12f;
	public static final int DEADLINE_HIT_EVERY = 10;
	/** It can be held this long (8 s), then it stops on its own. */
	public static final int DEADLINE_MAX = 160;
	public static final int DEADLINE_COOLDOWN = 160;
	/** His own movement while firing: -80%. */
	public static final double DEADLINE_SELF_SLOW = -0.8;
}
