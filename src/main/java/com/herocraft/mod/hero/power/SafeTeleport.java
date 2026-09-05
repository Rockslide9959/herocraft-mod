package com.herocraft.mod.hero.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Destination validation shared by every teleport/blink/phase ability (spec section 18). A move only
 * happens if the target is: a loaded chunk, inside the world border, not intersecting collision, has
 * two blocks of head-room, has something to stand near (or the player is flying), and is not lava.
 * Blink-style abilities also scan along the ray and stop at the last safe point before an obstacle
 * instead of punching through walls.
 */
public final class SafeTeleport {
	private SafeTeleport() {
	}

	public static boolean isSafe(ServerLevel level, ServerPlayer player, Vec3 feet) {
		BlockPos pos = BlockPos.containing(feet);
		if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		if (feet.y < level.getMinBuildHeight() + 1 || feet.y > level.getMaxBuildHeight() - 2) {
			return false;
		}
		AABB box = player.getDimensions(player.getPose()).makeBoundingBox(feet);
		if (!level.noCollision(player, box)) {
			return false;
		}
		// suffocation / lava check on the body column
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
				BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			var state = level.getBlockState(p);
			if (state.is(Blocks.LAVA) || state.getFluidState().is(net.minecraft.world.level.material.Fluids.LAVA)) {
				return false;
			}
		}
		return true;
	}

	/** Teleport if the destination is safe. Returns true on success. */
	public static boolean tryTeleport(ServerPlayer player, Vec3 feet) {
		if (!(player.level() instanceof ServerLevel level) || !isSafe(level, player, feet)) {
			return false;
		}
		player.teleportTo(feet.x, feet.y, feet.z);
		player.resetFallDistance();
		player.connection.resetPosition();
		return true;
	}

	/**
	 * Teleport to {@code feet} in {@code targetLevel}, crossing dimensions if necessary. Used by the
	 * Return Marker so a marker set in one dimension always sends you back to <em>that</em> dimension.
	 */
	public static boolean tryTeleport(ServerPlayer player, ServerLevel targetLevel, Vec3 feet) {
		if (targetLevel == player.level()) {
			return tryTeleport(player, feet);
		}
		if (!targetLevel.hasChunkAt(BlockPos.containing(feet))) {
			targetLevel.getChunk(BlockPos.containing(feet)); // force-load the destination chunk
		}
		if (!isSafe(targetLevel, player, feet)) {
			return false;
		}
		player.teleportTo(targetLevel, feet.x, feet.y, feet.z, java.util.Set.of(), player.getYRot(), player.getXRot());
		player.resetFallDistance();
		player.connection.resetPosition();
		return true;
	}

	/**
	 * Blink toward {@code dir} up to {@code maxDistance}: walk the ray in small steps and teleport to
	 * the furthest safe point found (stopping before any wall). Returns true if the player moved.
	 */
	public static boolean blink(ServerPlayer player, Vec3 dir, double maxDistance) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 origin = player.position();
		Vec3 step = dir.normalize().scale(0.5);
		Vec3 best = null;
		for (double d = 0.5; d <= maxDistance; d += 0.5) {
			Vec3 candidate = origin.add(step.scale(d / 0.5));
			if (isSafe(level, player, candidate)) {
				best = candidate;
			} else if (best != null) {
				break; // hit an obstacle after finding a safe stretch
			}
		}
		if (best == null) {
			return false;
		}
		player.teleportTo(best.x, best.y, best.z);
		player.resetFallDistance();
		player.connection.resetPosition();
		return true;
	}

	/** Phase a short distance through a thin wall: only succeeds if the far side is safe AND close. */
	public static boolean phaseThrough(ServerPlayer player, Vec3 dir, double maxDistance) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 origin = player.position();
		for (double d = 1.0; d <= maxDistance; d += 0.5) {
			Vec3 candidate = origin.add(dir.normalize().scale(d));
			if (isSafe(level, player, candidate)) {
				player.teleportTo(candidate.x, candidate.y, candidate.z);
				player.resetFallDistance();
				player.connection.resetPosition();
				return true;
			}
		}
		return false;
	}
}
