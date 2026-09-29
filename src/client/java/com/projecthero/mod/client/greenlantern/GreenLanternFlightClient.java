package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLanternConfig;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.21: directional Ring Flight, local player only (explicit user request -- "directional flight combined with
 * creative flight"). Replaces vanilla's creative-flight step while Ring Flight is engaged:
 * <ul>
 *   <li><b>forward / back</b> fly along the full 3D look vector, so looking up or down while holding W climbs or dives;</li>
 *   <li><b>strafe</b> slides sideways on the horizontal plane, like creative flight;</li>
 *   <li><b>Space / Sneak</b> still rise and sink straight up and down on top of that;</li>
 *   <li><b>no input</b> eases to a dead hover -- no gravity, no drift.</li>
 * </ul>
 * The velocity eases toward the wanted one ({@link GreenLanternConfig#FLIGHT_ACCELERATION} /
 * {@link GreenLanternConfig#FLIGHT_BRAKING} of the gap per tick) from this class's own record of last tick's velocity
 * -- not {@code getDeltaMovement()}, which vanilla's own creative-flight Space/Sneak nudges have already been added to
 * before {@code travel} runs -- so steering is smooth and never jitters between the two models.
 *
 * <p>Speeds keep the existing feel exactly: cruise is {@code flyingSpeed x 10} blocks/tick (the server still sets
 * {@code flyingSpeed} -- 0.06 cruise, the Boost ratio above it), doubled while sprinting like creative flight;
 * vertical is {@link GreenLanternConfig#FLIGHT_VERTICAL_SPEED_BPS} ({@link GreenLanternConfig#BOOST_VERTICAL_SPEED_BPS}
 * boosting). Boost is still Sneak+Sprint and still costs the same (the server reads it off the same keys) -- but while
 * boosting, Sneak is the boost modifier rather than "descend", since you now dive by looking down.
 *
 * <p>Like every other client-simulated movement in this mod (Spider-Man's adhesion, the Flight power), the client
 * moves itself and the server accepts the result -- the speeds are well inside its movement tolerance for a player
 * with {@code mayfly}. Activation (double-tap Space), the auto-land and all energy upkeep are unchanged and server-side.
 */
public final class GreenLanternFlightClient {
	private static Vec3 velocity;

	private GreenLanternFlightClient() {
	}

	/** @return true if Ring Flight moved the player this tick (vanilla's travel step must then be skipped). */
	public static boolean travel(LocalPlayer player) {
		if (!player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false) || !player.getAbilities().flying
				|| player.isPassenger() || player.isSpectator() || player.isFallFlying()) {
			velocity = null;
			return false;
		}
		if (velocity == null) {
			velocity = player.getDeltaMovement();
		}
		boolean sneak = player.input.shiftKeyDown;
		boolean boosting = sneak && player.isSprinting();
		float forward = player.input.forwardImpulse;
		float strafe = player.input.leftImpulse;
		int vertical = (player.input.jumping ? 1 : 0) - (sneak && !boosting ? 1 : 0);

		double speed = player.getAbilities().getFlyingSpeed() * 10.0 * (player.isSprinting() ? 2.0 : 1.0);
		double verticalSpeed = (boosting ? GreenLanternConfig.BOOST_VERTICAL_SPEED_BPS
				: GreenLanternConfig.FLIGHT_VERTICAL_SPEED_BPS) / 20.0;

		Vec3 look = player.getLookAngle();
		float yaw = player.getYRot() * ((float) Math.PI / 180f);
		Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
		Vec3 wish = look.scale(forward).add(left.scale(strafe));
		if (wish.lengthSqr() > 1.0) {
			wish = wish.normalize();
		}
		Vec3 wanted = wish.scale(speed).add(0.0, vertical * verticalSpeed, 0.0);
		boolean steering = forward != 0f || strafe != 0f || vertical != 0;
		double ease = steering ? GreenLanternConfig.FLIGHT_ACCELERATION : GreenLanternConfig.FLIGHT_BRAKING;
		Vec3 next = velocity.add(wanted.subtract(velocity).scale(ease));
		if (!steering && next.lengthSqr() < 1.0e-5) {
			next = Vec3.ZERO;
		}

		player.setDeltaMovement(next);
		player.move(MoverType.SELF, next);
		// Entity.move zeroes whichever components collided, so this is the velocity that actually happened.
		velocity = player.getDeltaMovement();
		player.resetFallDistance();
		return true;
	}
}
