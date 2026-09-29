package com.projecthero.mod.moonknight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The lunar power multiplier every Moon Knight ability uses (damage, range, duration x power; cooldowns / power).
 * Pure functions of the world clock and the player's position, so the server (which decides) and the client HUD
 * (which shows it) always agree.
 *
 * <ul>
 *   <li>Night, full moon (phase 0): 1.5</li>
 *   <li>Other nights, scaling with the moon: gibbous 1.3, quarter 1.15, crescent 1.0; new moon (phase 4) 0.8</li>
 *   <li>Day, or a dimension with no day/night cycle (Nether, End): 0.7</li>
 *   <li>No sky above (underground / indoors): minus 0.15, never below 0.6</li>
 * </ul>
 */
public final class MoonKnightLunar {
	private MoonKnightLunar() {
	}

	/** The lunar multiplier for this player right now. */
	public static float power(Player player) {
		return power(player.level(), BlockPos.containing(player.getEyePosition()));
	}

	public static float power(Level level, BlockPos pos) {
		float base = isMoonNight(level) ? byPhase(level.getMoonPhase()) : MoonKnightConfig.LUNAR_DAY;
		if (!hasSky(level, pos)) {
			base = Math.max(MoonKnightConfig.LUNAR_MIN, base - MoonKnightConfig.LUNAR_NO_SKY_PENALTY);
		}
		return base;
	}

	/** The night value for a moon phase (0 = full ... 4 = new ... 7 = waxing gibbous). */
	public static float byPhase(int phase) {
		int fromFull = Math.min(Math.floorMod(phase, 8), 8 - Math.floorMod(phase, 8)); // 0 full .. 4 new
		return switch (fromFull) {
			case 0 -> MoonKnightConfig.LUNAR_FULL_MOON;
			case 1 -> MoonKnightConfig.LUNAR_GIBBOUS;
			case 2 -> MoonKnightConfig.LUNAR_QUARTER;
			case 3 -> MoonKnightConfig.LUNAR_CRESCENT;
			default -> MoonKnightConfig.LUNAR_NEW_MOON;
		};
	}

	/** Night in a world that has one (the Nether and the End never do). */
	public static boolean isMoonNight(Level level) {
		return !level.dimensionType().hasFixedTime() && level.dimensionType().hasSkyLight() && level.isNight();
	}

	public static boolean isFullMoonNight(Level level) {
		return isMoonNight(level) && level.getMoonPhase() == 0;
	}

	/** Open sky above this block (the moonlight check the Temple's altar also uses). */
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
