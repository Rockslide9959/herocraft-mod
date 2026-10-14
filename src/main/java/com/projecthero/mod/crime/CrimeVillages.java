package com.projecthero.mod.crime;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.16.2: the small amount of "where" every crime needs -- the village (its bell, the meeting-point POI villagers
 * already gather at), a name for it, a compass hint for the alert, and a legal patch of ground to put a crook on.
 * POI lookups are section-indexed (no block scan), and nothing here loads a chunk.
 */
public final class CrimeVillages {
	private static final String[] FRONT = { "Oak", "Ash", "Mill", "Stone", "Brook", "Elm", "Thorn", "Fox", "Raven", "Wheat", "Amber",
			"Briar", "Cold", "Hazel", "Iron", "Maple", "Willow", "Copper", "Hollow", "Red" };
	private static final String[] BACK = { "field", "ford", "haven", "dale", "brook", "wick", "stead", "bury", "moor", "ton", "vale",
			"cross", "well", "gate" };
	private static final String[] DIRS = { "s", "sw", "w", "nw", "n", "ne", "e", "se" };

	private CrimeVillages() {
	}

	/** The nearest village bell within {@code radius} of {@code pos}, or empty. */
	public static Optional<BlockPos> findBell(ServerLevel level, BlockPos pos, int radius) {
		PoiManager poi = level.getPoiManager();
		return poi.findClosest(h -> h.is(PoiTypes.MEETING), pos, radius, PoiManager.Occupancy.ANY);
	}

	/** A made-up but stable name for the village whose centre is {@code centre} ("Oakford", "Ravenmoor"...). */
	public static String nameFor(BlockPos centre) {
		long h = Mth.getSeed(centre.getX(), 0, centre.getZ());
		int a = (int) Math.floorMod(h, FRONT.length);
		int b = (int) Math.floorMod(h >> 17, BACK.length);
		return FRONT[a] + BACK[b];
	}

	/** "~40 m NE" style hint from {@code fromX/Z} to {@code to}: the lang key for the eight-way direction. */
	public static String directionKey(double fromX, double fromZ, double toX, double toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		// Minecraft yaw: 0 = south (+Z), 90 = west (-X)
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		int idx = Math.floorMod((int) Math.round(yaw / 45.0), 8);
		return "event.projecthero.crime.dir." + DIRS[idx];
	}

	/** Distance rounded to the nearest 5 m (never 0). */
	public static int roundedDistance(double d) {
		return Math.max(5, (int) Math.round(d / 5.0) * 5);
	}

	public static Component hint(double fromX, double fromZ, double toX, double toZ) {
		double d = Math.sqrt((toX - fromX) * (toX - fromX) + (toZ - fromZ) * (toZ - fromZ));
		return Component.translatable("event.projecthero.crime.hint", roundedDistance(d),
				Component.translatable(directionKey(fromX, fromZ, toX, toZ)));
	}

	/** Rings the village bell at {@code bell} if there still is one (villagers who hear it run indoors). */
	public static boolean ringBell(ServerLevel level, BlockPos bell) {
		if (bell == null || !level.isLoaded(bell)) {
			return false;
		}
		BlockState state = level.getBlockState(bell);
		if (state.getBlock() instanceof BellBlock b) {
			return b.attemptToRing(level, bell, (Direction) null);
		}
		return false;
	}

	/**
	 * A standing spot {@code minR}-{@code maxR} blocks from {@code around} (surface within 8 blocks of its height),
	 * or {@code null} after a fixed number of tries. Never loads a chunk.
	 */
	public static BlockPos spotNear(ServerLevel level, BlockPos around, double minR, double maxR, RandomSource random) {
		for (int attempt = 0; attempt < 20; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = Mth.lerp(random.nextDouble(), minR, maxR);
			int x = around.getX() + Mth.floor(Math.cos(angle) * dist);
			int z = around.getZ() + Mth.floor(Math.sin(angle) * dist);
			BlockPos found = groundAt(level, x, z, around.getY(), 8);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** A standing spot in the column {@code x, z} near height {@code refY}, or {@code null}. */
	public static BlockPos groundAt(ServerLevel level, int x, int z, int refY, int drift) {
		BlockPos probe = new BlockPos(x, refY, z);
		if (!level.isLoaded(probe)) {
			return null;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(surface - refY) <= drift && standable(level, new BlockPos(x, surface, z))) {
			return new BlockPos(x, surface, z);
		}
		for (int dy = 0; dy <= drift; dy++) {
			BlockPos up = new BlockPos(x, refY + dy, z);
			if (standable(level, up)) {
				return up;
			}
			BlockPos down = new BlockPos(x, refY - dy, z);
			if (dy > 0 && standable(level, down)) {
				return down;
			}
		}
		return null;
	}

	/** Sturdy floor, two clear non-fluid blocks above. */
	public static boolean standable(ServerLevel level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above())) {
			return false;
		}
		if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
			return false;
		}
		for (int dy = 0; dy < 2; dy++) {
			BlockPos at = pos.above(dy);
			BlockState s = level.getBlockState(at);
			if (!s.getCollisionShape(level, at).isEmpty() || !s.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
