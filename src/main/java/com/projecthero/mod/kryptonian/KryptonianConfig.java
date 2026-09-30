package com.projecthero.mod.kryptonian;

/**
 * Every Kryptonian tunable (v0.14.8), static finals like {@code AllMightConfig}. Ticks are game ticks (20 a second).
 *
 * <p>Tier check against the other Hero-Tier heavy hitters: the Hulk punches for 20 with 60 HP, 20 armour and a 3 HP/s
 * regeneration; Thor takes 80% less damage, hits for 22 with Mjolnir and flies. The Kryptonian sits between them: 15
 * bare-handed, 60 HP, 75% less damage, 2 HP/s in sunlight, true flight, and a kit built around a Solar Energy bar.
 */
public final class KryptonianConfig {
	private static final int S = 20;

	private KryptonianConfig() {
	}

	// ---------------------------------------------------------------- body (passives)
	/** Added to the player's base 1 attack damage: bare fists hit for 15. */
	public static final double ATTACK_BONUS = 14.0;
	/** 20 + 40 = 60 max health. */
	public static final double HEALTH_BONUS = 40.0;
	public static final double KNOCKBACK_RESISTANCE = 1.0;
	/** Walking speed +40% (base multiplier). */
	public static final double SPEED_BONUS = 0.4;
	public static final double STEP_HEIGHT_BONUS = 0.5;
	public static final double REACH_BONUS = 1.0;
	/** Super jump: roughly 4 blocks. */
	public static final double JUMP_BLOCKS = 4.0;
	/** Every hit that gets through is cut by 75% (fire, lava, falls and drowning never get through at all). */
	public static final float DAMAGE_REDUCTION = 0.75f;
	/** Melee punches: extra knockback (sprinting: more, plus a little lift). */
	public static final double PUNCH_KNOCKBACK = 1.2;
	public static final double SPRINT_PUNCH_KNOCKBACK = 2.2;

	// ---------------------------------------------------------------- solar energy
	public static final float SOLAR_MAX = 100.0f;
	/** Per second: in direct sunlight (daytime, open sky, not raining). */
	public static final float SOLAR_SUN_PER_SECOND = 4.0f;
	/** Per second: daytime without direct sun (rain, shade, indoors near the surface). */
	public static final float SOLAR_SHADE_PER_SECOND = 1.0f;
	/** Per second: at night under the open sky. */
	public static final float SOLAR_NIGHT_PER_SECOND = 0.5f;
	/** Per second: underground / the Nether / the End. */
	public static final float SOLAR_DARK_PER_SECOND = 0.25f;
	/** Healing in the sun: 1 HP every 10 ticks (2 HP/s); otherwise 1 HP every 40 ticks. */
	public static final int SUN_REGEN_INTERVAL = 10;
	public static final int SHADE_REGEN_INTERVAL = 40;
	public static final float REGEN_AMOUNT = 1.0f;
	/** The sun also feeds him: one hunger point every this many ticks in direct sunlight. */
	public static final int SUN_FEED_INTERVAL = 10 * S;

	// ---------------------------------------------------------------- kryptonite
	/** Kryptonite ore / block within this many blocks (a cube) weakens him. */
	public static final int KRYPTONITE_BLOCK_RADIUS = 5;
	/** A shard on the ground, or held by someone, within this many blocks. */
	public static final double KRYPTONITE_ENTITY_RADIUS = 6.0;
	/** Seconds the weakness lingers after the kryptonite is gone. */
	public static final int KRYPTONITE_LINGER_TICKS = 2 * S;
	public static final float KRYPTONITE_SOLAR_DRAIN_PER_SECOND = 5.0f;
	public static final float KRYPTONITE_DAMAGE_PER_SECOND = 1.0f;

	// ---------------------------------------------------------------- flight
	/** Blocks per tick: cruising (18 b/s) and Sprint super-speed flight (40 b/s). */
	public static final double FLIGHT_SPEED = 0.9;
	public static final double FLIGHT_SPRINT_SPEED = 2.0;
	/** Blocks per tick straight up / down (Space / Sneak). */
	public static final double FLIGHT_VERTICAL_SPEED = 0.6;
	/** Fraction of the gap to the wanted velocity closed per tick: steering, coasting and braking (S). */
	public static final double FLIGHT_ACCELERATION = 0.18;
	public static final double FLIGHT_COAST = 0.08;
	public static final double FLIGHT_BRAKE = 0.35;
	/** Ticks after take-off before touching the ground lands him. */
	public static final int FLIGHT_LIFTOFF_GRACE = 10;
	/** Blocks per tick above which the sonic boom fires (and below which it re-arms). */
	public static final double SONIC_BOOM_SPEED = 1.5;
	public static final double SONIC_BOOM_REARM_SPEED = 0.9;

	// ---------------------------------------------------------------- R: Kryptonian Punch
	public static final float PUNCH_DAMAGE = 32.0f;
	public static final double PUNCH_RANGE = 6.0;
	public static final double PUNCH_LAUNCH = 3.0;
	public static final double PUNCH_LIFT = 0.6;
	/** The shockwave where the punch lands. */
	public static final float PUNCH_WAVE_DAMAGE = 12.0f;
	public static final double PUNCH_WAVE_RADIUS = 3.0;
	public static final float PUNCH_COST = 8.0f;
	public static final int PUNCH_COOLDOWN = 4 * S;

	// ---------------------------------------------------------------- Shift+R: Heat Vision (held)
	public static final double HEAT_RANGE = 32.0;
	public static final float HEAT_DAMAGE = 4.0f;
	public static final int HEAT_HIT_INTERVAL = 5;
	public static final int HEAT_FIRE_SECONDS = 5;
	/** The longest one burst can be held. */
	public static final int HEAT_MAX_TICKS = 6 * S;
	public static final float HEAT_COST_PER_SECOND = 3.0f;
	/** Counted from when the beam stops. */
	public static final int HEAT_COOLDOWN = 5 * S;

	// ---------------------------------------------------------------- G: Freeze Breath
	public static final double BREATH_RANGE = 12.0;
	public static final double BREATH_CONE_DEGREES = 60.0;
	public static final int BREATH_TICKS = 30;
	public static final int BREATH_HIT_INTERVAL = 5;
	public static final float BREATH_DAMAGE = 5.0f;
	public static final int BREATH_SLOW_TICKS = 6 * S;
	public static final float BREATH_COST = 12.0f;
	public static final int BREATH_COOLDOWN = 8 * S;

	// ---------------------------------------------------------------- Shift+G: Thunderclap
	public static final double CLAP_RANGE = 20.0;
	public static final double CLAP_CONE_DEGREES = 80.0;
	public static final float CLAP_DAMAGE = 20.0f;
	public static final double CLAP_KNOCKBACK = 3.0;
	public static final double CLAP_LIFT = 0.4;
	public static final int CLAP_STUN_TICKS = 2 * S;
	public static final float CLAP_COST = 15.0f;
	public static final int CLAP_COOLDOWN = 10 * S;

	// ---------------------------------------------------------------- Z: Ground Slam
	public static final double SLAM_DIVE_SPEED = 2.6;
	public static final float SLAM_DAMAGE = 30.0f;
	public static final double SLAM_RADIUS = 7.0;
	public static final double SLAM_KNOCKBACK = 1.6;
	public static final double SLAM_LIFT = 1.0;
	public static final double SLAM_CRATER_RADIUS = 2.5;
	public static final int SLAM_CRATER_MAX_BLOCKS = 30;
	/** The longest a dive may last before it slams wherever he is. */
	public static final int SLAM_MAX_DIVE_TICKS = 3 * S;
	public static final float SLAM_COST = 15.0f;
	public static final int SLAM_COOLDOWN = 8 * S;

	// ---------------------------------------------------------------- Shift+Z: SOLAR FLARE (ultimate)
	public static final float FLARE_MIN_SOLAR = 50.0f;
	public static final int FLARE_CHARGE_TICKS = 2 * S;
	public static final double FLARE_RADIUS = 12.0;
	/** Damage = base + every point of Solar Energy dumped x per-solar (so 60..120). */
	public static final float FLARE_BASE_DAMAGE = 60.0f;
	public static final float FLARE_DAMAGE_PER_SOLAR = 0.6f;
	public static final double FLARE_KNOCKBACK = 3.5;
	public static final double FLARE_LIFT = 1.0;
	public static final double FLARE_CRATER_RADIUS = 4.0;
	public static final int FLARE_CRATER_MAX_BLOCKS = 80;
	/** Depowered afterwards: no flight, no moves, no passives, no Solar Energy. */
	public static final int FLARE_DEPOWER_TICKS = 30 * S;
	public static final int FLARE_COOLDOWN = 90 * S;

	// ---------------------------------------------------------------- X: Super Dash
	public static final double DASH_DISTANCE = 16.0;
	public static final double DASH_SPEED = 2.0;
	public static final float DASH_DAMAGE = 20.0f;
	public static final double DASH_HIT_RADIUS = 1.8;
	public static final double DASH_KNOCKBACK = 2.5;
	public static final float DASH_COST = 6.0f;
	public static final int DASH_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- Shift+X: Sky Launch
	public static final double LAUNCH_HEIGHT = 40.0;
	public static final float LAUNCH_WAVE_DAMAGE = 8.0f;
	public static final double LAUNCH_WAVE_RADIUS = 4.0;
	public static final float LAUNCH_COST = 5.0f;
	public static final int LAUNCH_COOLDOWN = 8 * S;

	// ---------------------------------------------------------------- V: X-Ray Vision
	public static final int XRAY_TICKS = 10 * S;
	public static final double XRAY_RANGE = 48.0;
	public static final float XRAY_COST = 5.0f;
	public static final int XRAY_COOLDOWN = 20 * S;

	// ---------------------------------------------------------------- Shift+V: Super Grab / Throw
	public static final double GRAB_RANGE = 6.0;
	public static final double GRAB_MAX_WIDTH = 3.0;
	public static final int GRAB_HOLD_TICKS = 10 * S;
	public static final double THROW_SPEED = 3.0;
	public static final float THROW_DAMAGE = 24.0f;
	public static final double THROW_IMPACT_RADIUS = 3.0;
	public static final float GRAB_COST = 5.0f;
	/** Counted from the throw. */
	public static final int GRAB_COOLDOWN = 10 * S;

	// ---------------------------------------------------------------- bosses
	/** A boss takes at most this share of its max health from one hit, and is never knocked back. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	public static final float BOSS_HEALTH_THRESHOLD = 300.0f;

	// ---------------------------------------------------------------- the Kryptonite Meteor
	/** Chance, rolled once every dusk in the Overworld, that a meteor falls tonight. */
	public static final double METEOR_NIGHTLY_CHANCE = 0.2;
	/** After this many meteor-less nights one is guaranteed. */
	public static final int METEOR_PITY_NIGHTS = 6;
	public static final int METEOR_MIN_DISTANCE = 80;
	public static final int METEOR_MAX_DISTANCE = 200;
	/** Ticks the fireball takes from the sky to the ground. */
	public static final int METEOR_FALL_TICKS = 100;
	public static final double METEOR_START_HEIGHT = 140.0;
	public static final double METEOR_CRATER_RADIUS = 4.0;
	public static final int METEOR_ORE_COUNT = 10;
}
