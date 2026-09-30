package com.projecthero.mod.moonknight;

/**
 * Every tunable number for Moon Knight in one place (the user's hard requirement: "put all tunable numbers in a
 * config class so I can balance easily"). Same pattern as {@code AllMightConfig}: plain constants, ticks unless the
 * name says otherwise. Ability numbers are BASE values -- damage / range / duration are multiplied by
 * {@link MoonKnightLunar#power} and cooldowns divided by it.
 *
 * <p>Built in phases (see {@code docs/MOONKNIGHT_REFERENCE.md}); Phase 1 covers the lunar power, the Vengeance meter,
 * the Fracture and Khonshu's Resurrection charge.
 */
public final class MoonKnightConfig {
	private MoonKnightConfig() {
	}

	private static final int SECOND = 20;
	private static final int MINUTE = 60 * SECOND;
	/** One Minecraft day. */
	public static final long DAY_TICKS = 24000L;
	/** A full lunar cycle: eight days, full moon on the first. */
	public static final long MOON_CYCLE_TICKS = 8L * DAY_TICKS;

	// ---------------------------------------------------------------- lunar power

	// v0.14.4: exactly three lunar states (MoonKnightLunar.State) -- the per-phase table and the no-sky penalty are gone.
	/** FULL MOON: night with moon phase 0 -- the strongest. */
	public static final float LUNAR_FULL_MOON = 1.5f;
	/** NIGHT: any other night. The base numbers below are the night numbers (x1.0). */
	public static final float LUNAR_NIGHT = 1.0f;
	/** DAY: daytime, and always in the Nether and the End (no moon there) -- the weakest. */
	public static final float LUNAR_DAY = 0.7f;

	// ---------------------------------------------------------------- vengeance

	public static final float VENGEANCE_MAX = 100.0f;
	/** Starting Vengeance after the ritual (or a /moonknight grant). */
	public static final float VENGEANCE_START = 50.0f;
	/** Starting Vengeance when the ritual is completed under a full moon. */
	public static final float VENGEANCE_START_FULL_MOON = 100.0f;
	/** Killing a hostile mob that was targeting a villager, wandering trader, iron golem or another player. */
	public static final float VENGEANCE_PROTECTOR_KILL = 6.0f; // v0.14.4: was 5
	/** Any other hostile mob kill at night (or under the full moon). */
	public static final float VENGEANCE_NIGHT_KILL = 3.0f; // v0.14.4: was 2
	/** Any other hostile mob kill during the day. */
	public static final float VENGEANCE_DAY_KILL = 2.0f; // v0.14.4: was 1
	// v0.14.4: the two-idle-days drain is gone -- Vengeance now regenerates out of combat (see the v0.14.4 block).

	// ---------------------------------------------------------------- fracture

	/** Vengeance hitting 0 while transformed: a forced random alter switch for this long. */
	public static final int FRACTURE_TICKS = 20 * SECOND;
	/** The screen wobble at the start of a fracture (Nausea), kept short so it isn't annoying. */
	public static final int FRACTURE_NAUSEA_TICKS = 3 * SECOND;
	/** A fracture can't happen again within this long of the last one -- it should be rare. */
	public static final int FRACTURE_MIN_GAP_TICKS = 10 * MINUTE;

	// ---------------------------------------------------------------- Khonshu's Resurrection (charge state; the save itself is Phase 6)

	/** Hearts (half-hearts x2) the host is restored to. */
	public static final float RESURRECT_HEALTH = 12.0f;
	/** Invulnerability after a resurrection. */
	public static final int RESURRECT_INVULNERABLE_TICKS = 2 * SECOND;

	// ---------------------------------------------------------------- suit (Phase 2)

	/** The H transformation: bandages spiral up the body for this long, invulnerable, then the suit is on. */
	public static final int TRANSFORM_TICKS = 30;
	/** Un-transforming: the suit dissolves away pixel by pixel for this long (v0.13.21: 10 -> 30, 1.5 s like the suit-up). */
	public static final int UNTRANSFORM_TICKS = 30;
	/** Anti-spam gate on H. */
	public static final int TOGGLE_DEBOUNCE_TICKS = 15;

	// ---------------------------------------------------------------- the suit's own gifts (v0.13.21)

	/** While the suit is on: heal {@link #SUIT_REGEN_AMOUNT} (half a heart) every {@link #SUIT_REGEN_INTERVAL} ticks. */
	public static final int SUIT_REGEN_INTERVAL = 5;
	public static final float SUIT_REGEN_AMOUNT = 1.0f;
	/** While the suit is on: flat melee damage added to every hand-to-hand hit (an attack-damage modifier). */
	public static final double SUIT_MELEE_BONUS = 7.0;
	/** v0.14.3: the suit is faster (+30% movement speed) and jumps over two blocks (jump strength +0.16 = 0.58, ~2.2 blocks). */
	public static final double SUIT_SPEED_BONUS = 0.30;
	public static final double SUIT_JUMP_BONUS = 0.16;
	/** v0.14.3: so the higher jump never costs a heart on landing. */
	public static final double SUIT_SAFE_FALL_BONUS = 1.0;
	/** While the suit is on: every hit taken is multiplied by this (20% less damage). */
	public static final float SUIT_DAMAGE_TAKEN = 0.80f;
	/**
	 * Khonshu will not let his fist fall: out of the suit, a single hit bigger than {@link #AUTO_SUIT_HIT}, or being
	 * left below {@link #AUTO_SUIT_HEALTH} (4 hearts) by any hit, starts the suit-up on its own.
	 */
	public static final float AUTO_SUIT_HIT = 10.0f;
	public static final float AUTO_SUIT_HEALTH = 8.0f;
	/** ...but not within this long of taking the suit off by choice (H), so it can still come off mid-fight. */
	public static final int AUTO_SUIT_GRACE_TICKS = 60;
	/** Changing alter while suited: the new alter's suit rematerialises over the old one, pixel by pixel, this long. */
	public static final int ALTER_SWAP_TICKS = 30;

	// ---------------------------------------------------------------- keys: tap / hold / sneak (Phase 3+)

	/** A key held this long counts as a HOLD instead of a TAP. */
	public static final int HOLD_THRESHOLD_TICKS = 10;

	// ---------------------------------------------------------------- R: Crescent Darts (Phase 3)

	/** v0.13.21: 5 -> 15 (x lunar power like every ability number). */
	public static final float DART_DAMAGE = 15.0f;
	public static final double DART_SPEED = 1.9;
	/** At night a dart gently homes toward a hostile mob within this radius of its path. */
	public static final double DART_HOMING_RADIUS = 6.0;
	/** Degrees per tick a homing dart may turn. */
	public static final float DART_HOMING_TURN = 6.0f;
	/** A dart that hits nothing turns back like a boomerang after flying this far. */
	public static final double DART_BOOMERANG_RANGE = 24.0;
	public static final int DART_COOLDOWN = 20;
	/** HOLD: charge up to this long, release to throw the fan. */
	public static final int DART_FAN_MAX_CHARGE = 30;
	/** v0.14.4: always 5 (was 3, 5 under a full moon); each homes on one of the 5 closest hostiles. */
	public static final int DART_FAN_COUNT = 5;
	public static final float DART_FAN_SPREAD_DEGREES = 12.0f;
	public static final int DART_FAN_COOLDOWN = 65;
	/** SNEAK+R Moon Mark: the target glows and takes +30% from the Moon Knight for 10 s. */
	public static final int MOON_MARK_TICKS = 200;
	public static final float MOON_MARK_BONUS = 0.30f;
	public static final int MOON_MARK_COOLDOWN = 100; // v0.14.4: 5 s (was 8 s)

	// ---------------------------------------------------------------- the Cape (Phase 3; v0.13.21 off the keys: glide = jump + hold Sneak, block = hold right click)

	/** Cape Glide: horizontal speed along the look (blocks/tick) and the slowest allowed fall. */
	public static final double GLIDE_SPEED = 0.85; // v0.14.3: was 0.55
	public static final double GLIDE_SINK = 0.06;
	/** At night glides carry further: the sink rate is divided by the lunar power. */
	public static final boolean GLIDE_BETTER_AT_NIGHT = true;
	/** Glide starts once the player has been airborne this long with Sneak held (a jump, then Shift). */
	public static final int GLIDE_MIN_AIR_TICKS = 3;
	/** Gliding into a mob kicks it: damage (x lunar power), knockback, and the gap before the next kick can land. */
	public static final float GLIDE_KICK_DAMAGE = 12.0f;
	public static final double GLIDE_KICK_KNOCKBACK = 1.1;
	public static final int GLIDE_KICK_GAP_TICKS = 10;
	/** HOLD right click Cape Block: every hit taken is multiplied by this (30% less), for as long as it is held. */
	public static final float CAPE_BLOCK_FACTOR = 0.70f;
	/** SNEAK+G Shadow Step: a backward blink and invisibility. */
	public static final double SHADOW_STEP_DISTANCE = 5.0;
	public static final int SHADOW_STEP_INVIS_TICKS = 60;
	public static final int SHADOW_STEP_COOLDOWN = 160;

	// ---------------------------------------------------------------- X: Dash (v0.13.21)

	/** TAP X: a burst along the look, this fast (blocks/tick) for this long -- about 12 blocks (v0.14.4: was 5 ticks, ~7). */
	public static final double DASH_SPEED = 1.5;
	public static final int DASH_TICKS = 8;
	public static final int DASH_COOLDOWN = 30;

	// ---------------------------------------------------------------- SNEAK+X Grappling Line, G Grapple Kick (Phase 4; v0.13.21 keys)

	/** v0.13.21: 24 x lunar power -> a flat 60 blocks, for the line and the kick. */
	public static final double GRAPPLE_RANGE = 100.0; // v0.14.3: was 60
	public static final int GRAPPLE_COOLDOWN = 40;
	/** TAP G Grapple Kick: grapple to a mob and dive-kick it on arrival. */
	public static final float DIVE_KICK_DAMAGE = 20.0f; // v0.14.4: was 8
	public static final double DIVE_KICK_KNOCKBACK = 1.4;
	public static final int DIVE_KICK_COOLDOWN = 100;
	/** SNEAK+X at a mob: the line reels it in and it is stunned (Slowness IV) this long once it arrives. */
	public static final int YANK_STUN_TICKS = 30;
	/** How fast a reeled-in mob is dragged (blocks/tick). */
	public static final double GRAPPLE_REEL_SPEED = 1.2;

	// ---------------------------------------------------------------- C: Truncheon / Staff (Phase 4; Z before v0.13.21)

	/** v0.14.4: the combo resets if the next truncheon hit comes later than this after the last one. */
	public static final int TRUNCHEON_COMBO_WINDOW = 30;
	/** v0.14.4: the combo's 3rd hit, the overhead smash: extra damage (x power) and knockback. Was 4. */
	public static final float TRUNCHEON_SLAM_BONUS = 6.0f;
	public static final double TRUNCHEON_SLAM_KNOCKBACK = 1.2;
	/** Hitting mobs at night with the truncheon heals this much (half-hearts). */
	public static final float TRUNCHEON_NIGHT_HEAL = 1.0f;
	/** HOLD C staff spin. */
	public static final double STAFF_SPIN_RADIUS = 3.5;
	public static final float STAFF_SPIN_DAMAGE = 15.0f; // v0.14.4: was 6
	public static final int STAFF_SPIN_COOLDOWN = 80;
	/** SNEAK+C: ground slam / aerial dive slam. */
	public static final double GROUND_SLAM_RADIUS = 4.0;
	public static final float GROUND_SLAM_DAMAGE = 18.0f; // v0.14.4 Crescent Slam: was 6
	/** v0.14.4: the dive slam is 9 + 1.5 per block dived (18 from 6 blocks up), max 36 (was 3 + 1 per block, max 24). */
	public static final float DIVE_SLAM_DAMAGE_PER_BLOCK = 1.5f;
	public static final float DIVE_SLAM_MAX_DAMAGE = 36.0f;
	public static final int SLAM_COOLDOWN = 130;

	// ---------------------------------------------------------------- V: Alters (Phase 5; C before v0.13.21)

	public static final int ALTER_SWITCH_COOLDOWN = 28;
	public static final int ALTER_SPECIAL_COOLDOWN = 400;
	/** MARC "Fist of Khonshu". */
	public static final int FIST_OF_KHONSHU_TICKS = 200;
	public static final float FIST_OF_KHONSHU_COST = 15.0f;
	/** STEVEN "Scholar's Sight". */
	public static final int SCHOLARS_SIGHT_TICKS = 160;
	public static final int SCHOLARS_SIGHT_RADIUS = 16;
	/** JAKE "Vanish". */
	/** Unused since v0.14.4 (Vanish is a toggle with no time limit); kept for reference. */
	public static final int VANISH_TICKS = 160;
	/** Passives. */
	public static final double MARC_ARMOR = 4.0;
	public static final double MARC_MELEE_BONUS = 0.20;
	public static final double MARC_KNOCKBACK_RESISTANCE = 0.3;
	public static final float STEVEN_MELEE_TAKEN = 0.85f;
	public static final double JAKE_SNEAK_SPEED_BONUS = 0.3;
	public static final double JAKE_DETECTION_FACTOR = 0.5;
	public static final float JAKE_BACKSTAB_BONUS = 0.5f;

	// ---------------------------------------------------------------- Z: Khonshu (Phase 6; V before v0.13.21)

	public static final double MOONBEAM_RANGE = 40.0;
	/** v0.14.4: an AoE -- every hostile within 4.5 blocks of the strike point (was a 2-block column). */
	public static final double MOONBEAM_RADIUS = 4.5;
	/** v0.14.4: 35 at the centre (was 10), falling off to {@link #MOONBEAM_EDGE_FACTOR} of that at the edge. */
	public static final float MOONBEAM_DAMAGE = 35.0f;
	/** v0.14.4: 10% of the Vengeance meter. */
	public static final float MOONBEAM_COST = 0.10f * VENGEANCE_MAX;
	public static final int MOONBEAM_COOLDOWN = 100; // v0.14.4: 5 s (was 10 s)
	/** HOLD Z (2 s) Eye of Khonshu: full moon + 100 Vengeance only, once per night. */
	public static final int EYE_HOLD_TICKS = 40;
	/** v0.14.4: a 30-block radius that follows the player (was a one-off 32-block pulse). */
	public static final double EYE_RADIUS = 30.0;
	/** v0.14.4: one minute (was 30 s). Not lunar-scaled. */
	public static final int EYE_DURATION = 1200;
	/** SNEAK+Z Khonshu's Judgement: v0.14.4 lasts 15 s (was 10 s), 20 s cooldown (was 13 s). */
	public static final int JUDGEMENT_TICKS = 300;
	public static final int JUDGEMENT_COOLDOWN = 400;

	// ---------------------------------------------------------------- Phase 3 / 4 extras (R, X, G, Z)

	/** Every Moon Knight ability hit on a boss (TitanCombat.isBoss) is capped at this fraction of its max health. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.05f;

	/** R: a dart turning back flies home at this speed (blocks/tick) and is caught within this distance. */
	public static final double DART_RETURN_SPEED = 1.5;
	public static final double DART_CATCH_DISTANCE = 1.4;
	/** R: a dart that has been out this long without being caught simply fades. */
	public static final int DART_MAX_LIFE_TICKS = 160;

	/** Cape Glide: never sink slower than this (the server's anti-float check needs a real descent). */
	public static final double GLIDE_MIN_SINK = 0.04;
	/** How quickly the glide turns toward where you look (0..1 per tick). */
	public static final double GLIDE_STEER = 0.14;
	/** Looking down: up to this much extra speed (and a steeper dive). */
	public static final double GLIDE_DIVE_SPEED_BONUS = 0.6;
	/** After a glide ends (landing, tap), fall damage stays off this long. */
	public static final int GLIDE_FALL_GRACE_TICKS = 30;
	/** Cape Block: movement speed multiplier change while the cape is held up (-0.5 = half speed, as the old shroud). */
	public static final double CAPE_BLOCK_SPEED_PENALTY = -0.5;

	/** The grappling line takes this long to fly out before the pull starts. */
	public static final int GRAPPLE_LINE_TRAVEL_TICKS = 3;
	/** SNEAK+X at a block: pull speed (blocks/tick), arrival distance, and the longest a pull (or a reel) may last. */
	public static final double GRAPPLE_PULL_SPEED = 1.8; // v0.14.3: was 1.3 (a 100-block line has to arrive)
	public static final double GRAPPLE_ARRIVE_DISTANCE = 1.6;
	public static final int GRAPPLE_MAX_PULL_TICKS = 100; // v0.14.3: was 70
	/** TAP G Grapple Kick: pull speed toward the mob and the reach at which the kick lands. */
	public static final double DIVE_KICK_PULL_SPEED = 1.5;
	public static final double DIVE_KICK_REACH = 1.6;
	/** SNEAK+X reel: the mob stops about this far in front of you; Slowness IV = amplifier 3. */
	public static final double YANK_STOP_DISTANCE = 2.0;
	public static final int YANK_SLOW_AMPLIFIER = 3;

	/** C Truncheon melee: damage per swing (hearts x2) and attack speed modifier (-2.0 = 2 swings/s). */
	public static final float TRUNCHEON_DAMAGE = 7.0f; // v0.14.4: was 6
	public static final float TRUNCHEON_ATTACK_SPEED = -2.0f;
	/** Consecutive hits needed for the combo slam. */
	public static final int TRUNCHEON_COMBO_HITS = 3;
	/** C HOLD staff: stays extended for the spin, and knocks everything outward this hard. */
	public static final int STAFF_SPIN_TICKS = 18;
	public static final double STAFF_SPIN_KNOCKBACK = 0.7;
	/** SNEAK+C ground slam: upward launch and outward shove on every mob in the ring. */
	public static final double GROUND_SLAM_LAUNCH = 0.85;
	public static final double GROUND_SLAM_KNOCKBACK = 0.6;
	/** SNEAK+C in the air: dive speed (blocks/tick) and the longest a dive may last. */
	public static final double DIVE_SLAM_SPEED = 1.8;
	public static final int DIVE_SLAM_MAX_TICKS = 100;
	// ---------------------------------------------------------------- Phase 5 / 6 extras (appended)

	/** Fist of Khonshu: extra knockback resistance on top of Marc's passive while it lasts. */
	public static final double FIST_KNOCKBACK_RESISTANCE = 0.5;
	/** Fist of Khonshu: Strength amplifier (1 = Strength II). */
	public static final int FIST_STRENGTH_AMPLIFIER = 1;
	/** Scholar's Sight: never send more outlines than this (nearest first). */
	public static final int SCHOLARS_SIGHT_MAX_BLOCKS = 400;
	/** Vanish: mobs targeting the player within this radius drop their aggro. */
	public static final double VANISH_AGGRO_RADIUS = 48.0;
	/** Vanish (Jake): mob detection-range factor while vanished (on top of the Invisibility effect). */
	public static final double VANISH_DETECTION_FACTOR = 0.15;
	/** Steven: the chance of one extra roll of a slain mob's loot table (averages out close to Looting +1). */
	public static final float STEVEN_EXTRA_LOOT_CHANCE = 0.5f;
	/** Steven: villager prices drop by this fraction of the base cost (min 1), like a gentler Hero of the Village. */
	public static final double STEVEN_TRADE_DISCOUNT = 0.2;
	/** Jake: a melee hit counts as "from behind" when the attacker is within this many degrees of the target's back. */
	public static final double JAKE_BACKSTAB_ARC_DEGREES = 60.0;
	/** The radial alter picker: hold V this long (client ticks) before it opens -- a little over the server's hold threshold. */
	public static final int ALTER_PICKER_OPEN_TICKS = 12;
	/** Moonbeam: undead take this multiple. */
	public static final float MOONBEAM_UNDEAD_MULTIPLIER = 2.0f;
	/** Eye of Khonshu: effect amplifiers (1 = level II) on the player (Strength, Speed) and on hostiles (Weakness). */
	public static final int EYE_PLAYER_AMPLIFIER = 1;
	public static final int EYE_WEAKNESS_AMPLIFIER = 1;
	/** Eye of Khonshu: how long the skull takes to draw in the sky, and how long it lingers after. */
	public static final int EYE_SKULL_DRAW_TICKS = 40;
	public static final int EYE_SKULL_LINGER_TICKS = 60;
	/** Eye of Khonshu: the skull's height above the player and its size (blocks per skull unit). */
	public static final double EYE_SKULL_HEIGHT = 18.0;
	public static final double EYE_SKULL_SCALE = 1.1;
	/** Khonshu's Judgement: how far the mark reaches. */
	public static final double JUDGEMENT_RANGE = 32.0;
	/** Bosses never take more than this fraction of their max health from one Khonshu hit (like All Might). */
	public static final float KHONSHU_BOSS_MAX_FRACTION = 0.10f;

	// ---------------------------------------------------------------- v0.14.4 truncheon

	/**
	 * The 3-hit truncheon combo: hit 1 a forehand swing (plain), hit 2 a backhand (+{@link #TRUNCHEON_BACKHAND_BONUS}
	 * x power, a shove), hit 3 the overhead smash (+{@link #TRUNCHEON_SLAM_BONUS} x power, {@link #TRUNCHEON_SLAM_KNOCKBACK}
	 * x power knockback, a small lift). Hits must be at least {@link #TRUNCHEON_COMBO_MIN_GAP} ticks apart to advance it
	 * (a fully charged swing is 10), so click-spamming doesn't reach the smash; more than {@link #TRUNCHEON_COMBO_WINDOW}
	 * after the last hit and it starts over at hit 1.
	 */
	public static final float TRUNCHEON_BACKHAND_BONUS = 3.0f;
	public static final double TRUNCHEON_BACKHAND_KNOCKBACK = 0.6;
	public static final double TRUNCHEON_SLAM_LIFT = 0.3;
	public static final int TRUNCHEON_COMBO_MIN_GAP = 6;
	/** The staff spin's whole-body turn (every viewer): one full turn over this many ticks. */
	public static final int STAFF_SPIN_TURN_TICKS = 10;

	// ---------------------------------------------------------------- v0.14.4 (Moon Knight balance pass)

	/** Vengeance regenerates this fraction of the meter per second (0.5%/s = 0.5 points/s) while out of combat. */
	public static final float VENGEANCE_REGEN_FRACTION_PER_SECOND = 0.005f;
	/** "Out of combat" = no damage dealt to or taken from anything for this long (5 s). */
	public static final int OUT_OF_COMBAT_TICKS = 5 * SECOND;
	/** While suited, fall damage is multiplied by this (50% less), on top of the suit's 20% off every hit. */
	public static final float SUIT_FALL_DAMAGE_TAKEN = 0.5f;
	/** Moonbeam AoE falloff: a mob at the very edge of the radius takes this fraction of the centre damage (linear). */
	public static final float MOONBEAM_EDGE_FACTOR = 0.6f;
	/** Eye of Khonshu: debuffs re-applied to every hostile in the radius this often; a random Moonbeam falls this often. */
	public static final int EYE_PULSE_INTERVAL = 20;
	public static final int EYE_STRIKE_INTERVAL = 40;
	/** Khonshu's Judgement: costs 10% Vengeance; the target takes this much per second (x lunar power), and the
	 * player heals every point of damage he deals the judged target (the burn and his own hits) while it lasts. */
	public static final float JUDGEMENT_COST = 0.10f * VENGEANCE_MAX;
	public static final float JUDGEMENT_DAMAGE_PER_SECOND = 10.0f;
	/** Crescent Fan: the darts lock on to the closest hostiles within this range (in line of sight), and turn this hard. */
	public static final double DART_FAN_TARGET_RANGE = 32.0;
	public static final float DART_LOCKED_TURN = 25.0f;
	/** Grapple Kick aim assist: with nothing under the crosshair, the best target within this many degrees of it. */
	public static final double KICK_AIM_CONE_DEGREES = 9.0;
	/** Grapple Kick: the kick lands once the player's box, grown by this much, touches the target's box. */
	public static final double KICK_HIT_INFLATE = 0.8;
	/** Grapple Kick: the pull aims where a moving target will be, leading it by up to this many ticks. */
	public static final int KICK_LEAD_MAX_TICKS = 8;
	/** Steven Grant mines as if his tool had Fortune III (the higher of this and a real Fortune tool). */
	public static final int STEVEN_FORTUNE_LEVEL = 3;
	/** Moon Knight steps straight up full blocks while suited: +0.4 step height (vanilla 0.6 -> 1.0). */
	public static final double SUIT_STEP_HEIGHT_BONUS = 0.4;
}
