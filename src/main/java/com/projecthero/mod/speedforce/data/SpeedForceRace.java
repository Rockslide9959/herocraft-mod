package com.projecthero.mod.speedforce.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user, race ghosts): a Speed Force Speedster's race course -- the START and FINISH gates set with a Speed Force
 * Shard (in one dimension) and the best times run on it, fastest first ({@code projecthero:speed_force_race}: persistent,
 * kept through death, synced to the owner -- the Shift+N menu lists the times). The best run's track (for its ghost) is
 * kept apart in {@link Ghost} (never synced: it can be long). Immutable.
 */
public record SpeedForceRace(String dimension, Optional<BlockPos> start, Optional<BlockPos> finish, List<Integer> best) {
	public static final int BEST_KEPT = 5;
	public static final SpeedForceRace EMPTY = new SpeedForceRace("", Optional.empty(), Optional.empty(), List.of());

	public static final Codec<SpeedForceRace> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("dimension", "").forGetter(SpeedForceRace::dimension),
			BlockPos.CODEC.optionalFieldOf("start").forGetter(SpeedForceRace::start),
			BlockPos.CODEC.optionalFieldOf("finish").forGetter(SpeedForceRace::finish),
			Codec.INT.listOf().optionalFieldOf("best", List.of()).forGetter(SpeedForceRace::best)
	).apply(i, SpeedForceRace::new));

	public SpeedForceRace {
		best = List.copyOf(best);
	}

	/** Both gates are set. */
	public boolean ready() {
		return start.isPresent() && finish.isPresent();
	}

	/** A new course from {@code pos} (no finish, no times). */
	public static SpeedForceRace startAt(String dimension, BlockPos pos) {
		return new SpeedForceRace(dimension, Optional.of(pos), Optional.empty(), List.of());
	}

	public SpeedForceRace withFinish(BlockPos pos) {
		return new SpeedForceRace(dimension, start, Optional.of(pos), List.of());
	}

	/** A finished run of {@code ticks}: into the best times (fastest first, {@link #BEST_KEPT} kept). */
	public SpeedForceRace withTime(int ticks) {
		List<Integer> b = new ArrayList<>(best);
		b.add(ticks);
		b.sort(Integer::compare);
		while (b.size() > BEST_KEPT) {
			b.remove(b.size() - 1);
		}
		return new SpeedForceRace(dimension, start, finish, b);
	}

	/** The fastest time so far (ticks), or -1. */
	public int bestTime() {
		return best.isEmpty() ? -1 : best.get(0);
	}

	/** The best run's track: a world position every {@code sampleTicks} game ticks from the start gate. */
	public record Ghost(List<Vec3> track) {
		public static final Ghost EMPTY = new Ghost(List.of());
		public static final Codec<Ghost> CODEC = RecordCodecBuilder.create(i -> i.group(
				Vec3.CODEC.listOf().optionalFieldOf("track", List.of()).forGetter(Ghost::track)
		).apply(i, Ghost::new));

		public Ghost {
			track = List.copyOf(track);
		}
	}
}
