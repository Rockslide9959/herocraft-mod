package com.projecthero.mod.punisher;

/**
 * Every tunable for the Punisher Hero-Tier power in one place (spec section 36). Durations are in
 * ticks (20/sec) unless the name says otherwise.
 */
public final class PunisherConfig {
	private PunisherConfig() {
	}

	// ---------------- passives (weapon proficiency) ----------------

	/** Recoil growth multiplier while a Punisher fires (reduced recoil). */
	public static final float RECOIL_FACTOR = 0.6f;
	/** Spread multiplier -- ballistic expertise, slightly better than a normal shooter. */
	public static final float SPREAD_FACTOR_HIP = 0.9f;
	public static final float SPREAD_FACTOR_ADS = 0.85f;
	/** Reload duration multiplier (15% faster). Superseded, not stacked, by Adrenaline. */
	public static final float RELOAD_FACTOR = 0.85f;

	// ---------------- No Mercy (low-health execution bonus) ----------------

	/** Below this fraction of max health a non-boss hostile takes bonus firearm damage. */
	public static final float NO_MERCY_HEALTH_FRACTION = 0.15f;
	public static final float NO_MERCY_BONUS_NONBOSS = 0.25f;
	public static final float NO_MERCY_BONUS_BOSS = 0.10f;
	/** A target at or above this max health is treated as a boss. */
	public static final float BOSS_MAX_HEALTH = 150f;

	// ---------------- Frag Grenade (G) ----------------

	public static final int GRENADE_COOLDOWN_TICKS = 12 * 20;
	public static final int GRENADE_FUSE_TICKS = 3 * 20;
	public static final int GRENADE_MAX_COOK_TICKS = 3 * 20;
	public static final float GRENADE_DAMAGE = 12f;
	public static final double GRENADE_RADIUS = 4.5;
	public static final float GRENADE_BLOCK_POWER = 1.6f; // small -- must not level a base
	public static final float GRENADE_THROW_SPEED = 1.1f;

	// ---------------- Tactical Roll (X) ----------------

	public static final int ROLL_COOLDOWN_TICKS = 4 * 20;
	public static final int ROLL_DURATION_TICKS = 8;
	public static final double ROLL_SPEED = 0.62;         // blocks/tick during the roll
	public static final int ROLL_IFRAME_TICKS = 4;        // brief damage reduction window (mid-roll)
	public static final float ROLL_DAMAGE_REDUCTION = 0.4f;

	// ---------------- Suppressive Fire (Z) ----------------

	public static final int SUPPRESSIVE_COOLDOWN_TICKS = 20 * 20;
	public static final int SUPPRESSIVE_DURATION_TICKS = 8 * 20;   // v0.9.22: 8 s (was 4)
	public static final float SUPPRESSIVE_FIRE_RATE_FACTOR = 0.7f;   // faster (shorter interval)
	public static final float SUPPRESSIVE_RECOIL_FACTOR = 0.3f;
	public static final float SUPPRESSIVE_SPREAD_FACTOR = 0.5f;
	public static final float SUPPRESSIVE_SELF_SLOW = 0.25f;         // -25% move speed while active
	public static final int SUPPRESSIVE_SLOW_TICKS = 40;             // Slowness on things you hit
	public static final int SUPPRESSIVE_SLOW_AMP = 1;

	// ---------------- Adrenaline (V) ----------------

	/** v0.9.4: 30 s cooldown (was 45), 20 s duration (was 8). */
	public static final int ADRENALINE_COOLDOWN_TICKS = 30 * 20;
	public static final int ADRENALINE_DURATION_TICKS = 20 * 20;
	public static final float ADRENALINE_RELOAD_FACTOR = 0.75f;      // supersedes RELOAD_FACTOR
	public static final float ADRENALINE_DAMAGE_BONUS = 0.15f;
	/** v0.9.4: the buff set applied on activation. Regeneration V is a short burst; the rest run the
	 *  full duration. Speed II replaces the old +20% movement-speed attribute modifier. */
	public static final int ADRENALINE_REGEN_TICKS = 3 * 20;
	public static final int ADRENALINE_REGEN_AMP = 4;               // Regeneration V
	public static final int ADRENALINE_RESISTANCE_AMP = 1;          // Resistance II
	public static final int ADRENALINE_HASTE_AMP = 1;               // Haste II
	public static final int ADRENALINE_SPEED_AMP = 1;               // Speed II
	/** Client game audio is dulled by this fraction while Adrenaline is active (tunnel-vision feel). */
	public static final float ADRENALINE_AUDIO_MUFFLE = 0.30f;
	/** The crash: Nausea I for this long, once, the moment Adrenaline wears off. */
	public static final int ADRENALINE_CRASH_NAUSEA_TICKS = 10 * 20;

	// ---------------- Explosive Charge / C4 (C) ----------------

	public static final int C4_MAX_ACTIVE = 3;
	public static final int C4_COOLDOWN_TICKS = 20;   // short -- the limit is the max-active count
	public static final float C4_DAMAGE = 20f;
	public static final double C4_RADIUS = 5.5;
	public static final float C4_BLOCK_POWER = 3.0f;  // moderate terrain damage, not obliteration
	public static final double C4_PLACE_RANGE = 5.0;

	// ---------------- Vigilante Training ----------------

	public static final int TRAIN_KILLS = 25;
	public static final int TRAIN_RANGED_KILLS = 10;
	public static final int TRAIN_HEADSHOTS = 5;
}
