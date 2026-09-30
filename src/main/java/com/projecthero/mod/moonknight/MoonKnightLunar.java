package com.projecthero.mod.moonknight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The lunar power multiplier every Moon Knight ability uses (damage, range, duration x power; cooldowns / power).
 * Pure functions of the world clock, so the server (which decides) and the client HUD (which shows it) always agree.
 *
 * <p>v0.14.4: exactly three states ({@link State}) instead of a per-phase table with a no-sky penalty:
 * <ul>
 *   <li>{@link State#DAY} x0.7 -- daytime, and always in the Nether and the End (no moon there);</li>
 *   <li>{@link State#NIGHT} x1.0 -- any night that is not a full moon (the base numbers are the night numbers);</li>
 *   <li>{@link State#FULL_MOON} x1.5 -- night under a full moon (moon phase 0).</li>
 * </ul>
 * What else each state unlocks is unchanged: NIGHT and FULL MOON both count as night (homing darts, the Moonbeam,
 * the Truncheon's night heal, +3 Vengeance kills, longer glides); FULL MOON alone opens the Eye of Khonshu and
 * recharges Khonshu's Resurrection.
 */
public final class MoonKnightLunar {
	private MoonKnightLunar() {
	}

	/** The three lunar power states, weakest to strongest. */
	public enum State {
		DAY("day"),
		NIGHT("night"),
		FULL_MOON("full_moon");

		private final String id;

		State(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** Lang key of the state's name (HUD / status). */
		public String nameKey() {
			return "hud.projecthero.moon_knight.lunar." + id;
		}

		public float power() {
			return switch (this) {
				case DAY -> MoonKnightConfig.LUNAR_DAY;
				case NIGHT -> MoonKnightConfig.LUNAR_NIGHT;
				case FULL_MOON -> MoonKnightConfig.LUNAR_FULL_MOON;
			};
		}

		public boolean isNight() {
			return this != DAY;
		}
	}

	/**
	 * The state for a world with / without a real sky cycle, night or not, and a moon phase. Pure, for the gametests.
	 *
	 * @param hasMoon false for the Nether and the End (a fixed-time or skylight-less dimension): always DAY
	 */
	public static State resolve(boolean hasMoon, boolean night, int moonPhase) {
		if (!hasMoon || !night) {
			return State.DAY;
		}
		return Math.floorMod(moonPhase, 8) == 0 ? State.FULL_MOON : State.NIGHT;
	}

	/** Does this dimension have a moon at all? (The Nether and the End do not.) */
	public static boolean hasMoon(Level level) {
		return !level.dimensionType().hasFixedTime() && level.dimensionType().hasSkyLight();
	}

	public static State state(Level level) {
		return resolve(hasMoon(level), level.isNight(), level.getMoonPhase());
	}

	public static State state(Player player) {
		return state(player.level());
	}

	/** The lunar multiplier for this player right now. */
	public static float power(Player player) {
		return state(player.level()).power();
	}

	/** v0.14.4: the position no longer matters (no sky penalty); kept so the command / HUD call sites read the same. */
	public static float power(Level level, BlockPos pos) {
		return state(level).power();
	}

	/** Night in a world that has one (the Nether and the End never do). */
	public static boolean isMoonNight(Level level) {
		return state(level).isNight();
	}

	public static boolean isFullMoonNight(Level level) {
		return state(level) == State.FULL_MOON;
	}

	/** Open sky above this block (the moonlight check the Temple's altar uses). */
	public static boolean hasSky(Level level, BlockPos pos) {
		return level.dimensionType().hasSkyLight() && level.canSeeSky(pos);
	}

	/** Which lunar cycle (8 days, starting on a full moon) the world is in. */
	public static long moonCycle(Level level) {
		return Math.floorDiv(level.getDayTime(), MoonKnightConfig.MOON_CYCLE_TICKS);
	}

	/** Scale a base amount (damage, range, duration) by the lunar power. */
	public static float scale(float base, float power) {
		return base * power;
	}

	/** Scale a base cooldown: divided by the lunar power, never below one tick. */
	public static int cooldown(int baseTicks, float power) {
		return Math.max(1, Math.round(baseTicks / Math.max(0.1f, power)));
	}
}
