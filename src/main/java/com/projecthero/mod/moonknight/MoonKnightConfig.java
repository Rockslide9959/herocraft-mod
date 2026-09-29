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
}
