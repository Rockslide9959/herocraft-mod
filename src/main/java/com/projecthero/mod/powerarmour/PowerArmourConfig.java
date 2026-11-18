package com.projecthero.mod.powerarmour;

/**
 * v0.17.5 Power Armour: every tuning number outside the tier / core / module tables. Damage values are the Iron base, multiplied
 * by {@link ArmourTier#damage} (and +15% with Overclock); cooldowns are multiplied by {@link ArmourTier#cooldown}. Energy is in
 * core units (E). See {@code docs/POWER_ARMOUR_REFERENCE.md}.
 */
public final class PowerArmourConfig {
	private PowerArmourConfig() {
	}

	// ---- frame + sequence (ticks)
	public static final int FRAME_OPEN_TICKS = 18;
	public static final int FRAME_CLOSE_TICKS = 22;
	public static final int FRAME_STAY_OPEN_TICKS = 60;
	public static final int FRAME_OCCUPANT_TIMEOUT = 120;
	public static final int ENTER_OPEN_TICKS = 20;
	public static final int ENTER_WALK_TICKS = 22;
	public static final int SEAL_TICKS = 40;
	public static final int EXIT_HOLD_TICKS = 16;
	public static final int UNSEAL_TICKS = 18;
	public static final int EXIT_WALK_TICKS = 18;
	public static final double ENTER_REACH = 4.5;
	/** How far in front of the frame the player turns round before stepping back in. */
	public static final double APPROACH = 1.15;
	/**
	 * Round 2 (user: "the proportions are basically just a scaled up player"): the suit is the player model scaled up by this one
	 * factor on every axis, with its layers (under-suit, plates, trim, glow) a thin shell inside the scaled box. Sealed, the
	 * wearer's {@code Attributes.SCALE} gets the same factor, so the hitbox (0.66 x 1.98, still under a 2-block doorway), the eye
	 * height (1.78) and the drawn suit all agree; the standing frame draws at it too.
	 */
	public static final float SUIT_SCALE = 1.1f;
	/** Shell depth (model px, before scaling) the suit's layers take inside the box: the player's 4-px limbs still fit inside. */
	public static final float SHELL = 0.18f;

	/** The suit's scale per axis (x, y, z): one factor, so a suit is exactly a player's proportions. */
	public static float[] suitScale() {
		return new float[] { SUIT_SCALE, SUIT_SCALE, SUIT_SCALE };
	}

	// ---- energy
	/** Idle draw per tick while sealed (1 E/s), times the tier's drain and Overclock's. */
	public static final float IDLE_DRAIN = 0.05f;
	public static final float OVERCLOCK_DRAIN = 1.25f;
	public static final float OVERCLOCK_DAMAGE = 1.15f;
	/** A fall the suit soaks up costs this much per point of fall damage (Vibranium: charges instead). */
	public static final float FALL_COST = 4f;
	public static final float VIBRANIUM_FALL_GAIN = 2f;
	public static final float VIBRANIUM_BLAST_GAIN = 3f;
	public static final float VIBRANIUM_BLAST_CUT = 0.5f;
	public static final float SOLAR_PER_SECOND = 0.5f;
	/** Capacitor Bank: while the core is above half, it tops the bank up by this much a second. */
	public static final float BANK_SIPHON = 2f;
	public static final float VISOR_PER_SECOND = 1f;
	/** Unpowered: walk speed multiplier change (very slow). */
	public static final float UNPOWERED_SPEED = -0.7f;
	public static final float STEP_HEIGHT = 0.45f;

	// ---- integrity
	/** Integrity lost per point of incoming damage while sealed. */
	public static final float INTEGRITY_PER_DAMAGE = 1f;
	/** Dying in the suit: it ejects you and stands where you fell with this share of its integrity gone. */
	public static final float DEATH_INTEGRITY_LOSS = 0.3f;
	public static final float REPAIR_KIT_SHARE = 0.3f;

	// ---- R Servo Punch
	public static final float PUNCH_DAMAGE = 8f;
	public static final double PUNCH_RANGE = 4.0;
	public static final int PUNCH_CHARGE_TICKS = 20;
	public static final int PUNCH_COOLDOWN = 10;
	public static final float PUNCH_COST = 8f;
	public static final float PUNCH_CHARGED_COST = 12f;
	public static final float PUNCH_KNOCKBACK = 1.2f;
	public static final float BREAK_HARDNESS = 1.5f;
	public static final float POWER_FIST_MULT = 1.5f;
	public static final double POWER_FIST_WAVE = 4.0;

	// ---- G Ground Stomp
	public static final float STOMP_DAMAGE = 7f;
	public static final double STOMP_RADIUS = 4.0;
	public static final double SEISMIC_RADIUS = 6.0;
	public static final int STOMP_COOLDOWN = 120;
	public static final float STOMP_COST = 40f;

	// ---- Shift+G Servo Grip / Magnetic Clamp
	public static final double GRIP_RANGE = 3.5;
	public static final double CLAMP_RANGE = 14.0;
	public static final int GRIP_HOLD_TICKS = 80;
	public static final float THROW_DAMAGE = 8f;
	public static final int GRIP_COOLDOWN = 60;
	public static final float GRIP_COST = 20f;
	public static final float BOSS_HEALTH = 100f;

	// ---- Z Shoulder Charge
	public static final float CHARGE_DAMAGE = 9f;
	public static final int CHARGE_TICKS = 8;
	public static final double CHARGE_SPEED = 1.1;
	public static final int CHARGE_COOLDOWN = 80;
	public static final float CHARGE_COST = 30f;
	public static final float BREACH_HARDNESS = 3f;

	// ---- Shift+Z Core Overload
	public static final float OVERLOAD_DAMAGE = 24f;
	public static final double OVERLOAD_RADIUS = 6.0;
	public static final int OVERLOAD_WINDUP = 20;
	public static final int OVERLOAD_COOLDOWN = 900;
	public static final float OVERLOAD_SHARE = 0.35f;
	public static final float OVERLOAD_MIN = 250f;

	// ---- X Jump Jets / Thruster Pack
	public static final int JETS_COOLDOWN = 40;
	public static final float JETS_COST = 20f;
	public static final float THRUST_PER_TICK = 0.4f;
	public static final int AFTERBURNER_COOLDOWN = 60;
	public static final float AFTERBURNER_COST = 60f;

	// ---- Shift+R arm weapons
	public static final float GATLING_DAMAGE = 2.5f;
	public static final int GATLING_INTERVAL = 3;
	public static final float GATLING_COST = 1.5f;
	public static final double GATLING_RANGE = 32.0;
	public static final float FLAMER_DAMAGE = 1.5f;
	public static final double FLAMER_RANGE = 6.0;
	public static final float FLAMER_COST = 1f;
	public static final float TESLA_DAMAGE = 9f;
	public static final int TESLA_TARGETS = 4;
	public static final int TESLA_COOLDOWN = 40;
	public static final float TESLA_COST = 35f;
	public static final float RAIL_DAMAGE = 28f;
	public static final int RAIL_CHARGE_TICKS = 30;
	public static final int RAIL_BEAM_TICKS = 8;
	public static final double RAIL_RANGE = 48.0;
	public static final int RAIL_COOLDOWN = 120;
	public static final float RAIL_COST = 110f;
	public static final float PILEDRIVER_DAMAGE = 14f;
	public static final int PILEDRIVER_COOLDOWN = 100;
	public static final float PILEDRIVER_COST = 45f;

	// ---- C back modules
	public static final float MISSILE_DAMAGE = 6f;
	public static final int MISSILE_COUNT = 4;
	public static final int MISSILE_COOLDOWN = 160;
	public static final float MISSILE_COST = 80f;
	public static final int SHIELD_TICKS = 160;
	public static final int SHIELD_COOLDOWN = 200;
	public static final float SHIELD_PER_SECOND = 6f;
	public static final float SHIELD_CUT = 0.6f;
	public static final int DRONE_TICKS = 200;
	public static final int DRONE_COOLDOWN = 600;
	public static final float DRONE_COST = 100f;
	public static final float DRONE_REPAIR_PER_SECOND = 5f;
	public static final int TURRET_TICKS = 600;
	public static final int TURRET_COOLDOWN = 800;
	public static final float TURRET_COST = 150f;
	public static final float TURRET_DAMAGE = 3f;

	// ---- V visor
	public static final double VISOR_RANGE = 32.0;
	public static final int ORE_RANGE = 10;
	public static final double LOCK_RANGE = 32.0;
	public static final double LOCK_CONE = 0.87; // cos 30 degrees

	// ---- passives
	public static final float REACTIVE_DAMAGE = 4f;
	public static final int REACTIVE_COOLDOWN = 60;
	public static final float REACTIVE_COST = 10f;
	public static final float SERVO_BOOST = 0.2f;
}
