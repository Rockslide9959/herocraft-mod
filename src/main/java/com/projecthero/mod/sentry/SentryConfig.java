package com.projecthero.mod.sentry;

/**
 * v0.17.5 Sentry &amp; the Void: every number (listed again in {@code docs/SENTRY_VOID_REFERENCE.md}). Damage is pitched at
 * Kryptonian / Thor parity (Kryptonian punch 32, Solar Flare 120): A Million Suns and Erase are the two big hitters. Ticks unless
 * named otherwise; meters are 0..100.
 */
public final class SentryConfig {
	private SentryConfig() {
	}

	// ---------------------------------------------------------------- meters
	public static final float SUN_MAX = 100f;
	public static final float VOID_MAX = 100f;
	/** Sun gained a second: direct sunlight, daytime shade, night under the sky, the dark (none). */
	public static final float SUN_DIRECT_PER_SECOND = 6.0f;
	public static final float SUN_SHADE_PER_SECOND = 2.0f;
	public static final float SUN_NIGHT_PER_SECOND = 0.5f;
	public static final float SUN_DARK_PER_SECOND = 0.0f;

	/** Void a second: fighting / standing at night, standing in the dark; falls in daylight. */
	public static final float VOID_NIGHT_PER_SECOND = 0.08f; // v0.17.5 rework: a third of 0.25
	public static final float VOID_DARK_PER_SECOND = 0.17f; // was 0.5
	public static final float VOID_DAYLIGHT_PER_SECOND = -1.0f;
	/** Holding V (Calm). */
	public static final float VOID_CALM_PER_SECOND = -6.0f;
	/** Merged with the Void (Void Form) it climbs this a second on its own (was 1.0). */
	public static final float VOID_FORM_PER_SECOND = 0.5f;
	/** Every Void gain (passive and per event) is multiplied by this while merged with the Void... */
	public static final float MERGED_VOID_GAIN = 1.5f;
	/** ...and by this while the Sentry Suit (top + trousers) is worn; the suit also multiplies the Sun gained a second by {@link #SUIT_SUN_GAIN}. */
	public static final float SUIT_VOID_GAIN = 0.6f;
	public static final float SUIT_SUN_GAIN = 1.5f;
	/** A hit of at least this much damage taken counts as heavy... */
	public static final float HEAVY_HIT = 8.0f;
	/** ...and adds this much Void per point of damage (at most {@link #HEAVY_HIT_VOID_CAP} a hit). */
	public static final float HEAVY_HIT_VOID_PER_DAMAGE = 0.13f; // was 0.4
	public static final float HEAVY_HIT_VOID_CAP = 2.0f; // was 6
	/** Void for each kill (a player: more). */
	public static final float KILL_VOID = 0.7f; // was 2
	public static final float PLAYER_KILL_VOID = 2.0f; // was 6
	/** The thresholds. */
	public static final float VOID_WHISPERS = 50f;
	public static final float VOID_MISFIRE = 75f;
	public static final float VOID_DIMMING = 90f;
	/** Above {@link #VOID_MISFIRE}: the chance a Sentry key misfires into a Void move. */
	public static final float MISFIRE_CHANCE = 0.12f;
	/** How often the meters are re-sent while they tick in place. */
	public static final int SYNC_INTERVAL = 10;

	// ---------------------------------------------------------------- the body
	/** v0.17.5 rework (user: "Make his max hp 40"): +20 on the base 20 = 40, merged with the Void too (its buffs are damage and cooldowns). */
	public static final double SENTRY_HEALTH_BONUS = 20.0;
	public static final double VOID_HEALTH_BONUS = 20.0;
	/** Damage taken is cut by this (Kryptonian: 80%). */
	public static final float SENTRY_RESIST = 0.70f;
	public static final float VOID_RESIST = 0.75f;
	public static final double SENTRY_MELEE_BONUS = 12.0;
	public static final double VOID_MELEE_BONUS = 16.0;
	public static final double SPEED_BONUS = 0.30;
	/** Sun-fed healing: 1 HP every this many ticks while Sun &gt; 0 (costs {@link #REGEN_SUN} each). */
	public static final int REGEN_INTERVAL = 40;
	public static final float REGEN_SUN = 0.5f;

	// ---------------------------------------------------------------- Sentry kit
	/** R Golden Strike: three-hit combo; the 3rd hit throws a golden shockwave. */
	public static final float STRIKE_DAMAGE = 24f;
	public static final float STRIKE_THIRD_DAMAGE = 30f;
	public static final float STRIKE_WAVE_DAMAGE = 14f;
	public static final double STRIKE_WAVE_RADIUS = 4.5;
	public static final double STRIKE_REACH = 4.5;
	public static final int STRIKE_COOLDOWN = 8;
	public static final int STRIKE_COMBO_WINDOW = 30;
	public static final float STRIKE_COST = 2f;

	/** Shift+R Sunbeam: held. */
	public static final float BEAM_DAMAGE = 6f;
	public static final int BEAM_DAMAGE_INTERVAL = 4;
	public static final double BEAM_RANGE = 32.0;
	public static final float BEAM_COST_PER_SECOND = 5f;
	public static final int BEAM_MAX_TICKS = 120;
	public static final int BEAM_COOLDOWN = 60;
	public static final float BEAM_CLASH_POWER = 1.4f;
	public static final float BEAM_VOID_PER_SECOND = 0.17f; // was 0.5

	/** G Molecular Grip: lift; release throws, hold tears apart. */
	public static final double GRIP_RANGE = 20.0;
	public static final int GRIP_TEAR_TICKS = 30;
	public static final int GRIP_MAX_TICKS = 100;
	public static final float GRIP_TEAR_DAMAGE = 55f;
	public static final float GRIP_THROW_DAMAGE = 14f;
	public static final double GRIP_THROW_SPEED = 2.4;
	public static final float GRIP_COST = 10f;
	public static final float GRIP_TEAR_VOID = 1.7f; // was 5
	public static final int GRIP_COOLDOWN = 80;

	/** Shift+G Unmake: a 3x3 patch of blocks into light (no drops). */
	public static final double UNMAKE_RANGE = 24.0;
	public static final float UNMAKE_COST = 8f;
	public static final float UNMAKE_VOID = 1f; // was 3
	public static final int UNMAKE_COOLDOWN = 40;

	/** Z Sun Dive (from the air). */
	public static final float DIVE_DAMAGE = 32f;
	public static final double DIVE_RADIUS = 5.0;
	public static final double DIVE_SPEED = 2.6;
	public static final int DIVE_MAX_TICKS = 60;
	public static final float DIVE_COST = 12f;
	public static final int DIVE_COOLDOWN = 120;

	/** Shift+Z A Million Suns: 3 s charge, then the explosion of light. */
	public static final int SUNS_CHARGE_TICKS = 60;
	public static final float SUNS_DAMAGE = 140f;
	public static final float SUNS_EDGE_DAMAGE = 70f;
	public static final double SUNS_RADIUS = 12.0;
	public static final float SUNS_COST = 60f;
	public static final float SUNS_VOID = 8f; // was 25
	public static final int SUNS_COOLDOWN = 1200;

	/** C Healing Light. */
	public static final double HEAL_RADIUS = 10.0;
	public static final float HEAL_AMOUNT = 8f;
	public static final float HEAL_COST = 20f;
	public static final float HEAL_VOID = -8f;
	public static final float HEAL_VOID_PER_ALLY = -1f;
	public static final int HEAL_COOLDOWN = 240;

	/** Shift+C Sentinel Shield. */
	public static final double SHIELD_RADIUS = 5.0;
	public static final int SHIELD_TICKS = 120;
	public static final float SHIELD_COST = 15f;
	public static final int SHIELD_COOLDOWN = 400;

	/** Flight: double-tap Space (Sentry and merged alike). X: the sonic-boom dash (regular Sentry). */
	public static final double FLIGHT_CRUISE_BPS = 22.0;
	public static final double FLIGHT_SPRINT_BPS = 44.0;
	/** A take-off this soon after a landing is ignored (vanilla double-tap can drop flight the same tick). */
	public static final int FLIGHT_TOGGLE_DEBOUNCE = 6;
	public static final double DASH_BLOCKS = 18.0;
	public static final float DASH_DAMAGE = 14f;
	public static final float DASH_COST = 5f;
	public static final int DASH_COOLDOWN = 80;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	/** Shift+V Let It Out: locked this long after the Void is thrown out of a takeover. (v0.17.5 rework: no Void gate any more, the trial replaces it.) */
	public static final int LET_OUT_LOCK_TICKS = 6000;
	/** The trial ("Hold the Light" minigame): it lasts this long on the client... */
	public static final int TRIAL_TICKS = 160;
	/** ...a win counts only this far in on the server clock (nobody honest fills the bar faster)... */
	public static final int TRIAL_MIN_WIN_TICKS = 60;
	/** ...and with no report this long after it should have ended, it counts as lost. */
	public static final int TRIAL_GRACE_TICKS = 100;
	/** A lost trial: the Void jumps by this (flat: not scaled by the suit or merging). */
	public static final float TRIAL_LOSE_VOID = 15f;
	/** Merged with the Void: every hit does this much more, and the Void moves recharge this much faster (cooldown x). */
	public static final float MERGED_DAMAGE = 1.25f;
	public static final float MERGED_COOLDOWN = 0.75f;
	/** The suit fades between the Sentry and the Void suit over this many ticks. */
	public static final int FORM_FADE_TICKS = 50;

	// ---------------------------------------------------------------- Void Form kit
	/** R Shadow Lash: a 100-degree cone. */
	public static final float LASH_DAMAGE = 20f;
	public static final double LASH_RANGE = 6.0;
	public static final int LASH_COOLDOWN = 12;
	public static final float LASH_VOID = 0.35f; // was 1

	/** Shift+R Erase. */
	public static final float ERASE_DAMAGE = 100f;
	public static final double ERASE_RANGE = 24.0;
	public static final int ERASE_COOLDOWN = 400;
	public static final float ERASE_VOID = 2f; // was 6

	/** G Grasp: drag several creatures in. */
	public static final double GRASP_RANGE = 14.0;
	public static final int GRASP_MAX = 5;
	public static final float GRASP_DAMAGE = 6f;
	public static final int GRASP_COOLDOWN = 120;
	public static final float GRASP_VOID = 0.7f; // was 2

	/** Z Void Rift: a black hole for 4 s. */
	public static final double RIFT_RANGE = 24.0;
	public static final double RIFT_RADIUS = 7.0;
	public static final int RIFT_TICKS = 80;
	public static final float RIFT_DAMAGE = 6f;
	public static final int RIFT_DAMAGE_INTERVAL = 10;
	public static final int RIFT_COOLDOWN = 360;
	public static final float RIFT_VOID = 1.7f; // was 5

	/** Shift+Z Nightfall: a dome of darkness. */
	public static final double NIGHTFALL_RADIUS = 10.0;
	public static final int NIGHTFALL_TICKS = 200;
	public static final int NIGHTFALL_COOLDOWN = 800;
	public static final float NIGHTFALL_VOID = 2f; // was 6

	/** X Shadow Step: to a dark spot you look at. */
	public static final double STEP_RANGE = 40.0;
	/** The spot's light (block or sky, whichever is brighter after the time of day) must be at most this. */
	public static final int STEP_MAX_LIGHT = 7;
	public static final int STEP_COOLDOWN = 60;
	public static final float STEP_VOID = 0.35f; // was 1

	/** Shift+V (or V) back to the Sentry while merged: only below this. */
	public static final float RETURN_MAX_VOID = 90f;

	// ---------------------------------------------------------------- the takeover
	/** Nothing for the first 30 s. */
	public static final int MIND_DELAY_TICKS = 600;
	/** A failed room adds 20 s. */
	public static final int ROOM_FAIL_TICKS = 400;
	/** A room can't honestly be won faster than this (server check). */
	public static final int ROOM_MIN_TICKS = 80;
	public static final int ROOMS = 3;
	/** Outside help: grip drained per right-click tick of a helper (held use repeats every 4 ticks). */
	public static final float HELP_PER_CLICK = 2.0f;
	/** Grip creeps back a second (day / night). */
	public static final float GRIP_REGEN_DAY = 0.25f;
	public static final float GRIP_REGEN_NIGHT = 1.0f;
	/** When the grip breaks a room open it comes back to this. */
	public static final float GRIP_AFTER_BREAK = 60f;
	/** The body flies at this (blocks a tick) and hunts within this. */
	public static final double TAKEOVER_SPEED = 0.55;
	public static final double TAKEOVER_HUNT_RANGE = 24.0;
	public static final double TAKEOVER_WANDER_RANGE = 128.0;
	/** A kill is absorbed: it heals the body this much. */
	public static final float ABSORB_HEAL = 4f;
	/** Void after the takeover: thrown out, death. */
	public static final float VOID_AFTER_WIN = 40f;
	public static final float VOID_AFTER_DEATH = 60f;
	public static final int KNEEL_TICKS = 60;

	// ---------------------------------------------------------------- the void matter
	/** The ring starts this wide and grows this much a second, up to the max. */
	public static final double MATTER_START_RADIUS = 3.0;
	public static final double MATTER_GROWTH_PER_SECOND = 0.25;
	public static final double MATTER_MAX_RADIUS = 16.0;
	/** Blocks swallowed per tick at most. */
	public static final int MATTER_BLOCKS_PER_TICK = 8;
	/** A void matter block turns to nothing after this. */
	public static final int MATTER_DISSOLVE_TICKS = 30;
	/** The eaten world comes back this long after control returns... */
	public static final int RESTORE_DELAY_TICKS = 1200;
	/** ...this many blocks a tick. */
	public static final int RESTORE_PER_TICK = 24;

	/** A burnt shadow on the ground fades after this. */
	public static final int SHADOW_TICKS = 2400;

	// ---------------------------------------------------------------- the Sentry Suit
	/** The suit mends 1 durability every this many ticks, worn or anywhere in the inventory. */
	public static final int SUIT_REPAIR_INTERVAL = 60;
}
