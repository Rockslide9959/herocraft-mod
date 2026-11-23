package com.projecthero.mod.alarm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: how guards and cameras see. A guard sees a player inside a {@link #GUARD_CONE} degree cone (half each side
 * of where its head points) out to {@link #GUARD_RANGE} blocks -- 8 while the player sneaks, 4 while invisible, and half
 * that again in darkness (light below 7 where the player stands) -- with a clear line of sight. Its suspicion meter fills
 * by {@link #fillRate} per tick while it sees you: about 2 s at the edge of its range, a third of a second up close.
 * Walking (not sneaking) within {@link #HEAR_RANGE} blocks behind it is heard: the meter fills slowly even outside the
 * cone. The meter drains by {@link #DRAIN} per tick otherwise.
 */
public final class AlarmVision {
	public static final double GUARD_CONE = 70.0;
	public static final double GUARD_RANGE = 16.0;
	public static final double CAMERA_CONE = 50.0;
	public static final double CAMERA_RANGE = 12.0;
	public static final double HEAR_RANGE = 3.5;
	public static final float DRAIN = 1f / 120f;
	public static final float HEAR_RATE = 1f / 50f;

	private AlarmVision() {
	}

	/** The range {@code base} shrinks to for this player: sneaking halves, invisible quarters, darkness halves again. */
	public static double range(ServerLevel level, Player player, double base) {
		double r = base;
		if (player.isInvisible() || player.hasEffect(MobEffects.INVISIBILITY)) {
			r = base / 4.0;
		} else if (player.isCrouching()) {
			r = base / 2.0;
		}
		if (dark(level, player)) {
			r /= 2.0;
		}
		return r;
	}

	/** Light (block or sky) below 7 where the player stands. */
	public static boolean dark(ServerLevel level, Player player) {
		BlockPos p = BlockPos.containing(player.getEyePosition());
		return level.getMaxLocalRawBrightness(p) < 7;
	}

	/** Meter fill per tick at {@code dist} out of a sight range of {@code range}: 1/6 point-blank down to 1/40 at the edge. */
	public static float fillRate(double dist, double range) {
		double t = range <= 0 ? 1.0 : Mth.clamp(dist / range, 0.0, 1.0);
		return (float) Mth.lerp(t, 1.0 / 6.0, 1.0 / 40.0);
	}

	/** True if a point {@code to} is within {@code coneDeg} (full width) of {@code forward} seen from {@code from}. */
	public static boolean inCone(Vec3 from, Vec3 forward, Vec3 to, double coneDeg) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1.0e-4) {
			return true;
		}
		double cos = d.dot(forward.normalize()) / len;
		return cos >= Math.cos(Math.toRadians(coneDeg / 2.0));
	}

	/** True when no block (visual shape) stands between the two points; fluids are ignored. */
	public static boolean clear(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.world.entity.Entity viewer) {
		ClipContext ctx = viewer == null
				? new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())
				: new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer);
		return level.clip(ctx).getType() == HitResult.Type.MISS;
	}

	/**
	 * How fast {@code guard}'s meter fills toward {@code player} this tick: 0 when it can't see or hear them.
	 */
	public static float guardRate(ServerLevel level, Mob guard, Player player) {
		if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
			return 0f;
		}
		double dist = guard.distanceTo(player);
		double range = range(level, player, GUARD_RANGE);
		Vec3 eye = guard.getEyePosition();
		if (dist <= range) {
			Vec3 look = Vec3.directionFromRotation(guard.getXRot() * 0.3f, guard.getYHeadRot());
			Vec3 target = player.getBoundingBox().getCenter();
			if (inCone(eye, look, target, GUARD_CONE) && guard.getSensing().hasLineOfSight(player)) {
				return fillRate(dist, range);
			}
		}
		if (dist <= HEAR_RANGE && !player.isCrouching() && !player.isInvisible()) {
			return HEAR_RATE;
		}
		return 0f;
	}

	/**
	 * True if a camera at {@code eye} looking along {@code forward} sees {@code player} (cone, range with the
	 * sneak / invisible / darkness rules, clear sight line).
	 */
	public static boolean cameraSees(ServerLevel level, Vec3 eye, Vec3 forward, Player player) {
		if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
			return false;
		}
		Vec3 target = player.getBoundingBox().getCenter();
		double range = range(level, player, CAMERA_RANGE);
		if (target.distanceTo(eye) > range || !inCone(eye, forward, target, CAMERA_CONE)) {
			return false;
		}
		return clear(level, eye, target, null) || clear(level, eye, player.getEyePosition(), null);
	}
}
