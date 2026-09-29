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
}
