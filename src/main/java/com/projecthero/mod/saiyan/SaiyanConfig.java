package com.projecthero.mod.saiyan;

/**
 * v0.17.3 Saiyan revamp: every number of the Saiyan core. Times in ticks. The moves' numbers live in {@code saiyan.kit}.
 *
 * <pre>
 *   always (while he has the power)     +20 max health (40), +30% speed, +7 melee, +30% attack speed, +30% knockback resistance,
 *                                       jump 2 blocks (+0.14 jump strength, +1 safe fall), Regeneration I (1 HP / 2.5 s),
 *                                       food exhaustion x1/1.3 (saturation and hunger last 30% longer)
 *   Ki                                  0..100, +1 a second (not while flying), V charge +12 a second; 3 melee hits in a row +5
 *   Zenkai Boost                        below 6 HP for 10 s: heal 6, +50% damage for 60 s; 5 min cooldown from the trigger
 *   stronger enemy (>= 1.5x his max     -10% damage taken, +10% Ki regen, +10% melee damage, while that enemy hit him or was hit
 *   health, or a boss)                  by him in the last 10 s
 *   Rage                                0..100: +2 per HP lost, +1 per hit landed (at most every 5 ticks), x1.5 below half
 *                                       health; decays 0.5 a second after 15 s out of combat; does not fill in Super Saiyan
 *   Super Saiyan (H at 100 Rage)        3:00 exactly; +35% damage, +20% speed, +4 armour, Ki regen x2; Rage empties;
 *                                       after: a 60 s lockout and Rage must refill from 0
 *   Kaioken (Shift + hold V 3 s)        +40% damage, +25% speed, +25% attack speed; -1 HP every 1.5 s, ends at 2 HP; 30 s
 *                                       cooldown after it ends; not in Super Saiyan (transforming ends it)
 *   Flight (double-tap jump)            0.5 Ki a second, no Ki regen while airborne
 * </pre>
 * The damage bonuses add up (SSJ + Zenkai = +85%) and apply to every hit the Saiyan deals (melee and the kit's Ki moves) in
 * {@link SaiyanDamage}; the stronger-enemy +10% applies to melee only.
 */
public final class SaiyanConfig {
	private static final int S = 20;
	private static final int MIN = 60 * S;

	// ---------------------------------------------------------------- passives
	public static final double HEALTH_BONUS = 20.0;
	public static final double SPEED_BONUS = 0.30;
	public static final double MELEE_BONUS = 7.0;
	public static final double ATTACK_SPEED_BONUS = 0.30;
	public static final double KNOCKBACK_RESIST = 0.30;
	/** Vanilla 0.42 jumps 1.25 blocks; 0.56 jumps 2.1. */
	public static final double JUMP_BONUS = 0.14;
	public static final double SAFE_FALL_BONUS = 1.0;
	/** Regeneration I: 1 HP every 50 ticks. */
	public static final float REGEN_AMOUNT = 1.0f;
	public static final int REGEN_INTERVAL = 50;
	/** Food exhaustion is divided by this (saturation lasts 30% longer). */
	public static final float SATURATION_STRETCH = 1.3f;

	// ---------------------------------------------------------------- Ki
	public static final float KI_MAX = 100f;
	public static final float KI_REGEN_PER_TICK = 1f / S;
	/** V: Ki a tick while charging (12 a second, empty to full in about 8 s). */
	public static final float KI_CHARGE_PER_TICK = 12f / S;
	/** Charging slows you to a walk-in-place (movement x0.3). */
	public static final double CHARGE_SLOW = -0.7;
	/** A V press let go within this many ticks is a tap. */
	public static final int TAP_TICKS = 6;
	/** Three melee hits in a row (each within this many ticks of the last) give {@link #STREAK_KI}. */
	public static final int STREAK_HITS = 3;
	public static final int STREAK_GAP = 3 * S;
	public static final float STREAK_KI = 5f;

	// ---------------------------------------------------------------- Zenkai Boost
	public static final float ZENKAI_HEALTH = 6f;
	public static final int ZENKAI_LOW_TICKS = 10 * S;
	public static final float ZENKAI_HEAL = 6f;
	public static final int ZENKAI_TICKS = MIN;
	public static final int ZENKAI_COOLDOWN = 5 * MIN;
	public static final float ZENKAI_DAMAGE = 0.50f;

	// ---------------------------------------------------------------- a stronger enemy
	public static final float STRONG_HEALTH_RATIO = 1.5f;
	public static final int STRONG_WINDOW = 10 * S;
	public static final float STRONG_RESIST = 0.10f;
	public static final float STRONG_KI_REGEN = 0.10f;
	public static final float STRONG_MELEE = 0.10f;

	// ---------------------------------------------------------------- Rage
	public static final float RAGE_MAX = 100f;
	public static final float RAGE_PER_HP_LOST = 2f;
	public static final float RAGE_PER_HIT = 1f;
	public static final int RAGE_HIT_GAP = 5;
	public static final float RAGE_LOW_HEALTH_MULT = 1.5f;
	public static final int RAGE_IDLE_TICKS = 15 * S;
	public static final float RAGE_DECAY_PER_TICK = 0.5f / S;

	// ---------------------------------------------------------------- Super Saiyan
	public static final int SSJ_TICKS = 3 * MIN;
	public static final float SSJ_DAMAGE = 0.35f;
	public static final double SSJ_SPEED = 0.20;
	public static final double SSJ_ARMOR = 4.0;
	public static final float SSJ_KI_REGEN_MULT = 2f;
	public static final int SSJ_LOCKOUT = MIN;
	/** How long the transformation burst plays (the client's animation). */
	public static final int TRANSFORM_BURST_TICKS = 16;

	// ---------------------------------------------------------------- Kaioken
	public static final int KAIOKEN_WINDUP = 3 * S;
	public static final float KAIOKEN_DAMAGE = 0.40f;
	public static final double KAIOKEN_SPEED = 0.25;
	public static final double KAIOKEN_ATTACK_SPEED = 0.25;
	public static final float KAIOKEN_DRAIN = 1f;
	public static final int KAIOKEN_DRAIN_EVERY = 30;
	public static final float KAIOKEN_FLOOR = 2f;
	public static final int KAIOKEN_COOLDOWN = 30 * S;

	// ---------------------------------------------------------------- flight
	public static final float KI_FLIGHT_PER_TICK = 0.5f / S;
	/** Directional flight, blocks a second (x1.2 in Super Saiyan). */
	public static final double FLIGHT_CRUISE_BPS = 18.0;
	public static final double FLIGHT_SPRINT_BPS = 36.0;
	public static final double FLIGHT_MAX_BPS = 55.0;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 10;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	/** How often the server re-sends the bars while they only tick (structural changes go at once). */
	public static final int SYNC_INTERVAL = 5;

	private SaiyanConfig() {
	}
}
