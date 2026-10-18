package com.projecthero.mod.miraculous.pegasus;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: every Pegasus teleport goes through here. A spot is only ever used if it is in a LOADED chunk inside the world
 * border, the body fits there (no block in the way: nobody is ever put into a wall or suffocates), no lava touches it,
 * it is under the dimension's ceiling (the Nether's bedrock roof: never on top of it) and above the bottom of the world,
 * and -- for a landing -- there is a floor under it within {@link PegasusConfig#LANDING_FLOOR_SEARCH} blocks (never the
 * void, never the open sky of the End).
 */
public final class PegasusTeleport {
	private PegasusTeleport() {
	}

	/** The highest block y a body may reach into in {@code level} (exclusive): the Nether stops under its roof. */
	public static double ceiling(ServerLevel level) {
		if (level.dimensionType().hasCeiling()) {
			return level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 1;
		}
		return level.getMaxBuildHeight() - 1;
	}

	public static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos);
	}

	/** {@code e}'s body fits with its feet at {@code feet}: loaded, inside the world, under the ceiling, nothing solid, no lava. */
	public static boolean fits(ServerLevel level, Entity e, Vec3 feet) {
		BlockPos pos = BlockPos.containing(feet);
		float h = e.getBbHeight();
		if (!loaded(level, pos) || !loaded(level, BlockPos.containing(feet.x, feet.y + h, feet.z))) {
			return false;
		}
		if (feet.y < level.getMinBuildHeight() + 1 || feet.y + h > ceiling(level)) {
			return false;
		}
		AABB box = e.getDimensions(e.getPose()).makeBoundingBox(feet);
		if (!level.noCollision(e, box)) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (level.getFluidState(p).is(Fluids.LAVA) || level.getFluidState(p).is(Fluids.FLOWING_LAVA)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Where {@code e} lands if it arrives at {@code target}: the target itself or straight under it (down to the first
	 * floor, at most {@link PegasusConfig#LANDING_FLOOR_SEARCH} blocks), else up to {@code lift} blocks higher (a step, a
	 * ledge), else one or two blocks to the side. Null: no safe spot (in a wall, over the void, unloaded, under the roof).
	 */
	public static @Nullable Vec3 landing(ServerLevel level, Entity e, Vec3 target, int lift) {
		for (int r = 0; r <= 2; r++) {
			int dirs = r == 0 ? 1 : 8;
			for (int k = 0; k < dirs; k++) {
				double a = Math.PI * 2 * k / dirs;
				Vec3 at = r == 0 ? target : target.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				for (int up = 0; up <= lift; up++) {
					Vec3 found = drop(level, e, at.add(0, up, 0));
					if (found != null) {
						return found;
					}
				}
			}
		}
		return null;
	}

	/** From {@code start} (which must fit) straight down to the floor; null if the body can't get there. */
	private static @Nullable Vec3 drop(ServerLevel level, Entity e, Vec3 start) {
		if (!fits(level, e, start)) {
			return null;
		}
		BlockPos pos = BlockPos.containing(start);
		if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
			return start; // arriving in water is fine
		}
		for (int i = 0; i <= PegasusConfig.LANDING_FLOOR_SEARCH; i++) {
			BlockPos at = pos.below(i);
			if (!loaded(level, at) || at.getY() <= level.getMinBuildHeight()) {
				return null;
			}
			// the floor's height relative to block `at` (0 = the top of a full block under it); infinite = nothing to stand on
			double floor = level.getBlockFloorHeight(at);
			if (!Double.isInfinite(floor)) {
				double y = at.getY() + floor;
				if (i == 0 && y > start.y + 0.55) {
					return null; // a step up, not a floor under it
				}
				Vec3 feet = new Vec3(start.x, y, start.z);
				return fits(level, e, feet) ? feet : null;
			}
			if (i > 0 && level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)) {
				Vec3 feet = new Vec3(start.x, at.getY(), start.z);
				return fits(level, e, feet) ? feet : null; // floating in water is a landing too
			}
			if (i > 0 && !fits(level, e, new Vec3(start.x, at.getY(), start.z))) {
				return null; // an overhang the body can't get past
			}
		}
		return null;
	}

	/**
	 * The highest spot at most {@code height} blocks straight above {@code e} that it can reach through open air (the column
	 * between must be clear: a cave roof stops it), under the ceiling, loaded. Null if that is less than {@code minHeight}.
	 */
	public static @Nullable Vec3 openAbove(ServerLevel level, Entity e, int height, int minHeight) {
		Vec3 base = e.position();
		Vec3 best = null;
		for (int h = 1; h <= height; h++) {
			Vec3 at = base.add(0, h, 0);
			if (!fits(level, e, at)) {
				break;
			}
			best = at;
		}
		return best == null || best.y - base.y < minHeight ? null : best;
	}

	/**
	 * Moves {@code e} to {@code feet} in {@code level} (crossing worlds only for players) facing {@code yaw}, with
	 * {@code velocity}; fall distance reset. False if it could not go (a non-player across worlds).
	 */
	public static boolean move(Entity e, ServerLevel level, Vec3 feet, float yaw, float pitch, Vec3 velocity) {
		if (e instanceof ServerPlayer sp) {
			sp.teleportTo(level, feet.x, feet.y, feet.z, yaw, pitch);
			sp.resetFallDistance();
			sp.setDeltaMovement(velocity);
			sp.hurtMarked = true;
			sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			return true;
		}
		if (e.level() != level) {
			return false;
		}
		e.teleportTo(feet.x, feet.y, feet.z);
		e.setYRot(yaw);
		if (e instanceof LivingEntity le) {
			le.setYHeadRot(yaw);
			le.yBodyRot = yaw;
		}
		e.resetFallDistance();
		e.setDeltaMovement(velocity);
		e.hurtMarked = true;
		return true;
	}
}
