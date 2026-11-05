package com.projecthero.mod.omnitrix.alien.cannonbolt;

import com.projecthero.mod.omnitrix.Omnitrix;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 round 3 (user: "change his ball form speed, make him charge up momentum to go faster, at 0 momentum he moves at 10 blocks
 * per second, at max momentum he moves at 25 blocks per second, he doesn't slow down when going up blocks or fall in the air or
 * turning, don't add a bar for this, increase his ball collision damage based on his momentum, make it 10 at 0 momentum and 20 at max
 * momentum, it should take him like 3 seconds of sprinting in a straight line to get to max momentum").
 *
 * <p>The owner's client moves the player, so the roll is simulated there ({@code CannonboltRollMixin} swaps vanilla's walking for
 * {@link #travel}): his horizontal speed is SET each tick, never accelerated, so ledges, air time and turns cannot bleed any of it.
 * The server reads his Momentum back from how fast he really moved ({@link #momentumFromSpeed}), which is what the Ram hits with.
 * The pure numbers live here so the gametests can check them; nothing below touches a client class.
 */
public final class CannonboltRoll {
	/** Blocks a tick at no Momentum and at full Momentum (10 and 25 blocks a second). */
	public static final double SPEED_MIN = 0.5, SPEED_MAX = 1.25;
	/** Ticks of sprinting from none to full (3 s). */
	public static final int BUILD_TICKS = 60;
	/** Ticks with no key held on the ground from full to none. */
	public static final int BLEED_TICKS = 15;
	/** The Ram at no Momentum and at full Momentum. */
	public static final float RAM_MIN = 10f, RAM_MAX = 20f;
	/** How much of the way to the keys' heading he turns each tick (a heavy ball, though the turn costs no speed). */
	public static final double TURN = 0.55;

	/** The owner's Momentum, 0..1 (one local player per client). */
	private static double momentum;
	private static Vec3 heading = Vec3.ZERO;
	/** The Spin Charge's banked Momentum while C is held (0 = not charging). */
	private static double wound;
	/** A move had him last tick: he leaves it at the speed and heading it left him, whatever they were going in. */
	private static boolean driven;

	private CannonboltRoll() {
	}

	public static double speedAt(double momentum) {
		return SPEED_MIN + (SPEED_MAX - SPEED_MIN) * Mth.clamp(momentum, 0.0, 1.0);
	}

	public static float ramDamage(double momentum) {
		return RAM_MIN + (RAM_MAX - RAM_MIN) * (float) Mth.clamp(momentum, 0.0, 1.0);
	}

	/** The Momentum that horizontal speed (blocks a tick) means: how the server, which never sees his keys, reads it. */
	public static double momentumFromSpeed(double speed) {
		return Mth.clamp((speed - SPEED_MIN) / (SPEED_MAX - SPEED_MIN), 0.0, 1.0);
	}

	/** One tick of Momentum: a sprint builds it, a hold keeps it, letting go on the ground bleeds it and a wall ends it. */
	public static double step(double momentum, boolean keys, boolean sprinting, boolean grounded, boolean hitWall) {
		if (hitWall) {
			return 0.0;
		}
		if (keys) {
			return sprinting ? Math.min(1.0, momentum + 1.0 / BUILD_TICKS) : momentum;
		}
		return grounded ? Math.max(0.0, momentum - 1.0 / BLEED_TICKS) : momentum;
	}

	/** Gametests and the client's ball: the owner's Momentum on this client. */
	public static double momentum() {
		return momentum;
	}

	/** The way vanilla's {@code moveRelative} turns the keys into a world direction (unit length; zero with no keys). */
	public static Vec3 keysToWorld(Vec3 input, float yaw) {
		double len = input.x * input.x + input.z * input.z;
		if (len < 1.0e-7) {
			return Vec3.ZERO;
		}
		len = Math.sqrt(len);
		double x = input.x / len, z = input.z / len;
		float sin = Mth.sin(yaw * Mth.DEG_TO_RAD), cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
		return new Vec3(x * cos - z * sin, 0, z * cos + x * sin);
	}

	/** Swings {@code from} towards {@code to}; a full reverse has no halfway, so it just flips. */
	public static Vec3 turn(Vec3 from, Vec3 to) {
		if (from.lengthSqr() < 1.0e-6) {
			return to;
		}
		Vec3 mid = from.scale(1.0 - TURN).add(to.scale(TURN));
		return mid.lengthSqr() < 1.0e-4 ? to : mid.normalize();
	}

	/**
	 * The owner's client, each tick he is rolled up (from {@code CannonboltRollMixin} at the head of {@code Player.travel}): sets his
	 * horizontal speed and answers the input vanilla should go on to use -- none while the roll has him.
	 */
	public static Vec3 travel(Player p, Vec3 input) {
		int flags = Omnitrix.state(p).kitFlags;
		Vec3 v = p.getDeltaMovement();
		if ((flags & CannonboltKit.F_CHARGING) != 0) {
			// the Spin Charge: he spins on the spot, WASD does nothing, and Momentum winds up
			wound = Math.min(1.0, wound + 1.0 / CannonboltKit.SPIN_CHARGE_FILL);
			momentum = wound;
			p.setDeltaMovement(0, v.y, 0);
			return Vec3.ZERO;
		}
		if (wound > 0) {
			// C came up: rolling away with W held he goes at what he wound up; with no W it was all for nothing
			boolean forward = input.z > 1.0e-5;
			momentum = forward ? wound : 0.0;
			wound = 0;
			heading = forward ? keysToWorld(new Vec3(0, 0, 1), p.getYRot()) : Vec3.ZERO;
		}
		if ((flags & CannonboltKit.F_DRIVEN) != 0) {
			// one of his moves is driving him: the keys do nothing and he comes out of it at the speed it left him
			Vec3 flat = new Vec3(v.x, 0, v.z);
			if (flat.lengthSqr() > 1.0e-4) {
				heading = flat.normalize();
				momentum = momentumFromSpeed(flat.length());
				if ((flags & CannonboltKit.F_RAMPAGE) != 0) {
					// a tick the server's velocity arrives late must not show as a stumble: the Rampage is one even roll
					momentum = 1.0;
					p.setDeltaMovement(heading.x * SPEED_MAX, v.y, heading.z * SPEED_MAX);
				}
			}
			driven = true;
			return Vec3.ZERO;
		}
		if (driven) {
			driven = false;
			Vec3 flat = new Vec3(v.x, 0, v.z);
			momentum = momentumFromSpeed(flat.length());
			heading = flat.lengthSqr() > 1.0e-4 ? flat.normalize() : Vec3.ZERO;
		}
		if ((flags & CannonboltKit.F_SPIN) != 0 || p.isInWater() || p.isInLava() || p.getAbilities().flying || p.isPassenger()) {
			heading = Vec3.ZERO;
			return input; // the Shell Spin holds him on the spot; water and lava drag at him; creative flight is its own thing
		}
		boolean full = (flags & (CannonboltKit.F_BOOST | CannonboltKit.F_UNSTOPPABLE)) != 0;
		Vec3 keys = keysToWorld(input, p.getYRot());
		boolean held = keys != Vec3.ZERO;
		momentum = full ? 1.0 : step(momentum, held, p.isSprinting() && input.z > 1.0e-5, p.onGround(), p.horizontalCollision && !p.minorHorizontalCollision);
		if (held) {
			heading = turn(heading, keys);
		} else if (p.onGround()) {
			heading = Vec3.ZERO;
			return input; // nothing held on the ground: friction brings him to a stop
		} else if (heading == Vec3.ZERO) {
			return input;
		}
		double speed = speedAt(momentum);
		p.setDeltaMovement(heading.x * speed, v.y, heading.z * speed);
		return Vec3.ZERO;
	}

	/** The local player is no longer rolling: nothing carries over into the next time he curls. */
	public static void reset() {
		momentum = 0;
		wound = 0;
		driven = false;
		heading = Vec3.ZERO;
	}
}
