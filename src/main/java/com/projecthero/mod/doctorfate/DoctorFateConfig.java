package com.projecthero.mod.doctorfate;

/**
 * v0.17.3 Doctor Fate: every number of the power in one place ({@code docs/FATE_SHAZAM_DESIGN.md}, the Doctor Fate section).
 * Times are ticks, damage is health points (2 = one heart), Dominion is 0..100.
 */
public final class DoctorFateConfig {
	private DoctorFateConfig() {
	}

	// ---- the helmet
	/** Wearing the helmet: 40 health (+20), 30% damage resistance. */
	public static final double HELMET_HEALTH_BONUS = 20.0;
	public static final float HELMET_RESIST = 0.30f;
	/** V: the helmet takes 1 s to go on or come off. */
	public static final int HELMET_ANIM_TICKS = 20;
	/** After a Possession the helmet can't go back on for 60 s. */
	public static final int LOCKOUT_TICKS = 60 * 20;

	// ---- Dominion
	public static final float DOMINION_MAX = 100f;
	/** Wearing the helmet: +0.15 a second (about 11 minutes from 0 to 100 on its own). */
	public static final float WEAR_GAIN_PER_TICK = 0.15f / 20f;
	/** Helmet off: -0.75 a second. */
	public static final float OFF_DRAIN_PER_TICK = 0.75f / 20f;
	public static final float TIER_OVERRIDE = 25f;
	public static final float TIER_PULL = 50f;
	public static final float TIER_HOLD = 75f;
	/** Override: 1 spell in 6 is Nabu's. */
	public static final int OVERRIDE_ONE_IN = 6;
	/** Pull: spells cost 10% less Dominion. */
	public static final float PULL_DISCOUNT = 0.9f;
	/** Pull: how far (deg a tick, at most) the aim drifts toward the nearest creature of Chaos within this many blocks. */
	public static final float PULL_DEG_PER_TICK = 0.55f;
	public static final double PULL_RANGE = 20.0;
	/** Taking hold: each spell also costs this much health (twice for spells worth 8+ Dominion). */
	public static final float HOLD_HEALTH_COST = 1f;
	/** Whispers: one every 40-80 s while the helmet is on. */
	public static final int WHISPER_MIN_TICKS = 40 * 20;
	public static final int WHISPER_SPREAD_TICKS = 40 * 20;
	public static final int WHISPER_LINES = 8;

	// ---- Possession
	public static final int POSSESSION_TICKS = 15 * 20;
	/** Each Space press while possessed takes this much off (0.5 s). */
	public static final int STRUGGLE_TICKS = 10;
	/** At most one struggle press counts every this many ticks (key repeat can't skip it). */
	public static final int STRUGGLE_MIN_GAP = 2;
	/** After a Possession, Dominion falls to this (Nabu is sated). */
	public static final float AFTER_POSSESSION_DOMINION = 70f;
	public static final double POSSESSION_RANGE = 32.0;
	/** Nabu casts every this many ticks, at this damage multiplier ("huge damage"). */
	public static final int POSSESSION_CAST_EVERY = 16;
	public static final float POSSESSION_DAMAGE_MULT = 2.0f;

	// ---- spells (helmet on): damage, Dominion, cooldown
	public static final float BOLT_DAMAGE = 10f;
	public static final float BOLT_DOMINION = 1f;
	public static final int BOLT_COOLDOWN = 10;
	public static final double BOLT_RANGE = 32.0;
	public static final float CHAINS_DAMAGE = 6f;
	public static final float CHAINS_DOMINION = 3f;
	public static final int CHAINS_COOLDOWN = 5 * 20;
	public static final int CHAINS_BIND_TICKS = 3 * 20;
	public static final int CHAINS_BIND_BOSS_TICKS = 20;
	/** Ankh Shield: +1 Dominion a second while held. */
	public static final float SHIELD_DOMINION_PER_TICK = 1f / 20f;
	public static final int SHIELD_COOLDOWN = 20;
	public static final float BANISH_DOMINION = 12f;
	public static final int BANISH_COOLDOWN = 20 * 20;
	public static final double BANISH_THROW = 40.0;
	/** A creature at least this strong (max health) is thrown, not banished. */
	public static final float BANISH_STRONG_HEALTH = 60f;
	public static final float GLYPH_DAMAGE = 4f;
	public static final float GLYPH_DOMINION = 8f;
	public static final int GLYPH_COOLDOWN = 12 * 20;
	public static final int GLYPH_TICKS = 3 * 20;
	public static final int GLYPH_HIT_EVERY = 10;
	public static final double GLYPH_RADIUS = 7.0;
	public static final int AVATAR_TICKS = 12 * 20;
	public static final int AVATAR_COOLDOWN = 3 * 60 * 20;
	public static final float AVATAR_SLAM_DAMAGE = 40f;
	public static final double AVATAR_SLAM_RADIUS = 8.0;
	public static final int AVATAR_SLAM_GAP = 12;
	public static final double AVATAR_PROJECTILE_RADIUS = 10.0;
	/** The Avatar of Order (and the Forbidden spell bargain) leaves Dominion at least here. */
	public static final float FORBIDDEN_DOMINION = 90f;
	public static final double STEP_DISTANCE = 12.0;
	public static final int STEP_COOLDOWN = 3 * 20;
	public static final float GATE_DOMINION = 5f;
	public static final int GATE_COOLDOWN = 8 * 20;
	public static final double GATE_LIFT = 15.0;
	public static final float BLAST_DAMAGE = 14f;
	public static final float BLAST_DOMINION = 4f;
	public static final int BLAST_COOLDOWN = 5 * 20;
	public static final double BLAST_RANGE = 8.0;
	public static final double BLAST_KNOCKBACK = 2.2;
	public static final float WARD_DOMINION = 10f;
	public static final int WARD_COOLDOWN = 30 * 20;
	public static final int WARD_TICKS = 10 * 20;
	public static final double WARD_RADIUS = 6.0;
	public static final float WARD_RESIST = 0.30f;
	public static final float SIGHT_DOMINION = 6f;
	public static final int SIGHT_COOLDOWN = 20 * 20;
	public static final int SIGHT_TICKS = 10 * 20;
	public static final double SIGHT_RANGE = 64.0;
	public static final int HELMET_COOLDOWN = 20;
	/** Shift+V Amulet of Anubis: Dominion it takes off, and its cooldown (2 min). */
	public static final float AMULET_DOMINION_CUT = 10f;
	public static final int AMULET_COOLDOWN = 2 * 60 * 20;
	public static final double AIM_RANGE = 24.0;

	// ---- Kent Nelson's own magic (helmet off): weak R, G and X only
	public static final float WEAK_BOLT_DAMAGE = 5f;
	public static final double WEAK_STEP_DISTANCE = 6.0;
	public static final int WEAK_SHIELD_MAX_TICKS = 3 * 20;
	public static final float WEAK_SHIELD_BLOCK = 0.6f;

	// ---- flight (double-tap Space, helmet on)
	public static final double FLIGHT_CRUISE_BPS = 14.0;
	public static final double FLIGHT_SPRINT_BPS = 28.0;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 6;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---- bargains
	public static final int BARGAIN_SECONDS = 10;
	/** Death's door: at 3 hearts or fewer. */
	public static final float DEATHS_DOOR_HEALTH = 6f;
	public static final float DEATHS_DOOR_DOMINION = 35f;
	public static final int DEATHS_DOOR_SHIELD_TICKS = 30 * 20;
	/** No new offer within this long of the last one. */
	public static final int OFFER_GAP_TICKS = 60 * 20;
	public static final int QUEST_NEED = 5;
	public static final float QUEST_REWARD = 30f;
	public static final float QUEST_PENALTY = 40f;
	/** Order's command: checked once every this many ticks at night, offered 1 time in this many checks (once a night). */
	public static final int QUEST_CHECK_TICKS = 60 * 20;
	public static final int QUEST_ONE_IN = 4;
	/** Each refusal today makes the next offer meaner: this much more Dominion, this much more reward. */
	public static final float MEANER_COST = 10f;
	public static final int MEANER_QUEST_NEED = 2;
	public static final float MEANER_REWARD = 10f;
	/** The Ankh Charm (Order's command reward): right-click to quiet Nabu by this much. */
	public static final float CHARM_DOMINION = 25f;

	// ---- the Tomb of Nabu
	public static final int GUARDS = 4;

	public static final int SYNC_INTERVAL = 5;
	public static final float BOSS_HEALTH = 150f;
}
