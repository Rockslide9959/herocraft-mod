package com.projecthero.mod.symbiote;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19: where the Symbiote's moves come from -- the host's hands, not their head. Every tendril, spike
 * and grapple line starts at the hand the pose raises ({@code SymbiotePose} holds the arm out along the aim),
 * and still flies along the look vector so aiming is unchanged.
 *
 * <p>Model maths: the arm pivots at the shoulder, 22 px up and 5 px out from the centre line (1.375 / 0.3125
 * blocks), and the fist is 10 px down the arm -- so with the arm held out along the look, the fist is ~0.6
 * blocks along it. Everything scales with the host (the Symbiote suit makes a Normal host 1.5x tall).
 */
public final class SymbioteHands {
	private static final double SHOULDER_HEIGHT = 1.375;
	private static final double SHOULDER_OUT = 0.3125;
	private static final double ARM_REACH = 0.6;

	private SymbioteHands() {
	}

	/** The host's right (main) hand, arm held out along the aim. */
	public static Vec3 right(Player player) {
		return hand(player, true);
	}

	public static Vec3 left(Player player) {
		return hand(player, false);
	}

	public static Vec3 hand(Player player, boolean rightHand) {
		double scale = player.getScale();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		Vec3 right = flat.lengthSqr() < 1.0e-6
				? Vec3.directionFromRotation(0.0f, player.getYRot() + 90.0f)
				: new Vec3(-flat.z, 0.0, flat.x).normalize();
		Vec3 shoulder = player.position().add(0.0, SHOULDER_HEIGHT * scale, 0.0)
				.add(right.scale((rightHand ? SHOULDER_OUT : -SHOULDER_OUT) * scale));
		return shoulder.add(look.scale(ARM_REACH * scale));
	}
}
