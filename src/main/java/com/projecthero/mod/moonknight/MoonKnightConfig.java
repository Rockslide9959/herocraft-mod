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

	/** Night, sky visible, full moon (moon phase 0). */
	public static final float LUNAR_FULL_MOON = 1.5f;
	/** Night: the gibbous moons (phases 1 and 7). The quarter and crescent values follow smoothly below. */
	public static final float LUNAR_GIBBOUS = 1.3f;
	/** Night: the half moons (phases 2 and 6). */
	public static final float LUNAR_QUARTER = 1.15f;
	/** Night: the crescents (phases 3 and 5). */
	public static final float LUNAR_CRESCENT = 1.0f;
	/** Night, new moon (phase 4). */
	public static final float LUNAR_NEW_MOON = 0.8f;
	/** Daytime (and dimensions with no day/night cycle). */
	public static final float LUNAR_DAY = 0.7f;
	/** Subtracted when the sky is not visible (underground / indoors)... */
	public static final float LUNAR_NO_SKY_PENALTY = 0.15f;
	/** ...but never below this. */
	public static final float LUNAR_MIN = 0.6f;

	// ---------------------------------------------------------------- vengeance

	public static final float VENGEANCE_MAX = 100.0f;
	/** Starting Vengeance after the ritual (or a /moonknight grant). */
	public static final float VENGEANCE_START = 50.0f;
	/** Starting Vengeance when the ritual is completed under a full moon. */
	public static final float VENGEANCE_START_FULL_MOON = 100.0f;
	/** Killing a hostile mob that was targeting a villager, wandering trader, iron golem or another player. */
	public static final float VENGEANCE_PROTECTOR_KILL = 5.0f;
	/** Any other hostile mob kill at night. */
	public static final float VENGEANCE_NIGHT_KILL = 2.0f;
	/** Any other hostile mob kill during the day. */
	public static final float VENGEANCE_DAY_KILL = 1.0f;
	/** No hostile kill for this long (two in-game days) and Vengeance starts to drain. */
	public static final long VENGEANCE_IDLE_BEFORE_DRAIN = 2L * DAY_TICKS;
	/** How fast it drains once it does: one point every 30 seconds. */
	public static final float VENGEANCE_DRAIN_PER_SECOND = 1.0f / 30.0f;

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
	/** Un-transforming: the bandages unwind for this long. */
	public static final int UNTRANSFORM_TICKS = 10;
	/** Anti-spam gate on H. */
	public static final int TOGGLE_DEBOUNCE_TICKS = 15;

	// ---------------------------------------------------------------- keys: tap / hold / sneak (Phase 3+)

	/** A key held this long counts as a HOLD instead of a TAP. */
	public static final int HOLD_THRESHOLD_TICKS = 10;

	// ---------------------------------------------------------------- R: Crescent Darts (Phase 3)

	public static final float DART_DAMAGE = 5.0f;
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
	public static final int DART_FAN_COUNT = 3;
	public static final int DART_FAN_COUNT_FULL_MOON = 5;
	public static final float DART_FAN_SPREAD_DEGREES = 12.0f;
	public static final int DART_FAN_COOLDOWN = 100;
	/** SNEAK+R Moon Mark: the target glows and takes +30% from the Moon Knight for 10 s. */
	public static final int MOON_MARK_TICKS = 200;
	public static final float MOON_MARK_BONUS = 0.30f;
	public static final int MOON_MARK_COOLDOWN = 240;

	// ---------------------------------------------------------------- X: Cape (Phase 3)

	/** Cape Glide: horizontal speed along the look (blocks/tick) and the slowest allowed fall. */
	public static final double GLIDE_SPEED = 0.55;
	public static final double GLIDE_SINK = 0.06;
	/** At night glides carry further: the sink rate is divided by the lunar power. */
	public static final boolean GLIDE_BETTER_AT_NIGHT = true;
	/** HOLD X Cape Shroud: damage taken multipliers and the slow. */
	public static final float SHROUD_PROJECTILE_FACTOR = 0.40f;
	public static final float SHROUD_MELEE_FACTOR = 0.75f;
	public static final int SHROUD_MAX_TICKS = 100;
	public static final int SHROUD_COOLDOWN = 160;
	/** SNEAK+X Shadow Step: a backward dash and invisibility. */
	public static final double SHADOW_STEP_DISTANCE = 5.0;
	public static final int SHADOW_STEP_INVIS_TICKS = 60;
	public static final int SHADOW_STEP_COOLDOWN = 240;

	// ---------------------------------------------------------------- G: Grappling Line (Phase 4)

	public static final double GRAPPLE_RANGE = 24.0;
	public static final int GRAPPLE_COOLDOWN = 60;
	/** HOLD G: grapple to a mob and dive-kick it on arrival. */
	public static final float DIVE_KICK_DAMAGE = 8.0f;
	public static final double DIVE_KICK_KNOCKBACK = 1.4;
	public static final int DIVE_KICK_COOLDOWN = 160;
	/** SNEAK+G Yank: pull the target to you and stun it (Slowness IV). */
	public static final int YANK_STUN_TICKS = 30;
	public static final int YANK_COOLDOWN = 200;

	// ---------------------------------------------------------------- Z: Truncheon / Staff (Phase 4)

	/** Every 3rd consecutive truncheon hit within this window is a slam. */
	public static final int TRUNCHEON_COMBO_WINDOW = 30;
	public static final float TRUNCHEON_SLAM_BONUS = 4.0f;
	public static final double TRUNCHEON_SLAM_KNOCKBACK = 1.2;
	/** Hitting mobs at night with the truncheon heals this much (half-hearts). */
	public static final float TRUNCHEON_NIGHT_HEAL = 1.0f;
	/** HOLD Z staff spin. */
	public static final double STAFF_SPIN_RADIUS = 3.5;
	public static final float STAFF_SPIN_DAMAGE = 6.0f;
	public static final int STAFF_SPIN_COOLDOWN = 120;
	/** SNEAK+Z: ground slam / aerial dive slam. */
	public static final double GROUND_SLAM_RADIUS = 4.0;
	public static final float GROUND_SLAM_DAMAGE = 6.0f;
	public static final float DIVE_SLAM_DAMAGE_PER_BLOCK = 1.0f;
	public static final float DIVE_SLAM_MAX_DAMAGE = 24.0f;
	public static final int SLAM_COOLDOWN = 200;

	// ---------------------------------------------------------------- C: Alters (Phase 5)

	public static final int ALTER_SWITCH_COOLDOWN = 40;
	public static final int ALTER_SPECIAL_COOLDOWN = 600;
	/** MARC "Fist of Khonshu". */
	public static final int FIST_OF_KHONSHU_TICKS = 200;
	public static final float FIST_OF_KHONSHU_COST = 15.0f;
	/** STEVEN "Scholar's Sight". */
	public static final int SCHOLARS_SIGHT_TICKS = 160;
	public static final int SCHOLARS_SIGHT_RADIUS = 16;
	/** JAKE "Vanish". */
	public static final int VANISH_TICKS = 160;
	/** Passives. */
	public static final double MARC_ARMOR = 4.0;
	public static final double MARC_MELEE_BONUS = 0.20;
	public static final double MARC_KNOCKBACK_RESISTANCE = 0.3;
	public static final float STEVEN_MELEE_TAKEN = 0.85f;
	public static final double JAKE_SNEAK_SPEED_BONUS = 0.3;
	public static final double JAKE_DETECTION_FACTOR = 0.5;
	public static final float JAKE_BACKSTAB_BONUS = 0.5f;

	// ---------------------------------------------------------------- V: Khonshu (Phase 6)

	public static final double MOONBEAM_RANGE = 40.0;
	public static final double MOONBEAM_RADIUS = 2.0;
	public static final float MOONBEAM_DAMAGE = 10.0f;
	public static final float MOONBEAM_COST = 10.0f;
	public static final int MOONBEAM_COOLDOWN = 300;
	/** HOLD V (2 s) Eye of Khonshu: full moon + 100 Vengeance only, once per night. */
	public static final int EYE_HOLD_TICKS = 40;
	public static final double EYE_RADIUS = 32.0;
	public static final int EYE_DURATION = 600;
	/** SNEAK+V Khonshu's Judgement. */
	public static final int JUDGEMENT_TICKS = 200;
	public static final float JUDGEMENT_REFUND = 20.0f;
	public static final float JUDGEMENT_HEAL = 6.0f;
	public static final int JUDGEMENT_COOLDOWN = 400;

	// ---------------------------------------------------------------- Phase 3 / 4 extras (R, X, G, Z)

	/** Every Moon Knight ability hit on a boss (TitanCombat.isBoss) is capped at this fraction of its max health. */
	public static final float BOSS_MAX_FRACTION_PER_HIT = 0.05f;

	/** R: a dart turning back flies home at this speed (blocks/tick) and is caught within this distance. */
	public static final double DART_RETURN_SPEED = 1.5;
	public static final double DART_CATCH_DISTANCE = 1.4;
	/** R: a dart that has been out this long without being caught simply fades. */
	public static final int DART_MAX_LIFE_TICKS = 160;
	/** R HOLD: an uncharged fan throws at this fraction of full speed and damage (a full 1.5 s charge = 1.0). */
	public static final float DART_FAN_MIN_CHARGE = 0.6f;

	/** X TAP Cape Glide: never sink slower than this (the server's anti-float check needs a real descent). */
	public static final double GLIDE_MIN_SINK = 0.04;
	/** How quickly the glide turns toward where you look (0..1 per tick). */
	public static final double GLIDE_STEER = 0.14;
	/** Looking down: up to this much extra speed (and a steeper dive). */
	public static final double GLIDE_DIVE_SPEED_BONUS = 0.6;
	/** After a glide ends (landing, tap), fall damage stays off this long. */
	public static final int GLIDE_FALL_GRACE_TICKS = 30;
	/** X HOLD Cape Shroud: movement speed multiplier change while wrapped (-0.5 = half speed). */
	public static final double SHROUD_SPEED_PENALTY = -0.5;

	/** G: the line takes this long to fly out before the pull starts. */
	public static final int GRAPPLE_LINE_TRAVEL_TICKS = 3;
	/** G TAP: pull speed (blocks/tick), arrival distance, and the longest a pull may last. */
	public static final double GRAPPLE_PULL_SPEED = 1.3;
	public static final double GRAPPLE_ARRIVE_DISTANCE = 1.6;
	public static final int GRAPPLE_MAX_PULL_TICKS = 50;
	/** G HOLD dive kick: pull speed toward the mob and the reach at which the kick lands. */
	public static final double DIVE_KICK_PULL_SPEED = 1.5;
	public static final double DIVE_KICK_REACH = 1.6;
	/** SNEAK+G Yank: the mob lands about this far in front of you; Slowness IV = amplifier 3. */
	public static final double YANK_STOP_DISTANCE = 2.0;
	public static final int YANK_SLOW_AMPLIFIER = 3;
	/** How long the yank's rope stays drawn. */
	public static final int YANK_LINE_TICKS = 8;

	/** Z Truncheon melee: damage per swing (hearts x2) and attack speed modifier (-2.0 = 2 swings/s). */
	public static final float TRUNCHEON_DAMAGE = 6.0f;
	public static final float TRUNCHEON_ATTACK_SPEED = -2.0f;
	/** Consecutive hits needed for the combo slam. */
	public static final int TRUNCHEON_COMBO_HITS = 3;
	/** Z HOLD staff: stays extended for the spin, and knocks everything outward this hard. */
	public static final int STAFF_SPIN_TICKS = 18;
	public static final double STAFF_SPIN_KNOCKBACK = 0.7;
	/** SNEAK+Z ground slam: upward launch and outward shove on every mob in the ring. */
	public static final double GROUND_SLAM_LAUNCH = 0.85;
	public static final double GROUND_SLAM_KNOCKBACK = 0.6;
	/** SNEAK+Z in the air: dive speed (blocks/tick) and the longest a dive may last. */
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
	/** The radial alter picker: hold C this long (client ticks) before it opens -- a little over the server's hold threshold. */
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
}
