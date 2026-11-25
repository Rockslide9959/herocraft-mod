package com.projecthero.mod.shazam;

/**
 * v0.17.3 Shazam: every number of the power in one place (docs/SHAZAM_REFERENCE.md). Times are in ticks.
 * A Shazam Family member gets {@link #FAMILY_SHARE} of the health bonus and of every move's damage.
 *
 * <p>v0.17.7 (user: "rebalance shazam please hes A tier, and give him better moves aswell please, he needs more moves aswell give
 * him hold moves"): the Planet tier -- melee 16-19, abilities 16-20, ultimate 32-40 per hit, nothing over {@link #MAX_HIT} 40 --
 * and a new S-H-A-Z-A-M kit with a hold move on R, G, C, Z and V.
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

	// ---------------------------------------------------------------- v0.17.7 Planet tier ("A" tier, user)
	/** No single hit of his goes over this, whatever stacks on it (Wisdom's crit, the Thunderstrike chain). */
	public static final float MAX_HIT = com.projecthero.mod.hero.PowerRank.PLANET.ultimateMax;
	/** Anything with this much max health (or a Titan / Wither / Dragon) is a boss: capped per hit, never lifted or launched. */
	public static final float BOSS_HEALTH_THRESHOLD = 300.0f;
	/** A single hit of his never takes more than this fraction of a boss's max health. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.06f;

	// ---------------------------------------------------------------- passives (transformed)
	/** Stamina of Atlas: 40 health (v0.17.5 user, was 50; a family member: 25). */
	public static final double HEALTH_BONUS = 20.0;
	public static final double FAMILY_HEALTH_BONUS = 5.0;
	/** v0.17.7 Planet: netherite-level toughness instead of the old flat 35% -- armour 20, toughness 12, 40% knockback resistance. */
	public static final double ARMOR_BONUS = 20.0;
	public static final double TOUGHNESS_BONUS = 12.0;
	public static final double KNOCKBACK_RESIST = 0.4;
	/** Strength of Hercules: +16 melee, a 17 bare fist (Planet melee 16-19; a family member: +8). */
	public static final double MELEE_BONUS = 16.0;
	/** Speed of Mercury: +40% speed. */
	public static final double SPEED_BONUS = 0.40;
	/** 0.1 blocks taller (1.8 -> 1.9). */
	public static final double SCALE_BONUS = 0.1 / 1.8;
	/** Magic hurts the champion 50% more. */
	public static final float MAGIC_WEAKNESS = 1.5f;
	/** A family member's share of the health bonus and of every move's damage. */
	public static final float FAMILY_SHARE = 0.5f;

	// ---------------------------------------------------------------- the Lightning bar
	public static final float LIGHTNING_MAX = 100f;
	/** v0.17.4 (user): fills 4 a second whenever he's transformed (the moves' costs still drain it). */
	public static final float LIGHTNING_REGEN_PER_TICK = 4f / S;
	/** Fills this much more a second during a thunderstorm (on top of the 4 a second). */
	public static final float STORM_FILL_PER_TICK = 3f / S;
	/** A lightning strike (not his own) within this range... */
	public static final double STRIKE_NEAR = 24.0;
	/** ...gives this much. */
	public static final float STRIKE_FILL = 50f;
	public static final int SYNC_INTERVAL = 5;

	// ---------------------------------------------------------------- flight (double-tap Space)
	/** v0.17.7 Planet: 18 b/s cruising, 34 sprinting (was 22 / 44; Thor, Cosmic, flies 18 / 36). */
	public static final double FLIGHT_CRUISE_BPS = 18.0;
	public static final double FLIGHT_SPRINT_BPS = 34.0;
	public static final int FLIGHT_TOGGLE_COOLDOWN = 10;
	public static final int FLIGHT_LIFTOFF_GRACE = 10;

	// ---------------------------------------------------------------- keys
	/** A key held this long is its hold move; a shorter press is the tap, fired on the release (Spider-Man's 8 ticks). */
	public static final int HOLD_TICKS = 8;

	// ---------------------------------------------------------------- R: Strength of Hercules
	/** Hercules Combo: three punches, the third knocks back. Melee range (16-19). */
	public static final float COMBO_DAMAGE_1 = 17f;
	public static final float COMBO_DAMAGE_2 = 17f;
	public static final float COMBO_DAMAGE_3 = 19f;
	public static final double COMBO_REACH = 4.5;
	/** The next punch continues the combo if it comes within this long of the last. */
	public static final int COMBO_WINDOW = 24;
	/** Between punches, and after the finisher. */
	public static final int COMBO_GAP = 5;
	public static final int COMBO_FINISH_COOLDOWN = 16;
	public static final double COMBO_FINISH_KNOCKBACK = 1.6;
	/** Shift+R Herculean Uppercut. */
	public static final float UPPERCUT_DAMAGE = 18f;
	public static final float UPPERCUT_COST = 10f;
	public static final double UPPERCUT_LAUNCH = 1.25;
	public static final int UPPERCUT_COOLDOWN = 4 * S;
	/** Hold R Haymaker of Hercules: charges for up to 1.5 s, 16 -> 20, then a shockwave round the impact. */
	public static final float HAYMAKER_MIN = 16f;
	public static final float HAYMAKER_MAX = 20f;
	public static final int HAYMAKER_CHARGE_TICKS = 30;
	/** Held this long, it throws itself. */
	public static final int HAYMAKER_MAX_HOLD = 3 * S;
	public static final float HAYMAKER_SHOCK_DAMAGE = 16f;
	public static final double HAYMAKER_SHOCK_RADIUS = 3.5;
	public static final double HAYMAKER_REACH = 5.0;
	public static final float HAYMAKER_COST = 15f;
	public static final int HAYMAKER_COOLDOWN = 6 * S;

	// ---------------------------------------------------------------- G: Power of Zeus
	/** Lightning of Zeus: a bolt from the sky on the aimed target, chaining to 2 more. */
	public static final float ZEUS_DAMAGE = 18f;
	public static final float ZEUS_CHAIN_DAMAGE = 16f;
	public static final int ZEUS_CHAINS = 2;
	public static final double ZEUS_RANGE = 40.0;
	public static final double ZEUS_CHAIN_RANGE = 8.0;
	/** Aimed at nothing, the bolt hits round where he looks. */
	public static final double ZEUS_SPLASH = 2.0;
	public static final float ZEUS_COST = 20f;
	public static final int ZEUS_COOLDOWN = 50;
	/** Shift+G Thunderhead Slam: a lightning-charged ground pound. */
	public static final float SLAM_DAMAGE = 18f;
	public static final double SLAM_RADIUS = 5.0;
	public static final int SLAM_WINDUP = 7;
	public static final float SLAM_COST = 25f;
	public static final int SLAM_COOLDOWN = 7 * S;
	/** Hold G Zeus's Wrath: a held stream from both hands -- 16 every quarter second, 4 lightning a pulse, 5 s at most. */
	public static final float WRATH_DAMAGE = 16f;
	public static final int WRATH_EVERY = 5;
	public static final float WRATH_COST = 4f;
	public static final double WRATH_RANGE = 22.0;
	public static final int WRATH_MAX_TICKS = 5 * S;
	public static final int WRATH_COOLDOWN = 6 * S;
	/** The beam's push in a beam clash. */
	public static final float WRATH_CLASH_POWER = 1.4f;

	// ---------------------------------------------------------------- C: Speed of Mercury
	/** Mercury Blitz: a dash through up to 3 enemies in a line. */
	public static final float BLITZ_DAMAGE = 16f;
	public static final int BLITZ_MAX_HITS = 3;
	public static final double BLITZ_DISTANCE = 14.0;
	public static final double BLITZ_WIDTH = 0.9;
	public static final float BLITZ_COST = 10f;
	public static final int BLITZ_COOLDOWN = 70;
	/** Shift+C Thunderclap: a shockwave cone. */
	public static final float CLAP_COST = 15f;
	public static final float CLAP_DAMAGE = 16f;
	public static final double CLAP_RANGE = 8.0;
	/** Half-angle of the cone (degrees). */
	public static final double CLAP_HALF_ANGLE = 45.0;
	public static final double CLAP_KNOCKBACK = 1.4;
	public static final int CLAP_GLASS_MAX = 32;
	public static final int CLAP_COOLDOWN = 5 * S;
	/** Hold C Mercury Rush: chained super-speed hops at the enemies round him, one every 5 ticks, 5 hits at most. */
	public static final float RUSH_DAMAGE = 16f;
	public static final int RUSH_MAX_HITS = 5;
	public static final int RUSH_EVERY = 5;
	public static final double RUSH_RANGE = 10.0;
	public static final float RUSH_COST = 6f;
	public static final int RUSH_COOLDOWN = 10 * S;

	// ---------------------------------------------------------------- Z: Stamina of Atlas
	/** Atlas Quake: a stomp that cracks the ground. */
	public static final float QUAKE_DAMAGE = 19f;
	public static final double QUAKE_RADIUS = 5.0;
	public static final int QUAKE_WINDUP = 5;
	public static final float QUAKE_COST = 20f;
	public static final int QUAKE_COOLDOWN = 6 * S;
	/** Hold Z Titan's Grip: grab, hold it overhead while the key is held (4 s at most), hurl it on the release. */
	public static final double GRIP_REACH = 5.0;
	public static final float GRIP_DAMAGE = 20f;
	public static final int GRIP_MAX_HOLD = 4 * S;
	public static final double GRIP_THROW_SPEED = 2.2;
	public static final float GRIP_COST = 15f;
	public static final int GRIP_COOLDOWN = 6 * S;
	/** A thrown / launched creature is tracked this long for its impact (a throw that never lands still hurts at the end). */
	public static final int LAUNCH_TICKS = 40;

	// ---------------------------------------------------------------- Shift+Z: ULTIMATE "SHAZAM!" Thunderstrike
	/** Needs a full bar and spends it all. */
	public static final float ULT_COST = 100f;
	/** He shouts and rises for this long; then the colossal bolt hits him... */
	public static final int ULT_RISE_TICKS = 16;
	/** ...and he releases it outward. */
	public static final int ULT_RELEASE_AT = 24;
	public static final float ULT_DAMAGE = 40f;
	public static final double ULT_RADIUS = 8.0;
	/** For 8 s after, every hit he lands chains lightning to the nearest other enemy. */
	public static final int ULT_CHARGED_TICKS = 8 * S;
	public static final float ULT_CHAIN_DAMAGE = 16f;
	public static final double ULT_CHAIN_RANGE = 8.0;
	/** At most one chain every this many ticks. */
	public static final int ULT_CHAIN_GAP = 4;
	public static final int ULT_COOLDOWN = 90 * S;

	// ---------------------------------------------------------------- X: Mercury Dash / Shift+X: Sonic Leap
	public static final double DASH_DISTANCE = 12.0;
	public static final int DASH_COOLDOWN = 25;
	/** X while flying: Mercury Burst, flight speed x1.6 for 2 s. */
	public static final int BURST_TICKS = 2 * S;
	public static final double BURST_SPEED = 1.6;
	public static final int BURST_COOLDOWN = 3 * S;
	/** Shift+X Sonic Leap: a huge leap that turns into flight near the top. */
	public static final double LEAP_UP = 2.2;
	public static final double LEAP_FORWARD = 1.0;
	public static final int LEAP_TO_FLIGHT = 12;
	public static final int LEAP_COOLDOWN = 3 * S;

	// ---------------------------------------------------------------- V: Wisdom of Solomon / Shift+V: Courage of Achilles / Hold V
	public static final int WISDOM_TICKS = 10 * S;
	/** Enemies within this range glow, and his hits on them crit. */
	public static final double WISDOM_RANGE = 30.0;
	public static final float WISDOM_CRIT = 1.25f;
	public static final int WISDOM_COOLDOWN = 20 * S;
	/** Courage of Achilles: Resistance II and no knockback (fear never takes while he is transformed, as before). */
	public static final int COURAGE_TICKS = 8 * S;
	public static final int COURAGE_RESISTANCE = 1;
	public static final float COURAGE_COST = 20f;
	public static final int COURAGE_COOLDOWN = 30 * S;
	/** Hold V: Share the Power (the champion only). */
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
