package com.projecthero.mod.shazam;

/**
 * v0.17.3 Shazam: every number of the power in one place (docs/FATE_SHAZAM_DESIGN.md, the Shazam section). Times are in ticks.
 * A Shazam Family member gets {@link #FAMILY_SHARE} of the health bonus and of every move's damage.
 */
public final class ShazamConfig {
	private ShazamConfig() {
	}

	public static final int S = 20;

	// ---------------------------------------------------------------- the word
	/** The gap between two words (transform / turn back). */
	public static final int WORD_GAP = 4 * S;
	/** The transform bolt: 12 to every creature within 3 blocks (never the speaker or a squadmate). */
	public static final float WORD_BOLT_DAMAGE = 12f;
	public static final double WORD_BOLT_RADIUS = 3.0;
	/** Players within this range see the "SHAZAM!" subtitle. */
	public static final double WORD_HEARD_RANGE = 32.0;

	// ---------------------------------------------------------------- passives (transformed)
	/** Stamina of Atlas: 50 health (a family member: 25). */
	public static final double HEALTH_BONUS = 30.0;
	public static final double FAMILY_HEALTH_BONUS = 5.0;
	public static final float DAMAGE_RESIST = 0.35f;
	/** Strength of Hercules: +10 melee (a family member: +5). */
	public static final double MELEE_BONUS = 10.0;
	/** Speed of Mercury: +40% speed. */
	public static final double SPEED_BONUS = 0.40;
	/** 0.1 blocks taller (1.8 -> 1.9). */
	public static final double SCALE_BONUS = 0.1 / 1.8;
	/** Magic hurts the champion 50% more. */
	public static final float MAGIC_WEAKNESS = 1.5f;
	/** A family member's share of the health bonus and of every move's damage. */
	public static final float FAMILY_SHARE = 0.5f;

	// ---------------------------------------------------------------- the Lightning bar (Slab)
	public static final float LIGHTNING_MAX = 100f;
	/** Fills this much a second during a thunderstorm. */
	public static final float STORM_FILL_PER_TICK = 3f / S;
	/** A lightning strike (not his own) within this range... */
	public static final double STRIKE_NEAR = 24.0;
	/** ...gives this much. */
	public static final float STRIKE_FILL = 50f;
	public static final int SYNC_INTERVAL = 5;

	// ---------------------------------------------------------------- flight (double-tap Space)
	public static final double FLIGHT_CRUISE_BPS = 22.0;
	public static final double FLIGHT_SPRINT_BPS = 44.0;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 10;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---------------------------------------------------------------- R: Lightning Hand
	public static final float HAND_COST = 8f;
	public static final float HAND_DAMAGE = 9f;
	public static final double HAND_RANGE = 24.0;
	public static final int HAND_CHAINS = 2;
	public static final double HAND_CHAIN_RANGE = 6.0;
	public static final int HAND_COOLDOWN = 15;

	// ---------------------------------------------------------------- Shift+R: Call the Lightning
	public static final float CALL_COST = 30f;
	public static final float CALL_DAMAGE = 22f;
	public static final double CALL_RANGE = 64.0;
	public static final double CALL_RADIUS = 2.5;
	public static final int CALL_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- G: Hercules Punch
	public static final float PUNCH_COST = 10f;
	public static final float PUNCH_DAMAGE = 16f;
	public static final double PUNCH_REACH = 4.5;
	/** Launch speed: about 15 blocks of flight with air drag. */
	public static final double PUNCH_LAUNCH = 1.45;
	public static final double PUNCH_LIFT = 0.45;
	/** A launched creature that hits a wall takes this much more. */
	public static final float WALL_DAMAGE = 8f;
	public static final int LAUNCH_TICKS = 40;
	public static final int PUNCH_COOLDOWN = 30;

	// ---------------------------------------------------------------- Shift+G: Atlas Lift
	public static final float LIFT_COST = 15f;
	public static final double LIFT_REACH = 5.0;
	/** Held overhead at most this long, then thrown on its own. */
	public static final int LIFT_HOLD_TICKS = 4 * S;
	public static final double LIFT_THROW_SPEED = 2.2;
	/** A hurled creature takes this much when it lands or hits a wall. */
	public static final float LIFT_IMPACT_DAMAGE = 10f;
	public static final int LIFT_COOLDOWN = 2 * S;

	// ---------------------------------------------------------------- Z: Zeus Storm
	public static final float STORM_COST = 50f;
	public static final int STORM_TICKS = 5 * S;
	public static final int STORM_EVERY = 10;
	public static final double STORM_RANGE = 16.0;
	public static final float STORM_DAMAGE = 10f;
	public static final int STORM_COOLDOWN = 15 * S;

	// ---------------------------------------------------------------- Shift+Z: Eternity Strike
	public static final float ETERNITY_COST = 100f;
	/** Rising (the sky darkens) for this long, then the huge bolt hits him. */
	public static final int ETERNITY_RISE_TICKS = 30;
	/** After the bolt he slams down; the blast goes off when he lands (or after this long at most). */
	public static final int ETERNITY_MAX_TICKS = 5 * S;
	public static final float ETERNITY_DAMAGE = 50f;
	public static final double ETERNITY_RADIUS = 10.0;
	public static final int ETERNITY_COOLDOWN = 3 * 60 * S;

	// ---------------------------------------------------------------- X: Mercury Dash / Shift+X: Thunder Tackle
	public static final double DASH_DISTANCE = 12.0;
	public static final int DASH_COOLDOWN = 25;
	public static final float TACKLE_COST = 20f;
	public static final double TACKLE_RANGE = 20.0;
	public static final float TACKLE_DAMAGE = 18f;
	public static final int TACKLE_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- C: Thunderclap / Shift+C: Achilles Stand
	public static final float CLAP_COST = 15f;
	public static final float CLAP_DAMAGE = 12f;
	public static final double CLAP_RANGE = 8.0;
	/** Half-angle of the cone (degrees). */
	public static final double CLAP_HALF_ANGLE = 45.0;
	public static final double CLAP_KNOCKBACK = 1.4;
	public static final int CLAP_GLASS_MAX = 32;
	public static final int CLAP_COOLDOWN = 2 * S;
	public static final float STAND_COST = 30f;
	public static final int STAND_TICKS = 4 * S;
	public static final double STAND_RANGE = 16.0;
	public static final float STAND_SQUAD_TAKEN = 0.7f;
	public static final int STAND_COOLDOWN = 30 * S;

	// ---------------------------------------------------------------- V: Wisdom of Solomon / Shift+V: Share the Power
	public static final int WISDOM_TICKS = 10 * S;
	public static final double WISDOM_RANGE = 24.0;
	public static final int WISDOM_COOLDOWN = 20 * S;
	public static final double SHARE_RANGE = 16.0;
	public static final int FAMILY_TICKS = 10 * 60 * S;
	public static final int FAMILY_MAX = 5;
	public static final int SHARE_COOLDOWN = 10 * 60 * S;

	// ---------------------------------------------------------------- the Wizard
	/** One in this many 30-second checks per player near a village in a thunderstorm. */
	public static final int WIZARD_CHANCE = 60;
	public static final int WIZARD_CHECK_EVERY = 30 * S;
	/** He waits this long, then fades (or when the storm ends). */
	public static final int WIZARD_LIFETIME = 5 * 60 * S;
	/** Judged unworthy: he won't come back to that player for 5 days. */
	public static final long WIZARD_BAN_TICKS = 5L * 24000L;
	/** A villager or iron golem killed within this long makes the heart impure (3 days). */
	public static final long INNOCENT_KILL_MEMORY = 3L * 24000L;
	/** Villagers within this range must not dislike him. */
	public static final double REPUTATION_RANGE = 48.0;
	/** The village must be within this many sections (16 blocks each). */
	public static final int VILLAGE_SECTIONS = 4;
}
