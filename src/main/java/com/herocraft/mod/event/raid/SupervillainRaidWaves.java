package com.herocraft.mod.event.raid;

import java.util.List;

import com.herocraft.mod.event.WaveDefinition;

import net.minecraft.world.entity.EntityType;

/**
 * The six waves of the Supervillain Village Raid, as data.
 *
 * <p>Waves 1-5 are pure Minecraft raiders -- this is deliberately a conventional escalating raid so
 * that the wave-6 Supervillain reveal lands. Counts here are the solo baseline; they are scaled per
 * participant at spawn time by {@link com.herocraft.mod.event.EventConfig.SupervillainRaid#waveMobMultiplier}.
 * Ravagers opt out of that scaling (a negative per-player value) for the same reason the Zombie
 * Raid's Juggernauts do: doubling the number of Ravagers for a duo is a far bigger jump than
 * doubling the number of Pillagers.
 *
 * <p>Wave 6 spawns no raiders from this table -- the {@link SupervillainRaid} spawns the boss and its
 * hand-picked escort itself, after the 20-second "something powerful is approaching" pause.
 *
 * <p>v0.9.10 doubled every solo base count (waves 1-5), the same pass the Zombie Raid got in v0.9.9.
 * Ravagers still opt out of per-player scaling (a negative per-player value) but their fixed count was
 * doubled too.
 */
public final class SupervillainRaidWaves {
	private static final List<WaveDefinition> WAVES = List.of(
			wave(1,
					spawn(EntityType.PILLAGER, 16, 0.5)),
			wave(2,
					spawn(EntityType.PILLAGER, 12, 0.5),
					spawn(EntityType.VINDICATOR, 8, 0.5)),
			wave(3,
					spawn(EntityType.PILLAGER, 14, 0.5),
					spawn(EntityType.VINDICATOR, 10, 0.5),
					spawn(EntityType.WITCH, 4, 0.3)),
			wave(4,
					spawn(EntityType.PILLAGER, 16, 0.5),
					spawn(EntityType.VINDICATOR, 12, 0.5),
					spawn(EntityType.EVOKER, 4, 0.25),
					spawn(EntityType.RAVAGER, 2, -1)),
			wave(5,
					spawn(EntityType.PILLAGER, 20, 0.5),
					spawn(EntityType.VINDICATOR, 16, 0.5),
					spawn(EntityType.EVOKER, 6, 0.25),
					spawn(EntityType.WITCH, 4, 0.3),
					spawn(EntityType.RAVAGER, 4, -1)),
			// Wave 6 is the Supervillain. This entry only exists so the wave counter reads "6 / 6".
			new WaveDefinition(6, true, List.of()));

	private SupervillainRaidWaves() {
	}

	public static int count() {
		return WAVES.size();
	}

	public static WaveDefinition get(int number) {
		return WAVES.get(Math.max(1, Math.min(WAVES.size(), number)) - 1);
	}

	private static WaveDefinition wave(int number, WaveDefinition.Spawn... spawns) {
		return new WaveDefinition(number, false, List.of(spawns));
	}

	@SuppressWarnings("unchecked")
	private static WaveDefinition.Spawn spawn(EntityType<?> type, int base, double perPlayer) {
		return new WaveDefinition.Spawn(
				() -> (EntityType<? extends net.minecraft.world.entity.Mob>) type, base, perPlayer, null);
	}
}
