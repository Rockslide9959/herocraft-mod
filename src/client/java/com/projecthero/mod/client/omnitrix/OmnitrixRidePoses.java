package com.projecthero.mod.client.omnitrix;

import com.projecthero.mod.omnitrix.OmnitrixRiding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2: the riders' poses for {@link OmnitrixRiding} (every humanoid layer, armour too). Only fields vanilla's
 * {@code setupAnim} resets every frame are touched (rotations, the parts' y, the legs' z), so nothing leaks onto the next
 * player drawn with the same model.
 */
public final class OmnitrixRidePoses {
	/** How far the Wildmutt rider leans into the ride (radians, at the neck). */
	private static final float LEAN = 0.42f;

	private OmnitrixRidePoses() {
	}

	/**
	 * Astride Wildmutt's back: legs down either side of his body, the upper body leaning forward, both hands forward low in
	 * his fur; the whole rider bobs with his stride (his walk cycle) and sways a little at rest.
	 */
	public static void wildmuttRider(HumanoidModel<?> m, LivingEntity rider, float age) {
		if (!(rider.getVehicle() instanceof LivingEntity mount)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float pos = mount.walkAnimation.position(partial);
		float spd = Mth.clamp(mount.walkAnimation.speed(partial), 0f, 1f);
		// one bob per paw-fall (the gallop's beat), plus a slow breath at rest
		float bob = -Math.abs(Mth.sin(pos * 0.6662f)) * 1.8f * spd + Mth.sin(age * 0.08f) * 0.25f * (1f - spd);
		float lean = LEAN + Mth.cos(pos * 0.6662f * 2f) * 0.07f * spd;
		m.body.xRot = lean;
		m.body.y = bob;
		m.head.y = bob;
		m.hat.copyFrom(m.head);
		// the hands go forward and down to grip his fur, a touch inward, rocking with the lean
		float grip = -0.5f - lean * 0.3f + Mth.sin(pos * 0.6662f * 2f) * 0.08f * spd;
		m.rightArm.y = 2f + bob;
		m.leftArm.y = 2f + bob;
		m.rightArm.xRot = grip;
		m.rightArm.yRot = 0.12f;
		m.rightArm.zRot = -0.06f;
		m.leftArm.xRot = grip;
		m.leftArm.yRot = -0.12f;
		m.leftArm.zRot = 0.06f;
		// the hips stay put while the chest leans: the legs move back under the tipped body (like vanilla's crouch)
		float legZ = 12f * Mth.sin(lean), legY = 12f * Mth.cos(lean) + bob;
		m.rightLeg.y = legY;
		m.leftLeg.y = legY;
		m.rightLeg.z = legZ;
		m.leftLeg.z = legZ;
		// astride: thighs forward-down and out round his flanks
		float squeeze = Mth.sin(pos * 0.6662f) * 0.06f * spd;
		m.rightLeg.xRot = -0.5f + squeeze;
		m.rightLeg.yRot = 0.18f;
		m.rightLeg.zRot = 0.62f;
		m.leftLeg.xRot = -0.5f - squeeze;
		m.leftLeg.yRot = -0.18f;
		m.leftLeg.zRot = -0.62f;
	}

	/**
	 * Grey Matter on a shoulder: sitting on top of it, legs dangling over the front and idly kicking, the hand nearer the head up
	 * holding on to it, the other resting on the shoulder. Called from {@code GreyMatterClient.pose} before a move's clip (a move
	 * still plays over it).
	 */
	public static void shoulder(HumanoidModel<?> m, Player gm, float age) {
		boolean left = OmnitrixRiding.leftShoulder(gm); // on the carrier's left: the head is on HIS right
		float kick = Mth.sin(age * 0.11f) * 0.2f;
		m.rightLeg.xRot = -0.6f + kick;
		m.rightLeg.yRot = 0.08f;
		m.rightLeg.zRot = 0.06f;
		m.leftLeg.xRot = -0.6f - kick;
		m.leftLeg.yRot = -0.08f;
		m.leftLeg.zRot = -0.06f;
		float sway = Mth.sin(age * 0.07f) * 0.05f;
		// the inner hand up on the carrier's head, the outer one on the shoulder beside him
		// the inner hand reaches up and forward round to the side of their face (his own huge head would hide one held straight up)
		float hold = -1.95f + sway, rest = -0.35f - sway;
		if (left) {
			m.rightArm.xRot = hold;
			m.rightArm.yRot = 0f;
			m.rightArm.zRot = -0.5f;
			m.leftArm.xRot = rest;
			m.leftArm.yRot = 0f;
			m.leftArm.zRot = -0.3f;
		} else {
			m.leftArm.xRot = hold;
			m.leftArm.yRot = 0f;
			m.leftArm.zRot = 0.5f;
			m.rightArm.xRot = rest;
			m.rightArm.yRot = 0f;
			m.rightArm.zRot = 0.3f;
		}
	}
}
