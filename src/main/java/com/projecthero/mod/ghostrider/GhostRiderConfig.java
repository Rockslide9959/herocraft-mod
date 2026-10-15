package com.projecthero.mod.ghostrider;

/**
 * v0.16.5: every Ghost Rider number in one place (static finals, like All Might's / Nova's). Times are in ticks.
 * The kit follows {@code docs/V0165_POWERS_PROPOSAL.md} section 3 as the user approved it.
 */
public final class GhostRiderConfig {
	private GhostRiderConfig() {
	}

	// ---------------------------------------------------------------- resources
	public static final float HELLFIRE_MAX = 100f;
	/**
	 * Hellfire per second, always (transformed or not, day or night, any dimension). v0.16.5 rework (user): "the same rate
	 * as Superman's sun bar" -- the Kryptonian's Solar Energy refill in direct sunlight.
	 */
	public static final float HELLFIRE_REGEN = com.projecthero.mod.kryptonian.KryptonianConfig.SOLAR_SUN_PER_SECOND;
	/** Transformed he keeps Regeneration III (amplifier 2); it costs this much Hellfire a second while it runs. */
	public static final int REGEN_AMPLIFIER = 2;
	public static final float REGEN_HELLFIRE_PER_SECOND = 2f;
	/** +11 melee damage while transformed. */
	public static final double MELEE_BONUS = 11.0;
	/** Transformed, every rise in food exhaustion is doubled (he burns through food twice as fast). */
	public static final float HUNGER_FACTOR = 2f;
	public static final float VENGEANCE_MAX = 100f;
	/** Vengeance gained per point of damage dealt to a guilty target. */
	public static final float VENGEANCE_PER_DAMAGE = 1.5f;
	/** Vengeance lost per second when nothing guilty has been hit for 10 s. */
	public static final float VENGEANCE_DECAY = 1f;

	// ---------------------------------------------------------------- the change (H)
	/** v0.16.5 rework: 56 -> 96, and the face burns over ~2.5 s instead of ~0.7 s (user: "burn away slower"). */
	public static final int TRANSFORM_TICKS = 96;
	/** Where in the change each stage sits (0..1): the clothes fold round him, the face burns away, the skull erupts. */
	public static final float FOLD_END = 0.33f;
	public static final float BURN_START = 0.35f;
	public static final float BURN_END = 0.86f;
	public static final float ERUPT = 0.88f;
	public static final int REVERT_TICKS = 24;
	public static final int TOGGLE_COOLDOWN = 20;
	/** H only turns him back when he has neither dealt nor taken combat damage for this long ... */
	public static final int OUT_OF_COMBAT_TICKS = 100;
	/** ... and no hostile monster is this close. */
	public static final double REVERT_HOSTILE_RANGE = 20.0;
	/** A death he cheated leaves him on this much health. */
	public static final float DEATH_SAVE_HEALTH = 6f;
	/** The contract's blood price: max health lost until the first transformation (5 hearts). */
	public static final double BLOOD_PRICE = 10.0;

	// ---------------------------------------------------------------- passives (transformed)
	public static final double MAX_HEALTH_BONUS = 6.0;
	/** Undead within this range that target him lose interest and back off (wither skeletons run). */
	public static final double UNDEAD_FEAR_RANGE = 12.0;

	// ---------------------------------------------------------------- bosses
	public static final float BOSS_HEALTH_THRESHOLD = 300f;
	/** A boss takes at most this fraction of its max health from one hit. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.08f;

	// ---------------------------------------------------------------- R: Hellfire Blast (held)
	public static final float BLAST_DAMAGE = 20f;
	public static final float BLAST_COST = 10f;
	public static final int BLAST_INTERVAL = 10; // 2 a second
	public static final double BLAST_SPEED = 1.8;
	public static final double BLAST_RANGE = 48.0;
	public static final int BLAST_BURN_SECONDS = 5;
	public static final int BLAST_COOLDOWN = 10;

	// ---------------------------------------------------------------- Shift+R: Hellfire Wave
	public static final float WAVE_DAMAGE = 30f; // v0.16.5 rework: 10 -> 30
	public static final double WAVE_LENGTH = 10.0;
	public static final float WAVE_COST = 25f;
	public static final int WAVE_COOLDOWN = 160;

	// ---------------------------------------------------------------- G: Chain Whip
	public static final float WHIP_DAMAGE = 18f; // v0.16.5 rework: 9 -> 18
	public static final double WHIP_RANGE = 8.0;
	public static final float WHIP_COST = 5f;
	public static final int WHIP_COOLDOWN = 30;
	/** Mobs at most this wide get yanked to him. */
	public static final double WHIP_YANK_MAX_WIDTH = 1.0;

	// ---------------------------------------------------------------- Shift+G: Chain Spin
	/** Every spin hit, no fall-off at the edge (v0.16.5 rework: 6 -> 13). */
	public static final float SPIN_DAMAGE = 13f;
	public static final double SPIN_RADIUS = 4.0;
	public static final int SPIN_TICKS = 60;
	public static final int SPIN_HIT_INTERVAL = 10;
	public static final float SPIN_COST = 25f;
	public static final int SPIN_COOLDOWN = 200;

	// ---------------------------------------------------------------- X: Hell Cycle / Shift+X: Cycle Jump
	public static final int CYCLE_COOLDOWN = 40;
	/** v0.16.5 rework (user): 15 blocks a second cruising, 30 while the rider sprints; a 4-block step. */
	public static final double CYCLE_CRUISE_BPS = 15.0;
	public static final double CYCLE_SPRINT_BPS = 30.0;
	public static final double CYCLE_STEP = 4.0;
	/**
	 * The movement-speed value that settles at {@code bps} on ordinary ground: a ridden mob adds {@code speed * 0.98}
	 * (the rider's forward input) a tick and keeps 0.6 * 0.91 of its velocity, so it settles at
	 * {@code speed * 0.98 / (1 - 0.546)} blocks a tick.
	 */
	public static double cycleSpeedFor(double bps) {
		return bps / 20.0 * (1.0 - 0.6 * 0.91) / 0.98;
	}

	public static final double CYCLE_SPEED = cycleSpeedFor(CYCLE_CRUISE_BPS);
	public static final double CYCLE_JUMP_DISTANCE = 15.0;
	public static final float CYCLE_JUMP_DAMAGE = 10f;
	public static final double CYCLE_JUMP_RADIUS = 4.0;
	public static final float CYCLE_JUMP_COST = 20f;
	public static final int CYCLE_JUMP_COOLDOWN = 120;
	/** Damage a second to creatures standing in the bike's fire trail. */
	public static final float TRAIL_DAMAGE = 3f;
	public static final int TRAIL_LIFE = 60;

	// ---------------------------------------------------------------- Z: Judgement Mark
	public static final double MARK_RANGE = 30.0;
	public static final int MARK_TICKS = 300;
	public static final float MARK_MULTIPLIER = 1.25f;
	public static final float MARK_COST = 20f;
	public static final int MARK_COOLDOWN = 400;

	// ---------------------------------------------------------------- Shift+Z: Penance Stare
	public static final double STARE_RANGE = 20.0;
	public static final int STARE_TICKS = 40;
	public static final float STARE_BASE = 10f;
	/** Extra damage per point of Vengeance (all of it is spent). */
	public static final float STARE_PER_VENGEANCE = 0.3f;
	/** Extra damage per point of the target's guilt. */
	public static final float STARE_PER_GUILT = 4f;
	public static final float STARE_MAX = 60f;
	/** Undead (no soul to burn) and bosses take this, flat. */
	public static final float STARE_FLAT = 15f;
	/** Players: damage per player they have killed (their own kill statistic). */
	public static final float STARE_PER_PLAYER_KILL = 3f;
	public static final float STARE_COST = 15f;
	public static final int STARE_COOLDOWN = 400;

	// ---------------------------------------------------------------- V: Hellfire Rush
	public static final float RUSH_DAMAGE = 18f;
	public static final double RUSH_DISTANCE = 12.0;
	public static final double RUSH_SPEED = 1.5;
	public static final double RUSH_KNOCKBACK = 2.2;
	public static final float RUSH_COST = 30f;
	public static final int RUSH_COOLDOWN = 160;

	// ---------------------------------------------------------------- Shift+V: Hellfire Eruption
	public static final float ERUPTION_DAMAGE = 25f;
	public static final double ERUPTION_RADIUS = 6.0;
	public static final double ERUPTION_LIFT = 1.1;
	public static final int ERUPTION_BURN_TICKS = 80;
	public static final float ERUPTION_GROUND_DAMAGE = 3f;
	public static final float ERUPTION_COST = 50f;
	public static final int ERUPTION_COOLDOWN = 400;

	// ---------------------------------------------------------------- C: Soul Shield / Shift+C: Rider's Wrath
	public static final int SHIELD_TICKS = 80;
	public static final float SHIELD_FACTOR = 0.5f;
	public static final int SHIELD_BURN_SECONDS = 4;
	public static final float SHIELD_COST = 25f;
	public static final int SHIELD_COOLDOWN = 280;
	public static final double WRATH_RADIUS = 6.0;
	public static final int WRATH_TICKS = 200;
	public static final float WRATH_COST = 40f;
	public static final int WRATH_COOLDOWN = 900;

	// ---------------------------------------------------------------- guilt
	/** Guilt a creature earns for hurting him, a villager or a player. */
	public static final int GUILT_HURT = 1;
	/** ... and for killing a villager or a player. */
	public static final int GUILT_KILL = 5;
	public static final int GUILT_MAX = 10;
	/** Guilt is forgotten after this long without new sins. */
	public static final int GUILT_MEMORY_TICKS = 20 * 60 * 5;
}
