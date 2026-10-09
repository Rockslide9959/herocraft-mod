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
	/** Reload duration multiplier (15% faster). */
	public static final float RELOAD_FACTOR = 0.85f;

	// ---------------- No Mercy (low-health execution bonus) ----------------

	/** Below this fraction of max health a non-boss hostile takes bonus firearm damage. */
	public static final float NO_MERCY_HEALTH_FRACTION = 0.15f;
	public static final float NO_MERCY_BONUS_NONBOSS = 0.25f;
	public static final float NO_MERCY_BONUS_BOSS = 0.10f;
	/** A target at or above this max health is treated as a boss. */
	public static final float BOSS_MAX_HEALTH = 150f;

	// ---------------- v0.15.18 kit: R / G / Z / X / C (V = weapon abilities, N = Tactical Satchel) ----------------
	// Every Shift move has its own cooldown, separate from the plain key's.

	// R -- Target Designation: mark the enemy you aim at; it takes +30% damage from you while marked (one mark at a time)
	public static final double MARK_RANGE = 48.0;
	public static final int MARK_DURATION_TICKS = 30 * 20;
	public static final float MARK_DAMAGE_BONUS = 0.30f;
	public static final int MARK_COOLDOWN_TICKS = 5 * 20;

	// Shift+R -- Threat Assessment: every living thing within 18 blocks glows for you alone
	public static final double THREAT_RADIUS = 18.0;
	public static final int THREAT_DURATION_TICKS = 10 * 20;
	public static final int THREAT_COOLDOWN_TICKS = 12 * 20;

	// G -- Brutal Strike: a close-quarters blow that stuns
	public static final double STRIKE_RANGE = 5.0;
	public static final float BRUTAL_STRIKE_DAMAGE = 18f;
	public static final int BRUTAL_STRIKE_STUN_TICKS = 2 * 20;
	public static final int BRUTAL_STRIKE_COOLDOWN_TICKS = 2 * 20;

	// Shift+G -- Breach Kick: a kick that throws the target about 10 blocks and stuns it
	public static final float BREACH_KICK_DAMAGE = 25f;
	/** Launch speed (blocks/tick) -- with air drag this carries a mob about 10 blocks. */
	public static final double BREACH_KICK_SPEED = 1.35;
	public static final double BREACH_KICK_LIFT = 0.42;
	public static final int BREACH_KICK_STUN_TICKS = 5 * 20;
	public static final int BREACH_KICK_COOLDOWN_TICKS = 5 * 20;

	// Z -- Frag Grenade (hold to cook, release to throw)
	public static final int GRENADE_COOLDOWN_TICKS = 12 * 20;
	public static final int GRENADE_FUSE_TICKS = 3 * 20;
	public static final int GRENADE_MAX_COOK_TICKS = 3 * 20;
	public static final float GRENADE_DAMAGE = 12f;
	public static final double GRENADE_RADIUS = 4.5;
	public static final float GRENADE_BLOCK_POWER = 1.6f; // small -- must not level a base
	public static final float GRENADE_THROW_SPEED = 1.1f;

	// Shift+Z (hold 5 s) -- Warzone: an artillery barrage on the block you aim at
	public static final int WARZONE_CHARGE_TICKS = 5 * 20;
	public static final double WARZONE_RANGE = 100.0;
	public static final double WARZONE_RADIUS = 15.0;
	public static final int WARZONE_DURATION_TICKS = 10 * 20;
	/** Missiles per second, spread at random over the marked area. */
	public static final double WARZONE_MISSILES_PER_SECOND = 3.0;
	public static final double WARZONE_BLAST_RADIUS = 5.0;
	public static final float WARZONE_DAMAGE = 30f;
	/** Fraction of the damage still dealt at the blast's edge (linear falloff from the centre). */
	public static final float WARZONE_EDGE_DAMAGE = 0.4f;
	public static final double WARZONE_DROP_HEIGHT = 45.0;
	public static final double WARZONE_FALL_SPEED = 2.5;   // blocks per tick
	public static final int WARZONE_COOLDOWN_TICKS = 120 * 20;

	// X -- Tactical Roll
	public static final int ROLL_COOLDOWN_TICKS = 4 * 20;
	public static final int ROLL_DURATION_TICKS = 11;     // v0.15.16: was 8 -- a longer dive (~7 blocks, was ~5)
	public static final double ROLL_SPEED = 0.68;         // blocks/tick during the roll (v0.15.16: was 0.62)
	public static final int ROLL_IFRAME_TICKS = 4;        // brief damage reduction window (mid-roll)
	public static final float ROLL_DAMAGE_REDUCTION = 0.4f;

	// Shift+X -- Tactical Advance: Speed IV (no cooldown was specified; 60 s chosen)
	public static final int ADVANCE_DURATION_TICKS = 30 * 20;
	public static final int ADVANCE_SPEED_AMP = 3;        // Speed IV
	public static final int ADVANCE_COOLDOWN_TICKS = 60 * 20;

	// C -- Smoke Screen: mobs inside lose their target and cannot pick a new one; other players inside are blinded
	public static final double SMOKE_RADIUS = 5.0;
	public static final int SMOKE_DURATION_TICKS = 6 * 20;
	public static final int SMOKE_COOLDOWN_TICKS = 12 * 20;

	// Shift+C -- Flashbang: blinds, slows and confuses everything near it
	public static final int FLASHBANG_FUSE_TICKS = 30;
	public static final double FLASHBANG_RADIUS = 6.0;
	public static final int FLASHBANG_EFFECT_TICKS = 8 * 20;
	public static final int FLASHBANG_SLOW_AMP = 1;       // Slowness II
	/** Flashed mobs drop their target and cannot pick one again for this long. */
	public static final int FLASHBANG_NO_TARGET_TICKS = 3 * 20;
	public static final float FLASHBANG_THROW_SPEED = 1.0f;
	public static final int FLASHBANG_COOLDOWN_TICKS = 12 * 20;

	/**
	 * v0.15.18: Adrenaline is no longer an ability. Only the dormant client stab animation ({@code GunAnim},
	 * {@code GunFirstPerson}, {@code PunisherGunPose}) still reads this -- remove it together with that animation.
	 */
	public static final int ADRENALINE_STAB_TICKS = 14;

	// ---------------- Vigilante Training ----------------

	public static final int TRAIN_KILLS = 25;
	public static final int TRAIN_RANGED_KILLS = 10;
	public static final int TRAIN_HEADSHOTS = 5;
}
