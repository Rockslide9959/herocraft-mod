package com.herocraft.mod.event;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finding somewhere legal to put a wave mob. Every rule on the spec's "do not spawn mobs..." list
 * (section 15) is a check here: inside solid walls, unintentionally far underground, extremely far
 * away, directly on top of the player, or in an unloaded chunk.
 *
 * <p>Costs a handful of block reads per attempt and gives up after a fixed number of attempts, so a
 * bad site can never turn into an unbounded search. Nothing here loads a chunk: a candidate in an
 * unloaded chunk is rejected outright rather than pulled in, which is what keeps a raid from
 * force-loading terrain around itself.
 */
public final class EventSpawns {
	private static final int ATTEMPTS = 24;
	/** How far above/below the event centre a spawn is allowed to end up. */
	private static final int MAX_VERTICAL_DRIFT = 20;

	private EventSpawns() {
	}

	/**
	 * @param level     the event's level
	 * @param center    the event centre
	 * @param avoid     players to keep clear of (normally the present participants)
	 * @param random    the level's random
	 * @return a legal standing position, or {@code null} if none was found this call
	 */
	public static BlockPos findSpawn(ServerLevel level, BlockPos center, List<ServerPlayer> avoid, RandomSource random) {
		EventConfig.Framework cfg = EventConfig.framework();
		double minR = Math.max(2, cfg.minSpawnDistance);
		double maxR = Math.max(minR + 1, cfg.maxSpawnDistance);
		double playerClearSq = (double) cfg.minSpawnDistanceFromPlayer * cfg.minSpawnDistanceFromPlayer;

		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = Mth.lerp(random.nextDouble(), minR, maxR);
			int x = center.getX() + Mth.floor(Math.cos(angle) * dist);
			int z = center.getZ() + Mth.floor(Math.sin(angle) * dist);

			// Unloaded chunk: reject rather than load it. isLoaded() is a chunk-map lookup, not a load.
			if (!level.isLoaded(new BlockPos(x, center.getY(), z))) {
				continue;
			}

			BlockPos candidate = groundAt(level, x, z, center.getY());
			if (candidate == null) {
				continue;
			}
			boolean tooCloseToSomeone = false;
			for (ServerPlayer player : avoid) {
				if (player.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5) < playerClearSq) {
					tooCloseToSomeone = true;
					break;
				}
			}
			if (!tooCloseToSomeone) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * The standing position at this column: the surface if it is near enough to the event's own height,
	 * otherwise a short vertical search around the event height so a raid started in a ravine, a cave
	 * mouth or a walled base does not fling its mobs onto the roof (or leave them at bedrock).
	 */
	private static BlockPos groundAt(ServerLevel level, int x, int z, int referenceY) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(surface - referenceY) <= MAX_VERTICAL_DRIFT) {
			BlockPos pos = new BlockPos(x, surface, z);
			return standable(level, pos) ? pos : null;
		}
		for (int dy = 0; dy <= MAX_VERTICAL_DRIFT; dy++) {
			BlockPos up = new BlockPos(x, referenceY + dy, z);
			if (standable(level, up)) {
				return up;
			}
			if (dy > 0) {
				BlockPos down = new BlockPos(x, referenceY - dy, z);
				if (standable(level, down)) {
					return down;
				}
			}
		}
		return null;
	}

	/** Solid floor, two blocks of clear non-fluid space above it. */
	private static boolean standable(ServerLevel level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above(1))) {
			return false;
		}
		BlockState floor = level.getBlockState(pos.below());
		if (!floor.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
			return false;
		}
		for (int dy = 0; dy < 2; dy++) {
			BlockPos at = pos.above(dy);
			BlockState state = level.getBlockState(at);
			if (!state.getCollisionShape(level, at).isEmpty() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Place {@code mob} at {@code pos} facing the event centre, and add it to the world. Kept here so
	 * every event spawns its mobs the same way.
	 */
	public static void place(ServerLevel level, Mob mob, BlockPos pos, BlockPos lookAt) {
		float yaw = (float) (Mth.atan2(lookAt.getZ() - pos.getZ(), lookAt.getX() - pos.getX()) * (180.0 / Math.PI)) - 90.0f;
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
		mob.setYHeadRot(yaw);
		level.addFreshEntity(mob);
	}
}
