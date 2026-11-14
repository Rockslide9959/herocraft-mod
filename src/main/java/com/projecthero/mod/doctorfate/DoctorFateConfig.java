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
	/** v0.17.4 (user: "melee bonus +8"): wearing the helmet adds 8 attack damage (4 hearts) to every punch and weapon hit. */
	public static final double HELMET_MELEE_BONUS = 8.0;
	/** v0.17.4 (user): Regeneration I while the helmet is on (refreshed before it runs out, so it never stutters). */
	public static final int HELMET_REGEN_TICKS = 219;
	public static final int HELMET_REGEN_REFRESH_BELOW = 25;
	/**
	 * v0.17.4 the put-on sequence (1.5 s): the helmet forms in both hands at the chest (0-6), is raised overhead (6-14), held up
	 * while the ankh flares behind it (14-20), lowered onto the head (20-27) and settles (27-30); the suit fades in from 20.
	 */
	public static final int HELMET_EQUIP_TICKS = 30;
	/** v0.17.4 the lift-off (1.2 s): hands to the helmet (0-6), lifted overhead (6-14), brought down to the chest (14-20), gone to gold (20-24). */
	public static final int HELMET_REMOVE_TICKS = 24;
	/** v0.17.4 H with no helmet on: it flies to you (or out of an ankh portal) for this long before the put-on sequence starts. */
	public static final int HELMET_SUMMON_TICKS = 20;
	/** After a Possession the helmet can't go back on for 60 s. */
	public static final int LOCKOUT_TICKS = 60 * 20;

	// ---- Dominion
	public static final float DOMINION_MAX = 100f;
	/**
	 * v0.17.4 retune (user: "about 2 minutes" of heavy use before Nabu starts taking over): wearing the helmet adds +0.06 a second
	 * (about 28 minutes from 0 to 100 on its own, was 11). Every spell's Dominion is a tenth of v0.17.3's; see
	 * {@code DoctorFateGameTests#heavyUseTakesAboutTwoMinutesToTakingHold} for the sum.
	 */
	public static final float WEAR_GAIN_PER_TICK = 0.06f / 20f;
	/** Helmet off: -0.5 a second (v0.17.3: -0.75; 100 to 0 in 3 min 20 s). */
	public static final float OFF_DRAIN_PER_TICK = 0.5f / 20f;
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
	/** Taking hold: each spell also costs this much health (twice for spells worth {@link #BIG_SPELL_DOMINION}+ Dominion). */
	public static final float HOLD_HEALTH_COST = 1f;
	/** v0.17.4: a "big" spell for Taking hold's health cost (Glyph Storm, Ward, Banishment; 8 before the tenfold retune). */
	public static final float BIG_SPELL_DOMINION = 0.8f;
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
	public static final float BOLT_DOMINION = 0.1f; // v0.17.4 retune: every spell's Dominion is a tenth of v0.17.3's
	public static final int BOLT_COOLDOWN = 10;
	public static final double BOLT_RANGE = 32.0;
	public static final float CHAINS_DAMAGE = 8f; // v0.17.3 moves pass: was 6
	public static final float CHAINS_DOMINION = 0.3f;
	public static final int CHAINS_COOLDOWN = 5 * 20;
	public static final int CHAINS_BIND_TICKS = 3 * 20;
	public static final int CHAINS_BIND_BOSS_TICKS = 20;
	/** Ankh Shield: +0.1 Dominion a second while held. */
	public static final float SHIELD_DOMINION_PER_TICK = 0.1f / 20f;
	public static final int SHIELD_COOLDOWN = 20;
	public static final float BANISH_DOMINION = 1.2f;
	public static final int BANISH_COOLDOWN = 20 * 20;
	public static final double BANISH_THROW = 40.0;
	/** A creature at least this strong (max health) is thrown, not banished. */
	public static final float BANISH_STRONG_HEALTH = 60f;
	public static final float GLYPH_DAMAGE = 5f; // v0.17.3 moves pass: was 4
	public static final float GLYPH_DOMINION = 0.8f;
	public static final int GLYPH_COOLDOWN = 12 * 20;
	public static final int GLYPH_TICKS = 3 * 20;
	public static final int GLYPH_HIT_EVERY = 8; // v0.17.3 moves pass: was 10 (6 hits -> 7)
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
	public static final int STEP_COOLDOWN = 2 * 20; // v0.17.3 moves pass: was 3 s
	public static final float GATE_DOMINION = 0.5f;
	public static final int GATE_COOLDOWN = 8 * 20;
	public static final double GATE_LIFT = 15.0;
	public static final float BLAST_DAMAGE = 14f;
	public static final float BLAST_DOMINION = 0.4f;
	public static final int BLAST_COOLDOWN = 5 * 20;
	public static final double BLAST_RANGE = 8.0;
	public static final double BLAST_KNOCKBACK = 2.2;
	public static final float WARD_DOMINION = 1.0f;
	public static final int WARD_COOLDOWN = 30 * 20;
	public static final int WARD_TICKS = 10 * 20;
	public static final double WARD_RADIUS = 6.0;
	public static final float WARD_RESIST = 0.30f;
	public static final float SIGHT_DOMINION = 0.6f;
	public static final int SIGHT_COOLDOWN = 20 * 20;
	public static final int SIGHT_TICKS = 10 * 20;
	public static final double SIGHT_RANGE = 64.0;
	public static final int HELMET_COOLDOWN = 20;
	/** Shift+V Amulet of Anubis: Dominion it takes off, and its cooldown (2 min). */
	public static final float AMULET_DOMINION_CUT = 10f;
	public static final int AMULET_COOLDOWN = 2 * 60 * 20;
	public static final double AIM_RANGE = 24.0;
	/** v0.17.3 moves pass: a targeted spell (Chains, Banishment, Gate Drop) that misses the crosshair takes the creature nearest the look ray within this many degrees. */
	public static final double AIM_ASSIST_DEG = 7.0;
	/** v0.17.3 moves pass: Gate Drop hurls the creature down at this speed (blocks / tick; was a 0.2 drift), so the fall is quick. */
	public static final double GATE_DROP_SPEED = 1.1;
	/** v0.17.3 moves pass: creatures in a Glyph Storm are slowed (Slowness I, refreshed each hit). */
	public static final int GLYPH_SLOW_TICKS = 20;

	// ---- the Tower of Fate mindscape (v0.17.3, hold Shift+V)
	/** Hold Shift+V this long to go into the Tower (a shorter press is the Amulet). */
	public static final int MIND_HOLD_TICKS = 20;
	/** Blessing of Order: spells deal x1.3 for 30 s, for +15 Dominion, every 3 min (helmet on). */
	public static final float MIND_BLESSING_MULT = 1.3f;
	public static final int MIND_BLESSING_TICKS = 30 * 20;
	public static final float MIND_BLESSING_DOMINION = 15f;
	public static final int MIND_BLESSING_COOLDOWN = 3 * 60 * 20;
	/** Silence: 60 s with no Override, Pull, lag or whispers; spells' Dominion x1.5; refused from Taking hold; every 5 min. */
	public static final int MIND_SILENCE_TICKS = 60 * 20;
	public static final float MIND_SILENCE_COST_MULT = 1.5f;
	public static final int MIND_SILENCE_COOLDOWN = 5 * 60 * 20;
	/** Debt: -25 Dominion now, 35 collected 10 minutes later; not below 10 Dominion; settled early it costs 30. */
	public static final float MIND_DEBT_CUT = 25f;
	public static final float MIND_DEBT_OWED = 35f;
	public static final int MIND_DEBT_TICKS = 10 * 60 * 20;
	public static final float MIND_DEBT_MIN = 10f;
	public static final float MIND_REPAY_DISCOUNT = 5f;

	// ---- Kent Nelson's own magic (v0.17.3, helmet off: weak R, G and X). v0.17.4 (user): no powers without the helmet; kept for the
	// weak code paths, which nothing reaches now
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
