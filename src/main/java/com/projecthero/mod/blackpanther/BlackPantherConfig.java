package com.projecthero.mod.blackpanther;

/** v0.17.3: every Black Panther number in one place. Ticks unless named otherwise; damage in half hearts. */
public final class BlackPantherConfig {
	public static final int VISION_TICKS = 200; // the Ancestral Plane, 10 s
	public static final int SUIT_UP_TICKS = 40;
	public static final int SUIT_DOWN_TICKS = 32;
	public static final int TOGGLE_COOLDOWN = 20;

	public static final float KINETIC_MAX = 100f;
	/** The suit soaks this share of a hit... */
	public static final float SOAK = 0.4f;
	/** ...and stores this much Kinetic per point soaked. */
	public static final float KINETIC_PER_DAMAGE = 4f;
	/** Out of combat the bar drains this much a second, after DRAIN_DELAY ticks without a hit taken or landed. */
	public static final float DRAIN = 2f;
	public static final int DRAIN_DELAY = 200;

	public static final double SUIT_MELEE = 5.0; // the vibranium claws
	/** Arrows and bullets below this much damage bounce off (the Iron Man bulletproof rule). */
	public static final float DEFLECT_LIMIT = 15f;

	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// R
	public static final float PULSE_COST = 30f;
	public static final float PULSE_DAMAGE = 8f;
	public static final double PULSE_RADIUS = 5.0;
	public static final int PULSE_COOLDOWN = 30;
	public static final float OVERLOAD_DAMAGE = 30f;
	public static final double OVERLOAD_RADIUS = 10.0;
	public static final int OVERLOAD_COOLDOWN = 100;

	// G
	public static final float CLAW_DAMAGE = 6f;
	public static final float CLAW_FINISH_DAMAGE = 9f;
	public static final double CLAW_REACH = 3.5;
	public static final int CLAW_STEP = 6;
	public static final int CLAW_COOLDOWN = 24;
	public static final int BLEED_TICKS = 80;
	public static final float BLEED_DAMAGE = 1f;
	public static final double POUNCE_RANGE = 12.0;
	public static final double POUNCE_SPEED = 1.6;
	public static final float POUNCE_LAND_DAMAGE = 8f;
	public static final float POUNCE_STRIKE_DAMAGE = 12f;
	public static final int POUNCE_PIN_TICKS = 40;
	public static final int POUNCE_COOLDOWN = 200;

	// X
	public static final double LEAP_SPEED = 1.62; // about 12 blocks up
	public static final double LEAP_FORWARD = 0.55;
	public static final int LEAP_COOLDOWN = 60;
	public static final int LEDGE_TICKS = 60;
	public static final double DROP_SPEED = 2.4;
	public static final float DROP_DAMAGE = 10f;
	public static final double DROP_RADIUS = 4.0;
	public static final int DROP_COOLDOWN = 80;

	// Z
	public static final float DAGGER_DAMAGE = 8f;
	public static final int DAGGER_BOUNCES = 3;
	public static final double DAGGER_SPEED = 1.7;
	public static final double DAGGER_RANGE = 24.0;
	public static final double DAGGER_HOP = 10.0;
	public static final int DAGGER_COOLDOWN = 100;
	public static final double FLASH_RADIUS = 6.0;
	public static final int FLASH_TICKS = 60;
	public static final int FLASH_COOLDOWN = 200;

	// V
	public static final double STALK_SEEN_WITHIN = 6.0;
	public static final float STALK_BACKSTAB = 3f;
	public static final int STALK_COOLDOWN = 40;
	public static final double SENSES_RANGE = 40.0;
	public static final int SENSES_TICKS = 200;
	public static final int SENSES_COOLDOWN = 400;
	public static final int FOOTPRINTS = 10;

	// C
	public static final float ABSORB_MAX = 40f;
	public static final int ABSORB_MAX_TICKS = 120;
	public static final int ABSORB_COOLDOWN = 80;
	public static final double ABSORB_SLOW = -0.6;
	/** Dot of the facing and the attacker: about 60 degrees either side counts as the front. */
	public static final double FRONT_ARC = 0.5;
	public static final int RETURN_WINDOW = 200;
	public static final double RETURN_RANGE = 48.0;
	public static final float RETURN_FACTOR = 1.5f;
	public static final float RETURN_MIN_DAMAGE = 6f;
	public static final int RETURN_COOLDOWN = 120;

	private BlackPantherConfig() {
	}
}
