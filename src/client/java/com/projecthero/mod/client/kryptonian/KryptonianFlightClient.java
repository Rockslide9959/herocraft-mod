package com.projecthero.mod.client.kryptonian;

import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianConfig;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8: Kryptonian flight, local player only -- true directional flight. Replaces vanilla's creative-flight step while
 * he is flying (the same client-simulates / server-authorises split as Green Lantern's Ring Flight):
 * <ul>
 *   <li><b>W</b> flies along the full 3D look vector (look up to climb, down to dive); <b>Sprint</b> is super-speed
 *       flight ({@link KryptonianConfig#FLIGHT_SPRINT_SPEED} blocks a tick);</li>
 *   <li><b>S</b> brakes hard to a hover instead of flying backwards;</li>
 *   <li><b>A / D</b> slide sideways, <b>Space / Sneak</b> rise and sink straight up and down;</li>
 *   <li>no input: he coasts to a dead hover -- no gravity, no drift.</li>
 * </ul>
 * The velocity eases toward the wanted one from this class's own record of last tick's velocity, so steering is smooth. A
 * big outside push (a knockback, the server launching him) is adopted instead of fought.
 */
public final class KryptonianFlightClient {
	private static Vec3 velocity;

	private KryptonianFlightClient() {
	}

	/** @return true if flight moved the player this tick (vanilla's travel step must then be skipped). */
	public static boolean travel(LocalPlayer player) {
		if (!Kryptonian.isFlying(player) || !player.getAbilities().flying || player.isPassenger() || player.isSpectator()
				|| player.isFallFlying()) {
			velocity = null;
			return false;
		}
		Vec3 current = player.getDeltaMovement();
		if (velocity == null || current.subtract(velocity).lengthSqr() > 0.6 * 0.6) {
			velocity = current;
		}
		boolean sneak = player.input.shiftKeyDown;
		float forward = player.input.forwardImpulse;
		float strafe = player.input.leftImpulse;
		int vertical = (player.input.jumping ? 1 : 0) - (sneak ? 1 : 0);
		boolean braking = forward < 0f;
		boolean sprint = player.isSprinting();

		double speed = sprint ? KryptonianConfig.FLIGHT_SPRINT_SPEED : KryptonianConfig.FLIGHT_SPEED;
		Vec3 look = player.getLookAngle();
		float yaw = player.getYRot() * ((float) Math.PI / 180f);
		Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
		Vec3 wish = look.scale(Math.max(0f, forward)).add(left.scale(strafe * 0.7));
		if (wish.lengthSqr() > 1.0) {
			wish = wish.normalize();
		}
		Vec3 wanted = wish.scale(speed).add(0.0, vertical * KryptonianConfig.FLIGHT_VERTICAL_SPEED, 0.0);
		boolean steering = forward > 0f || strafe != 0f || vertical != 0;
		double ease = braking ? KryptonianConfig.FLIGHT_BRAKE : steering ? KryptonianConfig.FLIGHT_ACCELERATION : KryptonianConfig.FLIGHT_COAST;
		Vec3 next = velocity.add(wanted.subtract(velocity).scale(ease));
		if (!steering && next.lengthSqr() < 1.0e-4) {
			next = Vec3.ZERO;
		}
		player.setDeltaMovement(next);
		player.move(MoverType.SELF, next);
		// Entity.move zeroes whichever components collided, so this is the velocity that actually happened
		velocity = player.getDeltaMovement();
		player.resetFallDistance();
		return true;
	}
}
