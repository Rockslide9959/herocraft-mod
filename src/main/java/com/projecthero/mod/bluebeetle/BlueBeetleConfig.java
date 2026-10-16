package com.projecthero.mod.bluebeetle;

/**
 * v0.16.5: every Blue Beetle number in one place. Times are in ticks. The kit follows
 * {@code docs/V0165_POWERS_PROPOSAL.md} section 4b as the user approved it.
 */
public final class BlueBeetleConfig {
	private BlueBeetleConfig() {
	}

	// ---------------------------------------------------------------- Scarab Energy
	public static final float ENERGY_MAX = 100f;
	public static final float ENERGY_REGEN = 3f;
	/** Standing still the scarab charges twice as fast. */
	public static final float STILL_MULTIPLIER = 2f;
	public static final float FLIGHT_DRAIN = 2f;

	// ---------------------------------------------------------------- the bond / the suit
	/** v0.16.5 (user): the first transformation takes 5 seconds. */
	public static final int BOND_TICKS = 100;
	public static final int SUIT_UP_TICKS = 30;
	public static final int SUIT_DOWN_TICKS = 26;
	public static final int TOGGLE_COOLDOWN = 20;
	public static final double SUIT_ARMOR = 20.0;
	public static final double SUIT_TOUGHNESS = 8.0;
	/** v0.16.5 (user): +9 melee while suited. */
	public static final double SUIT_MELEE = 9.0;
	/** Flying speed (double-tap jump; vanilla creative flight is 0.05). */
	public static final float FLIGHT_SPEED = 0.1f;
	public static final double WARN_RANGE = 16.0;
	public static final int WARN_COOLDOWN = 600;

	// ---------------------------------------------------------------- Adaptation
	public static final int ADAPT_HITS = 3;
	public static final int ADAPT_WINDOW = 200;
	public static final int ADAPT_TICKS = 1200;
	public static final float ADAPT_FACTOR = 0.6f;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ---------------------------------------------------------------- R: Plasma Blaster / Shift+R: Mega Cannon
	/** v0.16.5 (user): 15 a bolt. */
	public static final float BLAST_DAMAGE = 15f;
	public static final float BLAST_COST = 4f;
	public static final int BLAST_INTERVAL = 7; // about 3 a second
	public static final double BLAST_SPEED = 2.2;
	public static final double BLAST_RANGE = 48.0;
	public static final int BLAST_COOLDOWN = 10;
	public static final int CANNON_CHARGE_TICKS = 40;
	public static final float CANNON_DAMAGE = 30f;
	public static final double CANNON_RANGE = 30.0;
	public static final double CANNON_RADIUS = 0.9;
	public static final float CANNON_COST = 50f;
	public static final int CANNON_COOLDOWN = 400;

	// ---------------------------------------------------------------- G: Scarab Hammer / Shift+G: Scarab Grab
	public static final float HAMMER_DAMAGE = 20f;
	/** How far in front the smash reaches, and how wide (cos of the half-angle, about 60 degrees). */
	public static final double HAMMER_REACH = 3.8;
	public static final double HAMMER_ARC = 0.5;
	/** When the hammer lands, after the press. */
	public static final int HAMMER_HIT_TICK = 6;
	public static final float HAMMER_COST = 25f;
	public static final int HAMMER_COOLDOWN = 120;
	public static final double GRAB_RANGE = 14.0;
	/** Each tentacle strikes once. */
	public static final float GRAB_DAMAGE = 10f;
	public static final int GRAB_FIRST_HIT = 6;
	public static final int GRAB_SECOND_HIT = 10;
	public static final int GRAB_TICKS = 18;
	public static final float GRAB_COST = 25f;
	public static final int GRAB_COOLDOWN = 200;

	// ---------------------------------------------------------------- X: Scarab Swarm / Shift+X: Bubble Prison
	public static final int SWARM_COUNT = 3;
	public static final int SWARM_TICKS = 200;
	public static final float SWARM_DAMAGE = 3f;
	public static final float SWARM_COST = 35f;
	public static final int SWARM_COOLDOWN = 500;
	public static final double BUBBLE_RANGE = 24.0;
	public static final int BUBBLE_TICKS = 100;
	public static final float BUBBLE_COST = 30f;
	public static final int BUBBLE_COOLDOWN = 360;

	// ---------------------------------------------------------------- Z: Plasma Spikes / Shift+Z: Shield Pulse
	public static final int SPIKE_COUNT = 14;
	/** The first spike, blocks in front of him; one more every block after it. */
	public static final double SPIKE_FIRST = 1.5;
	public static final int SPIKE_SLAM_TICK = 5;
	public static final float SPIKE_DAMAGE = 18f;
	public static final double SPIKE_RADIUS = 1.1;
	public static final float SPIKE_COST = 30f;
	public static final int SPIKE_COOLDOWN = 160;
	/** How long a spike stands (it rises, holds, sinks), for the client and the session. */
	public static final int SPIKE_LIFE = 20;
	public static final double PULSE_RADIUS = 6.0;
	public static final float PULSE_COST = 30f;
	public static final int PULSE_COOLDOWN = 240;

	// ---------------------------------------------------------------- V: Energy Shield / Shift+V: Camouflage
	public static final float SHIELD_COST_PER_SECOND = 3f;
	/** cos of the half-angle the shield covers in front of him (about 70 degrees). */
	public static final double SHIELD_ARC = 0.34;
	public static final float CAMO_COST = 30f;
	/** Starts when the camouflage breaks. */
	public static final int CAMO_COOLDOWN = 300;

	// ---------------------------------------------------------------- C: Energy Blade / Shift+C: Khaji-Da Unleashed
	/** v0.16.5 (user): +7 melee on top of the suit while the blade is out. */
	public static final double BLADE_MELEE = 7.0;
	public static final float BLADE_COST = 10f;
	public static final int BLADE_COOLDOWN = 20;
	public static final int UNLEASH_TICKS = 300;
	public static final float UNLEASH_DAMAGE = 1.5f;
	public static final float UNLEASH_COOLDOWN_FACTOR = 0.5f;
	public static final int UNLEASH_AFTER_TICKS = 600;
	public static final int UNLEASH_COOLDOWN = 2400;
}
