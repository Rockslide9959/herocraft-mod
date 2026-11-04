package com.projecthero.mod.behemoth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;
import com.projecthero.mod.behemoth.entity.BehemothEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The Abyssal Behemoth's rare natural spawn (spec section 3), modelled on {@link com.projecthero.mod.titan.TitanSpawner}:
 * a slow per-player roll rather than any persistent per-player cooldown, plus an explicit dimension-wide
 * dedup scan. Nether-only, never on Peaceful, at most {@code maxActivePerDimension} at a time, with a
 * cooldown after a natural spawn AND after a kill (spec: "killing one should also trigger a cooldown").
 *
 * <p>v0.17.2: the spawn-spot finder ({@link #findSpot} / {@link #isOpenSpace}) is shared with the Abyssal Lure and with
 * the boss's own unstick search ({@link #findOpenNear}).
 *
 * <p>The cooldown map is keyed by {@link ResourceKey} (interned, no world reference) and holds only a
 * {@code long} game-time per dimension -- unlike a cache of entities or levels, this cannot leak a dead
 * world, but it is still cleared by {@code ServerStateReset} on server stop for consistency with every
 * other static piece of server state this mod keeps (see that class's javadoc).
 */
public final class BehemothSpawner {
	private static final Map<ResourceKey<Level>, Long> COOLDOWN_UNTIL = new HashMap<>();

	/** Air (in blocks) kept clear all round its box at a spawn: room to turn and rise without scraping terrain. */
	public static final double SPAWN_MARGIN = 2.0;

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
		Vec3 spawn = findSpot(level, player.position(), cfg.minPlayerDistance, cfg.maxPlayerDistance, false);
		if (spawn == null) {
			return false;
		}
		if (spawnAt(level, spawn, MobSpawnType.NATURAL) == null) {
			return false;
		}
		COOLDOWN_UNTIL.put(level.dimension(), level.getGameTime() + cfg.postSpawnCooldownTicks);
		com.projecthero.mod.ProjectHeroMod.LOGGER.info("[Behemoth] The Abyssal Behemoth naturally spawned at {} in {}", spawn, level.dimension().location());
		return true;
	}

	private static int activeCount(ServerLevel level) {
		return level.getEntitiesOfClass(AbyssalBehemothEntity.class,
				new AABB(-3.0e7, level.getMinBuildHeight(), -3.0e7, 3.0e7, level.getMaxBuildHeight(), 3.0e7)).size();
	}

	/** v0.17.2: one Behemoth with its feet at {@code feet}, facing a random way -- persistent, finalized, added. */
	public static AbyssalBehemothEntity spawnAt(ServerLevel level, Vec3 feet, MobSpawnType why) {
		AbyssalBehemothEntity behemoth = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(level);
		if (behemoth == null) {
			return null;
		}
		behemoth.moveTo(feet.x, feet.y, feet.z, level.random.nextFloat() * 360f, 0f);
		behemoth.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(feet)), why, null);
		behemoth.setPersistenceRequired();
		level.addFreshEntity(behemoth);
		return behemoth;
	}

	/** Every living Behemoth within {@code radius} of {@code at}. */
	public static List<AbyssalBehemothEntity> activeNear(ServerLevel level, Vec3 at, double radius) {
		return level.getEntitiesOfClass(AbyssalBehemothEntity.class, new AABB(at, at).inflate(radius), e -> e.isAlive());
	}

	/** The box a Behemoth with its feet at {@code feet} fills. */
	public static AABB boxAt(Vec3 feet) {
		double h = AbyssalBehemothEntity.SIZE / 2.0;
		return new AABB(feet.x - h, feet.y, feet.z - h, feet.x + h, feet.y + AbyssalBehemothEntity.SIZE, feet.z + h);
	}

	/**
	 * v0.17.2: a spawn needs its WHOLE box (plus {@link #SPAWN_MARGIN}) empty. The old check sampled a 9x5x9 grid every 2
	 * blocks centred on the feet -- but the box rises 9 blocks above the feet, so its top 7 blocks were never looked at and
	 * it spawned with its body in the cavern ceiling ("stuck in blocks"); the y scan (up to y=147) also reached ABOVE the
	 * Nether's bedrock roof, where it sat on the roof and hunted players underneath through it. Now: every block of the
	 * inflated box is collision-free and dry, loaded, and the box ends below the dimension's roof (its logical height).
	 */
	public static boolean isOpenSpace(ServerLevel level, Vec3 feet) {
		return isOpen(level, boxAt(feet).inflate(SPAWN_MARGIN));
	}

	/** {@code box} is loaded, below the roof, above the floor of the world, with no block collision and no liquid in it. */
	public static boolean isOpen(ServerLevel level, AABB box) {
		if (box.minY <= level.getMinBuildHeight() + 1 || box.maxY >= level.getMinBuildHeight() + level.getLogicalHeight() - 1) {
			return false;
		}
		if (!level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			return false;
		}
		return level.noCollision(box) && !level.containsAnyLiquid(box);
	}

	/**
	 * v0.17.2: an open spot (see {@link #isOpenSpace}) {@code minDist}-{@code maxDist} blocks out from {@code around}, scanned
	 * in a band of heights near it (so it never appears three caverns up). With {@code needSight} a spot that can also see
	 * {@code around} is preferred (the lure wants it to rise into view); one that cannot is the fallback.
	 */
	public static Vec3 findSpot(ServerLevel level, Vec3 around, double minDist, double maxDist, boolean needSight) {
		int roof = level.getMinBuildHeight() + level.getLogicalHeight();
		int lowY = Math.max(level.getMinBuildHeight() + 4, (int) Math.floor(around.y) - 12);
		int highY = Math.min(roof - (int) AbyssalBehemothEntity.SIZE - 4, (int) Math.floor(around.y) + 24);
		if (highY < lowY) {
			return null;
		}
		Vec3 fallback = null;
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			double dist = minDist + level.random.nextDouble() * Math.max(0.0, maxDist - minDist);
			double x = Math.floor(around.x + Math.cos(angle) * dist) + 0.5;
			double z = Math.floor(around.z + Math.sin(angle) * dist) + 0.5;
			for (int y = lowY + level.random.nextInt(3); y <= highY; y += 3) {
				Vec3 feet = new Vec3(x, y, z);
				if (!isOpenSpace(level, feet)) {
					continue;
				}
				if (!needSight || sees(level, feet, around)) {
					return feet;
				}
				if (fallback == null) {
					fallback = feet;
				}
				break;
			}
		}
		return fallback;
	}

	/** v0.17.2: an open spot near {@code at} (its own spot first), out to {@code maxDist} -- the unstick search for a wedged Behemoth. */
	public static Vec3 findOpenNear(ServerLevel level, Vec3 at, double maxDist) {
		if (isOpenSpace(level, at)) {
			return at;
		}
		for (double r = 3.0; r <= maxDist; r += 3.0) {
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2 * i / 16.0;
				for (int dy : new int[] { 0, 3, -3, 6, -6, 9, -9 }) {
					Vec3 feet = new Vec3(at.x + Math.cos(a) * r, Math.floor(at.y) + dy, at.z + Math.sin(a) * r);
					if (isOpenSpace(level, feet)) {
						return feet;
					}
				}
			}
		}
		return null;
	}

	/** A clear line from a Behemoth's eye (feet at {@code feet}) to a player standing at {@code target}. */
	public static boolean sees(ServerLevel level, Vec3 feet, Vec3 target) {
		Vec3 eye = feet.add(0, AbyssalBehemothEntity.SIZE * 0.6, 0);
		Vec3 to = target.add(0, 1.5, 0);
		return level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
				.getType() == HitResult.Type.MISS;
	}
}
