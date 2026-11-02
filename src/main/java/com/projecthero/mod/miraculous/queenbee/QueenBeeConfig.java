package com.projecthero.mod.miraculous.queenbee;

/**
 * v0.17.2: every Queen Bee number (the spinning top, the twelve moves, Venom, the glide). Ticks unless the name says
 * otherwise. The shared transformed stats come from the core ({@code MiraculousStats}) and are not repeated. Damage
 * follows the v0.17.2 scale: quick 5-8, normal 12-17, big 17-20, ultimate 40-70.
 */
public final class QueenBeeConfig {
	private QueenBeeConfig() {
	}

	// ---------------------------------------------------------------- the ability ids
	public static final String STRIKE = "queen_bee_top_strike";
	public static final String THROW = "queen_bee_top_throw";
	public static final String SHIELD = "queen_bee_spinning_shield";
	public static final String TANGLE = "queen_bee_top_tangle";
	public static final String SWING = "queen_bee_top_swing";
	public static final String COPTER = "queen_bee_top_copter";
	public static final String DIVE = "queen_bee_stinger_dive";
	public static final String VENOM = "queen_bee_venom";
	public static final String HIVE = "queen_bee_hive_sense";
	public static final String COMMAND = "queen_bee_queens_command";
	public static final String STING = "queen_bee_sting";
	public static final String VOLLEY = "queen_bee_stinger_volley";

	// ---------------------------------------------------------------- shared
	/** Bosses (this much max health, or {@code TitanCombat.isBoss}) take at most this much of their max health a hit and are never rooted or paralysed. */
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;
	public static final double FRONT_DOT = 0.4;

	// ---------------------------------------------------------------- passive: the buzzing glide
	/** Holding jump in the air, she falls no faster than this (blocks a tick; her client caps it). */
	public static final double GLIDE_FALL_SPEED = 0.12;
	/** The server forgives a fall while she drops slower than this a tick. */
	public static final double GLIDE_SAFE_DROP = 0.2;

	// ---------------------------------------------------------------- R: Top Strike / Shift+R: Top Throw
	public static final int STRIKE_HITS = 3;
	public static final int STRIKE_WINDUP = 3;
	public static final int STRIKE_GAP = 5;
	public static final int STRIKE_WINDOW = 20;
	public static final int STRIKE_SWING_TICKS = 10;
	public static final float STRIKE_DAMAGE = 7f;
	public static final float STRIKE_FINISHER_DAMAGE = 9f;
	public static final double STRIKE_REACH = 5.0;
	public static final double STRIKE_CONE = 0.5;
	public static final double STRIKE_KNOCKBACK = 0.35;
	public static final double STRIKE_FINISHER_KNOCKBACK = 1.1;
	public static final double STRIKE_FINISHER_LIFT = 0.2;
	public static final int STRIKE_COOLDOWN = 30;
	public static final double THROW_RANGE = 20.0;
	public static final float THROW_DAMAGE = 14f;
	/** Ricochets: up to this many creatures, each next one within {@link #THROW_HOP_RANGE} of the last. */
	public static final int THROW_TARGETS = 3;
	public static final double THROW_HOP_RANGE = 8.0;
	/** The top's speed in flight (blocks a tick), out and back. */
	public static final double THROW_SPEED = 1.6;
	/** It gives up and comes home after this long. */
	public static final int THROW_TICKS = 60;
	public static final int THROW_COOLDOWN = 120;

	// ---------------------------------------------------------------- G: Spinning Shield / Shift+G: Top Tangle
	public static final int SHIELD_TICKS = 80;
	public static final double SHIELD_RADIUS = 3.0;
	public static final float SHIELD_DAMAGE = 4f;
	public static final int SHIELD_EVERY = 10;
	public static final int SHIELD_COOLDOWN = 200;
	public static final double TANGLE_RANGE = 14.0;
	public static final double TANGLE_ASSIST_DEGREES = 7.0;
	public static final float TANGLE_DAMAGE = 6f;
	public static final int TANGLE_TICKS = 60;
	public static final int TANGLE_COOLDOWN = 200;

	// ---------------------------------------------------------------- X: Top Swing / Shift+X: Top Copter
	public static final double SWING_RANGE = 30.0;
	public static final int SWING_TICKS = 45;
	public static final double SWING_SPEED = 1.5;
	public static final double SWING_PULL = 0.28;
	public static final double SWING_ARRIVE = 2.2;
	public static final int SWING_COOLDOWN = 60;
	public static final int COPTER_TICKS = 15 * 20;
	/** Vanilla's flying speed is 0.05; the rotor is a little quicker. */
	public static final float COPTER_FLY_SPEED = 0.07f;
	/** Touching down this long after take-off ends the flight. */
	public static final int COPTER_GROUND_GRACE = 10;
	/** Starts when the flight ends. */
	public static final int COPTER_COOLDOWN = 160;

	// ---------------------------------------------------------------- Z: Stinger Dive / Shift+Z: VENOM
	public static final double DIVE_LEAP_UP = 1.15;
	public static final double DIVE_LEAP_FORWARD = 0.5;
	/** The dive starts at the top of the leap, or after this long at the latest. */
	public static final int DIVE_RISE_TICKS = 10;
	public static final double DIVE_SPEED = 2.2;
	/** Never lands: given up after this long. */
	public static final int DIVE_MAX_TICKS = 80;
	public static final float DIVE_DAMAGE = 17f;
	public static final double DIVE_RADIUS = 4.0;
	public static final double DIVE_LIFT = 0.6;
	public static final double DIVE_KNOCKBACK = 0.6;
	public static final int DIVE_COOLDOWN = 180;
	/** Venom stays armed (the top glowing) this long, waiting for her next hit. */
	public static final int VENOM_ARMED_TICKS = 10 * 20;
	/** Paralysed: rooted and unable to act this long. */
	public static final int VENOM_PARALYSE_TICKS = 10 * 20;
	public static final float VENOM_DAMAGE = 40f;
	/** A boss is only slowed (Slowness III) this long. */
	public static final int VENOM_BOSS_SLOW_TICKS = 3 * 20;
	/** Shift+Z again while armed: a touch this far in front of her. */
	public static final double VENOM_TOUCH_REACH = 3.5;
	public static final int VENOM_COOLDOWN = 2400;

	// ---------------------------------------------------------------- V: Hive Sense (toggle) / Shift+V: Queen's Command
	public static final double HIVE_RANGE = 32.0;
	public static final int HIVE_COOLDOWN = 20;
	public static final double COMMAND_RADIUS = 10.0;
	/** Hostile mobs drop their target and may not pick one this long. */
	public static final int COMMAND_TICKS = 100;
	/** Her allies (tamed pets, other players she may not harm) get Strength I this long. */
	public static final int COMMAND_STRENGTH_TICKS = 10 * 20;
	public static final int COMMAND_COOLDOWN = 400;

	// ---------------------------------------------------------------- C: Sting / Shift+C: Stinger Volley
	public static final double STING_LUNGE = 1.25;
	/** The jab lands on the first creature within this of her during the lunge. */
	public static final double STING_REACH = 2.6;
	public static final int STING_TICKS = 8;
	public static final float STING_DAMAGE = 13f;
	public static final int STING_POISON_TICKS = 60;
	public static final int STING_COOLDOWN = 80;
	public static final int VOLLEY_SHOTS = 6;
	public static final int VOLLEY_EVERY = 4;
	public static final double VOLLEY_RANGE = 20.0;
	public static final float VOLLEY_DAMAGE = 6f;
	public static final int VOLLEY_POISON_TICKS = 40;
	public static final int VOLLEY_COOLDOWN = 240;
}
