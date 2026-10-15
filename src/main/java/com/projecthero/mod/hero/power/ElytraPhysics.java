package com.projecthero.mod.hero.power;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user: "make spider-mans web wings function more like elytra please and give this same functionality to moon
 * knights glide"): vanilla's elytra flight as a pure function, shared by the Spider-Man Suit v2's Web Wings and Moon
 * Knight's Cape Glide (both client-simulated, both tested by gametests).
 *
 * <p>{@link #step} is a line-for-line copy of the fall-flying branch of {@code LivingEntity.travel} (1.21.1): a little
 * gravity scaled by how level he is, a falling glide turning its drop into forward speed, pulling up turning forward
 * speed back into height (and so stalling once it runs out), the horizontal velocity eased 10% a tick toward where he is
 * looking (that is how an elytra turns), then the elytra drags (0.99 / 0.98 / 0.99). The result is the velocity of the
 * NEXT move -- feed it the velocity he actually moved with last tick.
 *
 * <p>Vanilla never kicks an elytra flyer for "floating" ({@code isFallFlying()} exempts him); these glides are not
 * vanilla fall-flying, so {@link #antiFloat} makes sure a long, level, high-speed climb still drops a little every few
 * seconds -- far below anything a player notices, enough for the server's 80-tick floating check.
 */
public final class ElytraPhysics {
	/** Vanilla's gravity on a player (blocks / tick^2). */
	public static final double GRAVITY = 0.08;
	/** Vanilla's ordinary airborne drags -- what an end-of-tick hook must undo to recover the moved velocity. */
	public static final double AIR_DRAG_XZ = 0.91;
	public static final double AIR_DRAG_Y = 0.98;
	/** The server's floating check counts a tick as "floating" when he rose or sank less than this. */
	public static final double FLOAT_EPSILON = 0.03125;
	/** After this many floating ticks in a row, the glide sinks for one tick (the server kicks at 80). */
	public static final int FLOAT_LIMIT = 50;

	private ElytraPhysics() {
	}

	/** One tick of vanilla elytra flight with vanilla gravity. */
	public static Vec3 step(Vec3 v, float yawDeg, float pitchDeg) {
		return step(v, yawDeg, pitchDeg, GRAVITY);
	}

	/**
	 * One tick of vanilla elytra flight (LivingEntity.travel, fall-flying branch).
	 *
	 * @param v        the velocity he moved with last tick
	 * @param yawDeg   look yaw (vanilla degrees; 0 = +Z)
	 * @param pitchDeg look pitch (vanilla degrees; + = looking down)
	 * @param gravity  vanilla's gravity for him (0.08, or 0.01 falling with Slow Falling)
	 */
	public static Vec3 step(Vec3 v, float yawDeg, float pitchDeg, double gravity) {
		Vec3 look = Vec3.directionFromRotation(pitchDeg, yawDeg);
		float f = pitchDeg * Mth.DEG_TO_RAD;
		double d = Math.sqrt(look.x * look.x + look.z * look.z);
		double e = v.horizontalDistance();
		double g = look.length();
		double h = Math.cos(f);
		h = h * h * Math.min(1.0, g / 0.4);
		Vec3 out = v.add(0.0, gravity * (-1.0 + h * 0.75), 0.0);
		if (out.y < 0.0 && d > 0.0) {
			double i = out.y * -0.1 * h;
			out = out.add(look.x * i / d, i, look.z * i / d);
		}
		if (f < 0.0f && d > 0.0) {
			double i = e * (double) (-Mth.sin(f)) * 0.04;
			out = out.add(-look.x * i / d, i * 3.2, -look.z * i / d);
		}
		if (d > 0.0) {
			out = out.add((look.x / d * e - out.x) * 0.1, 0.0, (look.z / d * e - out.z) * 0.1);
		}
		return out.multiply(0.99f, 0.98f, 0.99f);
	}

	/** The velocity he moved with, from what vanilla's ordinary airborne step left (gravity + drag undone). */
	public static Vec3 undoAirStep(Vec3 afterVanilla) {
		return new Vec3(afterVanilla.x / AIR_DRAG_XZ, afterVanilla.y / AIR_DRAG_Y + GRAVITY, afterVanilla.z / AIR_DRAG_XZ);
	}

	/** Vanilla's ordinary airborne step after a move of {@code moved} (gravity, then drag) -- for tests. */
	public static Vec3 vanillaAirStep(Vec3 moved) {
		return new Vec3(moved.x * AIR_DRAG_XZ, (moved.y - GRAVITY) * AIR_DRAG_Y, moved.z * AIR_DRAG_XZ);
	}

	/** True if a move of {@code v} counts toward the server's floating check. */
	public static boolean floating(Vec3 v) {
		return v.y >= -FLOAT_EPSILON;
	}

	/**
	 * The floating guard: {@code floatTicks} is how many ticks in a row he has not sunk. At {@link #FLOAT_LIMIT} this tick
	 * sinks just enough to reset the server's count; otherwise {@code v} is returned as is.
	 */
	public static Vec3 antiFloat(Vec3 v, int floatTicks) {
		if (floatTicks >= FLOAT_LIMIT && floating(v)) {
			return new Vec3(v.x, -FLOAT_EPSILON * 1.6, v.z);
		}
		return v;
	}

	/** Gravity for a glider: vanilla's, or Slow Falling's 0.01 while falling. */
	public static double gravityFor(net.minecraft.world.entity.LivingEntity entity) {
		if (entity.getDeltaMovement().y <= 0.0 && entity.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) {
			return Math.min(GRAVITY, 0.01);
		}
		return GRAVITY;
	}
}
