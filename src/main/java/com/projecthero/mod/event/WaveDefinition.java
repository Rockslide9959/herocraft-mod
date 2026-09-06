package com.projecthero.mod.event;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * One wave of a wave-based event: what to spawn, how much of it scales with the group, and whether
 * it is a boss wave.
 *
 * <p>Wave <em>tables</em> are code, not save data -- only the current wave <em>number</em> is ever
 * persisted -- so a spawn entry can carry a plain {@link Consumer} to configure the mob it makes
 * (baby, armoured, elite) instead of needing a serializable variant enum.
 *
 * @param number   1-based wave number
 * @param bossWave true if this wave also spawns the event's boss
 * @param spawns   what this wave sends
 */
public record WaveDefinition(int number, boolean bossWave, List<Spawn> spawns) {

	/**
	 * One kind of mob within a wave.
	 *
	 * @param type       the entity type to spawn (a supplier so wave tables can be built as static
	 *                   fields before entity registration has run)
	 * @param baseCount  how many for a single participant
	 * @param perPlayer  extra per additional participant, as a fraction of {@code baseCount}; use a
	 *                   negative value to opt this entry out of group scaling entirely
	 * @param customizer applied to each spawned mob before it enters the world, or {@code null}
	 */
	public record Spawn(Supplier<EntityType<? extends Mob>> type, int baseCount, double perPlayer,
			Consumer<Mob> customizer) {

		public static Spawn of(Supplier<EntityType<? extends Mob>> type, int baseCount) {
			return new Spawn(type, baseCount, Double.NaN, null);
		}

		public static Spawn of(Supplier<EntityType<? extends Mob>> type, int baseCount, Consumer<Mob> customizer) {
			return new Spawn(type, baseCount, Double.NaN, customizer);
		}

		/** How many of this entry to spawn for {@code players} participants. */
		public int count(int players, double defaultPerPlayer) {
			double per = Double.isNaN(perPlayer) ? defaultPerPlayer : perPlayer;
			if (per < 0) {
				return baseCount;
			}
			int extra = Math.max(0, players - 1);
			return Math.max(1, (int) Math.round(baseCount * (1.0 + per * extra)));
		}
	}

	/** Total mobs this wave will spawn for {@code players} participants (bosses excluded). */
	public int totalCount(int players, double defaultPerPlayer) {
		int total = 0;
		for (Spawn s : spawns) {
			total += s.count(players, defaultPerPlayer);
		}
		return total;
	}
}
