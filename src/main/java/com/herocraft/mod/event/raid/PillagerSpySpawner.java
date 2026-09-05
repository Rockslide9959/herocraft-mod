package com.herocraft.mod.event.raid;

import java.util.Optional;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.EventManager;
import com.herocraft.mod.event.entity.PillagerSpy;
import com.herocraft.mod.event.entity.RaidEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The rare natural spawn of a Pillager Spy (spec section 3).
 *
 * <p>Runs on its own slow cadence -- once every {@link #CHECK_INTERVAL} ticks per player -- and even
 * then only rolls {@link EventConfig.SupervillainRaid#pillagerSpySpawnChance} (5% by default). The
 * result is roughly one spy per real-life hour-and-a-half of a player exploring the overworld near
 * villages: notable when it happens, never routine. Nothing here scans chunks it would have to load,
 * and the spy is spawned at the surface a short distance from the player, heading for the nearest
 * village.
 */
public final class PillagerSpySpawner {
	private static final int CHECK_INTERVAL = 6000; // 5 minutes
	private static final int VILLAGE_RANGE = 160;
	private static final int MIN_SPAWN = 32;
	private static final int MAX_SPAWN = 56;

	private PillagerSpySpawner() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension() != Level.OVERWORLD) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				tryForPlayer(level, player);
			}
		}
	}

	private static void tryForPlayer(ServerLevel level, ServerPlayer player) {
		EventConfig.SupervillainRaid cfg = EventConfig.supervillain();
		if (cfg.pillagerSpySpawnChance <= 0.0 || level.random.nextDouble() >= cfg.pillagerSpySpawnChance) {
			return;
		}
		if (player.isSpectator() || player.isCreative()) {
			return;
		}
		BlockPos pos = player.blockPosition();
		// A spy near a village the player is already standing in defeats the "it enters the village"
		// beat -- and we do not want them spawning constantly inside villages either (section 3).
		if (level.isVillage(pos)) {
			return;
		}
		PoiManager poi = level.getPoiManager();
		Optional<BlockPos> village = poi.findClosest(
				h -> h.is(PoiTypes.MEETING), pos, VILLAGE_RANGE, PoiManager.Occupancy.ANY);
		if (village.isEmpty()) {
			return;
		}
		// No spy or Supervillain Raid already in play near this player.
		if (EventManager.anyActiveNear(level, pos, SupervillainRaid.TYPE_ID, 256)) {
			return;
		}
		if (!level.getEntitiesOfClass(PillagerSpy.class, player.getBoundingBox().inflate(160)).isEmpty()) {
			return;
		}

		BlockPos spawn = surfaceSpawnToward(level, pos, village.get());
		if (spawn == null) {
			return;
		}
		PillagerSpy spy = RaidEntityTypes.PILLAGER_SPY.create(level);
		if (spy == null) {
			return;
		}
		spy.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		spy.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);
		level.addFreshEntity(spy);

		if (level.random.nextDouble() < cfg.pillagerSpyEscortChance) {
			int escort = 1 + level.random.nextInt(2);
			for (int i = 0; i < escort; i++) {
				Pillager guard = EntityType.PILLAGER.create(level);
				if (guard == null) {
					continue;
				}
				guard.moveTo(spawn.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 3,
						spawn.getY(), spawn.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 3, 0f, 0f);
				guard.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);
				guard.setCanJoinRaid(false);
				level.addFreshEntity(guard);
			}
		}
		HeroCraftMod.LOGGER.info("[SupervillainRaid] Pillager Spy spawned at {} (village near {})", spawn, village.get());
	}

	private static BlockPos surfaceSpawnToward(ServerLevel level, BlockPos player, BlockPos village) {
		double dx = village.getX() - player.getX();
		double dz = village.getZ() - player.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0) {
			return null;
		}
		dx /= len;
		dz /= len;
		for (int attempt = 0; attempt < 8; attempt++) {
			double dist = MIN_SPAWN + level.random.nextDouble() * (MAX_SPAWN - MIN_SPAWN);
			double jitter = (level.random.nextDouble() - 0.5) * 0.6;
			int x = (int) (player.getX() + (dx + jitter) * dist);
			int z = (int) (player.getZ() + (dz - jitter) * dist);
			if (!level.isLoaded(new BlockPos(x, player.getY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos candidate = new BlockPos(x, y, z);
			if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
					&& level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), net.minecraft.core.Direction.UP)) {
				return candidate;
			}
		}
		return null;
	}
}
