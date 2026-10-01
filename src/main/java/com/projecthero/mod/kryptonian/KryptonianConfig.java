package com.projecthero.mod.kryptonian;

/**
 * Every Kryptonian tunable (v0.14.8), static finals like {@code AllMightConfig}. Ticks are game ticks (20 a second).
 *
 * <p>Tier check against the other Hero-Tier heavy hitters: the Hulk punches for 20 with 60 HP, 20 armour and a 3 HP/s
 * regeneration; Thor takes 80% less damage, hits for 22 with Mjolnir and flies. The Kryptonian sits between them: 15
 * bare-handed, 60 HP, 75% less damage, 2 HP/s in sunlight, true flight, and a kit built around a Solar Energy bar.
 * (v0.14.15: 40 HP and Thor's 80%; v0.14.16: Solar-paid Regeneration III while hurt, and every move far cheaper so a
 * full bar of 100 lasts -- it only refills 5 s after the last drain.)
 */
public final class KryptonianConfig {
	private static final int S = 20;

	private KryptonianConfig() {
	}

	// ---------------------------------------------------------------- body (passives)
	/** Added to the player's base 1 attack damage: bare fists hit for 15. */
	public static final double ATTACK_BONUS = 14.0;
	/** 20 + 20 = 40 max health (v0.14.11: two rows of hearts, was 60). */
	public static final double HEALTH_BONUS = 20.0;
	public static final double KNOCKBACK_RESISTANCE = 1.0;
	/** Walking speed +40% (base multiplier). */
	public static final double SPEED_BONUS = 0.4;
	public static final double STEP_HEIGHT_BONUS = 0.5;
	public static final double REACH_BONUS = 1.0;
	/** Super jump: roughly 4 blocks. */
	public static final double JUMP_BLOCKS = 4.0;
	/**
	 * Every hit that gets through is cut by 80% (fire, lava, falls, lightning and drowning never get through at all).
	 * v0.14.15: was 75% -- now the same as Thor's.
	 */
	public static final float DAMAGE_REDUCTION = 0.80f;
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
	/**
	 * v0.14.16: Solar Energy only starts refilling this long after the last drain of any kind (a move, a held beam,
	 * flight, the Regeneration III, kryptonite).
	 */
	public static final int SOLAR_REGEN_DELAY = 5 * S;
	/** v0.14.16: flying costs this much Solar Energy a second (and so keeps the bar from refilling while airborne). */
	public static final float FLIGHT_SOLAR_PER_SECOND = 0.1f;
	/**
	 * v0.14.16: Regeneration III (amplifier 2) while he is below max health and has Solar Energy; it costs this much a
	 * second while it runs and comes off the moment he is back to full (replaces v0.14.15's permanent Regeneration I).
	 */
	public static final int REGEN_AMPLIFIER = 2;
	public static final float REGEN_SOLAR_PER_SECOND = 1.0f;
	/**
	 * Healing in direct sunlight on top of the Regeneration: 1 HP every 10 ticks (2 HP/s). v0.14.16: the 0.5 HP/s
	 * shade trickle is gone -- the Solar-powered Regeneration III covers healing away from the sun.
	 */
	public static final int SUN_REGEN_INTERVAL = 10;
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
	public static final double FLIGHT_SPRINT_SPEED = 1.75; // v0.14.11: 35 blocks a second (was 2.0 = 40)
	/** v0.14.11: sprint flight with Flight Boost on (X while flying): 55 blocks a second. */
	public static final double FLIGHT_BOOST_SPEED = 2.75;
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

	// v0.14.16 key layout and costs (the user's spec: the moves drain far less, so a full bar lasts):
	//   R  Punch 5          Shift+R  Thunderclap 5
	//   G  Heat Vision 1/s  Shift+G  Ground Pound 10
	//   Z  Freeze Breath 1/s Shift+Z SOLAR FLARE (a full 100)
	//   X  Super Dash 3 / Flight Boost   Shift+X  Sky Launch 3
	//   C  Super-Speed Barrage 5          Shift+C  Meteor Strike 10
	//   V  X-Ray Vision (toggle, free)    Shift+V  Pick Up / Set Down (free)

	// ---------------------------------------------------------------- R: Kryptonian Punch
	public static final float PUNCH_DAMAGE = 32.0f;
	public static final double PUNCH_RANGE = 6.0;
	public static final double PUNCH_LAUNCH = 3.0;
	public static final double PUNCH_LIFT = 0.6;
	/** The shockwave where the punch lands. */
	public static final float PUNCH_WAVE_DAMAGE = 12.0f;
	public static final double PUNCH_WAVE_RADIUS = 3.0;
	public static final float PUNCH_COST = 5.0f;
	public static final int PUNCH_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- G: Heat Vision (held)
	public static final double HEAT_RANGE = 32.0;
	public static final float HEAT_DAMAGE = 4.0f;
	public static final int HEAT_HIT_INTERVAL = 5;
	public static final int HEAT_FIRE_SECONDS = 5;
	/** The longest one burst can be held. */
	public static final int HEAT_MAX_TICKS = 10 * S;
	public static final float HEAT_COST_PER_SECOND = 1.0f;
	/** Counted from when the beam stops. */
	public static final int HEAT_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- Z: Freeze Breath (held)
	public static final double BREATH_RANGE = 12.0;
	public static final double BREATH_CONE_DEGREES = 60.0;
	/** v0.14.16: held like Heat Vision -- the longest one breath lasts. */
	public static final int BREATH_MAX_TICKS = 6 * S;
	public static final int BREATH_HIT_INTERVAL = 5;
	public static final float BREATH_DAMAGE = 5.0f;
	public static final int BREATH_SLOW_TICKS = 6 * S;
	public static final float BREATH_COST_PER_SECOND = 1.0f;
	/** Counted from when the breath stops. */
	public static final int BREATH_COOLDOWN = 4 * S;

	// ---------------------------------------------------------------- Shift+R: Thunderclap
	public static final double CLAP_RANGE = 20.0;
	public static final double CLAP_CONE_DEGREES = 80.0;
	public static final float CLAP_DAMAGE = 20.0f;
	public static final double CLAP_KNOCKBACK = 3.0;
	public static final double CLAP_LIFT = 0.4;
	public static final int CLAP_STUN_TICKS = 2 * S;
	public static final float CLAP_COST = 5.0f;
	public static final int CLAP_COOLDOWN = 8 * S;

	// ---------------------------------------------------------------- Shift+G: Ground Pound
	public static final double SLAM_DIVE_SPEED = 2.6;
	public static final float SLAM_DAMAGE = 30.0f;
	public static final double SLAM_RADIUS = 7.0;
	public static final double SLAM_KNOCKBACK = 1.6;
	public static final double SLAM_LIFT = 1.0;
	public static final double SLAM_CRATER_RADIUS = 2.5;
	public static final int SLAM_CRATER_MAX_BLOCKS = 30;
	/** The longest a dive may last before it slams wherever he is. */
	public static final int SLAM_MAX_DIVE_TICKS = 3 * S;
	public static final float SLAM_COST = 10.0f;
	public static final int SLAM_COOLDOWN = 8 * S;

	// ---------------------------------------------------------------- Shift+Z: SOLAR FLARE (ultimate)
	/** v0.14.16: needs a FULL bar and spends all of it (was 50+). */
	public static final float FLARE_MIN_SOLAR = SOLAR_MAX;
	public static final int FLARE_CHARGE_TICKS = 2 * S;
	public static final double FLARE_RADIUS = 12.0;
	/** v0.14.16: always the full bar's worth now (was 60 + 0.6 per point). */
	public static final float FLARE_DAMAGE = 120.0f;
	public static final double FLARE_KNOCKBACK = 3.5;
	public static final double FLARE_LIFT = 1.0;
	public static final double FLARE_CRATER_RADIUS = 4.0;
	public static final int FLARE_CRATER_MAX_BLOCKS = 80;
	/** Powerless afterwards: no moves, no flight, no Regeneration, no damage reduction, no passives, no Solar Energy. */
	public static final int FLARE_DEPOWER_TICKS = 30 * S;
	/** v0.14.16: and for the first 6 s of it Slowness IV, Blindness and Weakness IV. */
	public static final int FLARE_DEBUFF_TICKS = 6 * S;
	public static final int FLARE_DEBUFF_AMPLIFIER = 3;
	public static final int FLARE_COOLDOWN = 90 * S;

	// ---------------------------------------------------------------- X: Super Dash
	/** v0.14.16: 28 blocks (was 16). */
	public static final double DASH_DISTANCE = 28.0;
	public static final double DASH_SPEED = 2.0;
	/** The longest a dash may last (a safety net; 28 blocks at 2 b/t is 14 ticks). */
	public static final int DASH_MAX_TICKS = 24;
	public static final float DASH_DAMAGE = 20.0f;
	public static final double DASH_HIT_RADIUS = 1.8;
	public static final double DASH_KNOCKBACK = 2.5;
	public static final float DASH_COST = 3.0f;
	public static final int DASH_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- Shift+X: Sky Launch
	public static final double LAUNCH_HEIGHT = 40.0;
	public static final float LAUNCH_WAVE_DAMAGE = 8.0f;
	public static final double LAUNCH_WAVE_RADIUS = 4.0;
	public static final float LAUNCH_COST = 3.0f;
	public static final int LAUNCH_COOLDOWN = 6 * S;

	// ---------------------------------------------------------------- C: Super-Speed Barrage (v0.14.16)
	/** Everything in this cone in front of him takes the flurry. */
	public static final double BARRAGE_RANGE = 4.5;
	public static final double BARRAGE_CONE_DEGREES = 70.0;
	/** Hits, one every {@link #BARRAGE_HIT_INTERVAL} ticks; the last is the haymaker. */
	public static final int BARRAGE_HITS = 8;
	public static final int BARRAGE_HIT_INTERVAL = 3;
	public static final float BARRAGE_HIT_DAMAGE = 3.0f;
	public static final float BARRAGE_FINISHER_DAMAGE = 12.0f;
	public static final double BARRAGE_FINISHER_KNOCKBACK = 2.8;
	public static final double BARRAGE_FINISHER_LIFT = 0.5;
	public static final float BARRAGE_COST = 5.0f;
	public static final int BARRAGE_COOLDOWN = 6 * S;

	// ---------------------------------------------------------------- Shift+C: Meteor Strike (v0.14.16)
	/** From the ground he first shoots this high, then dives at whatever he is looking at. */
	public static final double STRIKE_RISE_HEIGHT = 12.0;
	/** The farthest spot he can aim the dive at. */
	public static final double STRIKE_AIM_RANGE = 48.0;
	public static final double STRIKE_DIVE_SPEED = 3.0;
	public static final int STRIKE_MAX_TICKS = 4 * S;
	public static final float STRIKE_DAMAGE = 28.0f;
	public static final double STRIKE_RADIUS = 6.0;
	public static final double STRIKE_KNOCKBACK = 2.2;
	public static final double STRIKE_LIFT = 1.1;
	public static final int STRIKE_FIRE_SECONDS = 4;
	public static final double STRIKE_CRATER_RADIUS = 2.5;
	public static final int STRIKE_CRATER_MAX_BLOCKS = 24;
	public static final float STRIKE_COST = 10.0f;
	public static final int STRIKE_COOLDOWN = 12 * S;

	// ---------------------------------------------------------------- V: X-Ray Vision (toggle, free)
	public static final double XRAY_RANGE = 48.0;
	/** The Night Vision it brings is topped up to this many ticks (never low enough to flicker). */
	public static final int XRAY_NIGHT_VISION_TICKS = 30 * S;

	// ---------------------------------------------------------------- Shift+V: Pick Up / Set Down (free)
	public static final double GRAB_RANGE = 6.0;
	/** v0.14.16: bigger things can be carried (was 3.0); bosses never. */
	public static final double GRAB_MAX_WIDTH = 4.5;
	/** How far in front of his eyes the held thing sits (plus half its width). */
	public static final double GRAB_HOLD_DISTANCE = 2.2;
	/** Fraction of the gap to the hold spot closed per tick (smooth follow); beyond the snap distance it jumps. */
	public static final double GRAB_FOLLOW = 0.5;
	public static final double GRAB_SNAP_DISTANCE = 8.0;
	/** How far down the gentle set-down looks for ground in front of him. */
	public static final int SET_DOWN_SEARCH_DEPTH = 64;
	/** After a set-down (or if no ground was found) the creature takes no fall damage for up to this long. */
	public static final int SET_DOWN_SAFE_TICKS = 30 * S;
	public static final double THROW_SPEED = 3.0;
	public static final float THROW_DAMAGE = 24.0f;
	public static final double THROW_IMPACT_RADIUS = 3.0;
	/** v0.14.16: free (the V moves cost nothing). Counted from the throw. */
	public static final int GRAB_COOLDOWN = 4 * S;
	/** After a gentle set-down. */
	public static final int SET_DOWN_COOLDOWN = 1 * S;

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
