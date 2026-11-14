package com.projecthero.mod.client.spin;

import com.projecthero.mod.spin.GyroSuitItem;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.SpinBalls;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 (user: "when the player holds out the steel ball change the holding animation, [the ball] shouldn't be in front of them, it
 * should be next to them and slightly out, like he's holding the steel balls next to him please"): Gyro's stance. An arm holding one of
 * his Steel Balls hangs at his side, a little out from the hip, instead of vanilla's raised "holding an item" arm -- so the ball (which
 * sits just under the fist, {@code models/item/steel_ball.json}) is beside his thigh, slightly out. It keeps a smaller walking swing,
 * and an arm about to throw from Gyro's suit's holsters (the suit on, that hand empty, a ball on the belt for it) takes the same stance
 * over the holster. During a throw (the swing) the stance fades, so the throw comes up from the side position and settles back into it.
 * Every viewer; from {@code SpinHoldPoseMixin} (setupAnim TAIL).
 */
public final class SpinHoldPose {
	/** How far out from the hip the arm hangs (radians) and how much of the walking swing it keeps. */
	private static final float OUT = 0.30f, SWING_KEPT = 0.55f;
	/** Vanilla's "holding an item" raise (x rotation), taken back off. */
	private static final float ITEM_RAISE = (float) Math.PI / 10f;

	private SpinHoldPose() {
	}

	public static void apply(Player p, HumanoidModel<?> m) {
		if (!Spin.hasPower(p) || p.isPassenger() && !Spin.riding(p) || p.isSwimming() || p.isFallFlying()) {
			return;
		}
		boolean rightMain = p.getMainArm() == HumanoidArm.RIGHT;
		arm(p, m, rightMain ? m.rightArm : m.leftArm, InteractionHand.MAIN_HAND, rightMain ? 1f : -1f, rightMain ? m.rightArmPose : m.leftArmPose);
		arm(p, m, rightMain ? m.leftArm : m.rightArm, InteractionHand.OFF_HAND, rightMain ? -1f : 1f, rightMain ? m.leftArmPose : m.rightArmPose);
	}

	/** {@code side}: +1 the model's right arm (out = +z rotation), -1 the left. */
	private static void arm(Player p, HumanoidModel<?> m, ModelPart arm, InteractionHand hand, float side, HumanoidModel.ArmPose armPose) {
		boolean held = SpinBalls.heldIndex(p, hand) >= 0;
		boolean holster = !held && p.getItemInHand(hand).isEmpty() && GyroSuitItem.suitWorn(p) && SpinBalls.inHand(p, hand) >= 0;
		if (!held && !holster) {
			return;
		}
		if (held && armPose != HumanoidModel.ArmPose.ITEM) {
			return; // another pose has the arm (blocking, charging...)
		}
		// the throw: the stance gives way over the swing and comes back after it
		float k = 1f;
		boolean swingHand = (p.swingingArm == InteractionHand.MAIN_HAND) == (hand == InteractionHand.MAIN_HAND);
		if (m.attackTime > 0f && swingHand) {
			k = 1f - Mth.sin(Mth.sqrt(m.attackTime) * Mth.PI);
		}
		if (p.isCrouching()) {
			k *= 0.6f;
		}
		// keep part of the walk swing (vanilla wrote it into xRot, halved for an item arm)
		float walk = held ? (arm.xRot + ITEM_RAISE) : arm.xRot;
		float target = walk * SWING_KEPT;
		arm.xRot = Mth.lerp(k, arm.xRot, target + 0.05f);
		arm.yRot = Mth.lerp(k, arm.yRot, 0f);
		arm.zRot += side * OUT * k;
	}
}
