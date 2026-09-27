package com.projecthero.mod.behemoth;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;
import com.projecthero.mod.behemoth.entity.BehemothEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Abyssal Behemoth's rare natural spawn (spec section 3), modelled on {@link com.projecthero.mod.titan.TitanSpawner}:
 * a slow per-player roll rather than any persistent per-player cooldown, plus an explicit dimension-wide
 * dedup scan. Nether-only, never on Peaceful, at most {@code maxActivePerDimension} at a time, with a
 * cooldown after a natural spawn AND after a kill (spec: "killing one should also trigger a cooldown").
 *
 * <p>The cooldown map is keyed by {@link ResourceKey} (interned, no world reference) and holds only a
 * {@code long} game-time per dimension -- unlike a cache of entities or levels, this cannot leak a dead
 * world, but it is still cleared by {@code ServerStateReset} on server stop for consistency with every
 * other static piece of server state this mod keeps (see that class's javadoc).
 */
public final class BehemothSpawner {
	private static final Map<ResourceKey<Level>, Long> COOLDOWN_UNTIL = new HashMap<>();

	private BehemothSpawner() {
	}

	public static void clearSessionState() {
		COOLDOWN_UNTIL.clear();
	}

	/** Called from {@link AbyssalBehemothEntity#die} -- starts the post-kill cooldown for that dimension. */
	public static void onKilled(ServerLevel level) {
		COOLDOWN_UNTIL.put(level.dimension(), level.getGameTime() + BehemothConfig.world().postKillCooldownTicks);
	}

	public static void tick(MinecraftServer server) {
		var cfg = BehemothConfig.world();
		if (!cfg.naturalSpawnEnabled || server.getTickCount() % Math.max(20, cfg.spawnCheckIntervalTicks) != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension() != Level.NETHER || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
				continue;
			}
			Long cooldown = COOLDOWN_UNTIL.get(level.dimension());
			if (cooldown != null && level.getGameTime() < cooldown) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (tryForPlayer(level, player)) {
					break;
				}
			}
		}
	}

	private static boolean tryForPlayer(ServerLevel level, ServerPlayer player) {
		var cfg = BehemothConfig.world();
		if (cfg.spawnChance <= 0.0 || level.random.nextDouble() >= cfg.spawnChance) {
			return false;
		}
		if (player.isSpectator() || player.isCreative()) {
			return false;
		}
		if (cfg.maxActivePerDimension <= 0 || activeCount(level) >= cfg.maxActivePerDimension) {
			return false;
		}
		BlockPos spawn = findSpot(level, player.blockPosition());
		if (spawn == null) {
			return false;
		}
		AbyssalBehemothEntity behemoth = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(level);
		if (behemoth == null) {
			return false;
		}
		behemoth.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		behemoth.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.NATURAL, null);
		behemoth.setPersistenceRequired();
		level.addFreshEntity(behemoth);
		COOLDOWN_UNTIL.put(level.dimension(), level.getGameTime() + cfg.postSpawnCooldownTicks);
		com.projecthero.mod.ProjectHeroMod.LOGGER.info("[Behemoth] The Abyssal Behemoth naturally spawned at {} in {}", spawn, level.dimension().location());
		return true;
	}

	private static int activeCount(ServerLevel level) {
		return level.getEntitiesOfClass(AbyssalBehemothEntity.class,
				new AABB(-3.0e7, level.getMinBuildHeight(), -3.0e7, 3.0e7, level.getMaxBuildHeight(), 3.0e7)).size();
	}

	/** A spot in open Nether air, {@code minPlayerDistance}-{@code maxPlayerDistance} blocks from the player,
	 *  with room to fly (spec: never spawn beside/on top of the player, never inside solid terrain). */
	private static BlockPos findSpot(ServerLevel level, BlockPos player) {
		var cfg = BehemothConfig.world();
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			double dist = cfg.minPlayerDistance + level.random.nextDouble() * (cfg.maxPlayerDistance - cfg.minPlayerDistance);
			int x = (int) (player.getX() + Math.cos(angle) * dist);
			int z = (int) (player.getZ() + Math.sin(angle) * dist);
			// Nether has no reliable "surface" heightmap -- scan a mid-height band for a genuinely open pocket
			// instead (large caverns / open ceilings are common, solid ceiling rock at y=128 is not).
			int baseY = 60 + level.random.nextInt(48);
			for (int attemptY = 0; attemptY < 6; attemptY++) {
				int y = net.minecraft.util.Mth.clamp(baseY + attemptY * 8, level.getMinBuildHeight() + 8, level.getMaxBuildHeight() - 12);
				BlockPos candidate = new BlockPos(x, y, z);
				if (!level.isLoaded(candidate)) {
					continue;
				}
				if (isOpenPocket(level, candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	/** A generous open-air pocket (spec: "prefer large open Nether areas", "enough open air for the entity"). */
	private static boolean isOpenPocket(ServerLevel level, BlockPos center) {
		int r = 4;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				for (int dy = -2; dy <= 2; dy += 2) {
					BlockPos p = center.offset(dx, dy, dz);
					if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}
}
