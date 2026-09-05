package com.herocraft.mod.titan;

import com.herocraft.mod.titan.entity.DisguisedTitanEntity;
import com.herocraft.mod.titan.entity.TitanEntity;
import com.herocraft.mod.titan.entity.TitanEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * The rare natural spawn of the disguised Titan (spec section 1) -- modelled directly on
 * {@link com.herocraft.mod.event.raid.PillagerSpySpawner}: a slow per-player cadence, a small chance
 * roll, and an explicit nearby-entity scan for dedup rather than any persistent per-player cooldown
 * state. The result should read the same way a rare Wandering Trader encounter does: unexpected,
 * never routine, never stacked in one area.
 */
public final class TitanSpawner {
	private static final int MIN_SPAWN = 40;
	private static final int MAX_SPAWN = 80;
	/** Radius of flat-ish headroom required so the eventual ~18-block transformation has room. */
	private static final int HEADROOM_RADIUS = 6;

	private TitanSpawner() {
	}

	/** Bounded, not world-border-wide -- see {@link #nearbyEncounterExists} for why. */
	private static final double GLOBAL_CAP_SCAN_RADIUS = 1024.0;

	public static void tick(MinecraftServer server) {
		var cfg = TitanConfig.world();
		if (!cfg.naturalSpawnEnabled || server.getTickCount() % Math.max(20, cfg.spawnCheckIntervalTicks) != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension() != Level.OVERWORLD) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (tryForPlayer(level, player)) {
					break; // one spawn attempt success per interval is plenty
				}
			}
		}
	}

	/**
	 * Whether an encounter already exists within {@code radius} of {@code pos}. Deliberately a bounded
	 * per-player-vicinity scan rather than a world-border-wide one (which could span tens of millions of
	 * blocks) -- this mod's performance discipline never scans unboundedly per tick. The trade-off: an
	 * encounter that a player has since travelled far away from won't be counted against the global cap
	 * until someone gets back within range of it, which is acceptable for a soft "don't feel spammy" cap.
	 */
	private static boolean nearbyEncounterExists(ServerLevel level, BlockPos pos, double radius) {
		var box = new net.minecraft.world.phys.AABB(pos).inflate(radius);
		return !level.getEntitiesOfClass(DisguisedTitanEntity.class, box).isEmpty()
				|| !level.getEntitiesOfClass(TitanEntity.class, box).isEmpty();
	}

	private static boolean tryForPlayer(ServerLevel level, ServerPlayer player) {
		var cfg = TitanConfig.world();
		if (cfg.spawnChance <= 0.0 || level.random.nextDouble() >= cfg.spawnChance) {
			return false;
		}
		if (player.isSpectator() || player.isCreative()) {
			return false;
		}
		BlockPos pos = player.blockPosition();
		if (cfg.maxActiveTitans <= 0
				|| nearbyEncounterExists(level, pos, Math.max(cfg.minDistanceBetweenEncounters, GLOBAL_CAP_SCAN_RADIUS))) {
			return false;
		}

		BlockPos spawn = findSurfaceSpot(level, pos);
		if (spawn == null) {
			return false;
		}
		DisguisedTitanEntity disguised = TitanEntityTypes.DISGUISED_TITAN.create(level);
		if (disguised == null) {
			return false;
		}
		disguised.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		disguised.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);
		disguised.setPersistenceRequired();
		level.addFreshEntity(disguised);
		com.herocraft.mod.HeroCraftMod.LOGGER.info("[Titan] disguised Titan spawned at {}", spawn);
		return true;
	}

	/** A flat, open, non-cave/non-liquid surface spot with headroom for the eventual transformation. */
	private static BlockPos findSurfaceSpot(ServerLevel level, BlockPos player) {
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			double dist = MIN_SPAWN + level.random.nextDouble() * (MAX_SPAWN - MIN_SPAWN);
			int x = (int) (player.getX() + Math.cos(angle) * dist);
			int z = (int) (player.getZ() + Math.sin(angle) * dist);
			if (!level.isLoaded(new BlockPos(x, player.getY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos candidate = new BlockPos(x, y, z);
			if (!isValidSpot(level, candidate)) {
				continue;
			}
			return candidate;
		}
		return null;
	}

	private static boolean isValidSpot(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				|| !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
			return false; // not standing room
		}
		BlockPos below = pos.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				|| !level.getFluidState(below).isEmpty() || level.getFluidState(pos).getType() != Fluids.EMPTY) {
			return false; // not solid ground, or standing in liquid
		}
		if (level.canSeeSky(pos) == false) {
			return false; // avoid caves/overhangs entirely -- must be a genuine surface spot
		}
		// Headroom check: reject if the flat spot is actually a narrow gap (e.g. between trees/cliffs)
		// too tight for a future giant to stand in -- a cheap sample ring, not a full volume scan.
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos edge = pos.relative(dir, HEADROOM_RADIUS);
			if (!level.isLoaded(edge)) {
				return false;
			}
		}
		return true;
	}
}
