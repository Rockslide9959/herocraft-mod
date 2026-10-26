package com.projecthero.mod.client.falcon;

import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.falcon.data.FalconState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Falcon's keyframed poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} (the suit layer
 * copies it). Driven by the synced {@link FalconState}. Negative {@code xRot} swings an arm forward / up.
 * <ul>
 *   <li><b>Wrist Guns</b>: both arms along the look, kicking in turn.</li>
 *   <li><b>Wing Shield</b>: forearms crossed in front (the wings wrap round them).</li>
 *   <li><b>Lift &amp; Carry / Talon Drop</b>: both hands reaching down to the one held.</li>
 *   <li><b>Hero landing</b>: down on one knee, a fist to the ground.</li>
 *   <li><b>Wing Slash</b>: the right arm leads the right wing's swing round from his back across the front.</li>
 *   <li>Flying: arms a little out from the sides under the wings.</li>
 *   <li>Short keyframes for the bash, missiles, afterburner, Redwing and take-off.</li>
 * </ul>
 */
public final class FalconPose {
	private FalconPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		FalconState s = Falcon.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		float age = now - s.animStart + partial;
		boolean gliding = Falcon.flying(player) && !player.onGround();

		if (s.firingUntil > now) {
			// the wrist launchers along the look, alternating recoil
			float kickR = (now % 8 < 4) ? 0.12f : 0f;
			model.rightArm.xRot = model.head.xRot - (float) Math.PI / 2f + kickR;
			model.leftArm.xRot = model.head.xRot - (float) Math.PI / 2f + (0.12f - kickR);
			model.rightArm.yRot = model.head.yRot - 0.08f;
			model.leftArm.yRot = model.head.yRot + 0.08f;
			model.rightArm.zRot = 0f;
			model.leftArm.zRot = 0f;
			return;
		}
		if (Falcon.shieldUp(player)) {
			pose(model.rightArm, -1.35f, -0.75f, 0f);
			pose(model.leftArm, -1.35f, 0.75f, 0f);
			return;
		}
		if (s.carryId != -1) {
			// hands down to the one held under him
			pose(model.rightArm, gliding ? -0.2f : -0.35f, 0f, 0.25f);
			pose(model.leftArm, gliding ? -0.2f : -0.35f, 0f, -0.25f);
			return;
		}
		// flying: arms a little out from the sides under the spread wings (any move keyframe below overrides it)
		if (gliding) {
			model.rightArm.zRot = 0.32f;
			model.leftArm.zRot = -0.32f;
			model.rightArm.xRot = 0.15f;
			model.leftArm.xRot = 0.15f;
		}
		switch (s.animId) {
			case FalconState.ANIM_LAND -> {
				if (age >= 0f && age < FalconConfig.LAND_POSE_TICKS && !gliding) {
					float k = age < 3f ? age / 3f : age > FalconConfig.LAND_POSE_TICKS - 4 ? (FalconConfig.LAND_POSE_TICKS - age) / 4f : 1f;
					heroLanding(model, Mth.clamp(k, 0f, 1f));
				}
			}
			case FalconState.ANIM_SLASH -> {
				float t = FalconGeometry.slashProgress(age);
				if (t >= 0f) {
					// the right arm leads the right wing round: out to the side, then across the front, synced to its yaw
					float yaw = FalconGeometry.slashYaw(t);
					float out = t <= 1f ? 1f : 1f - (t - 1f) / 0.55f;
					float raise = Math.min(1f, t * 5f) * out;
					float armYaw = Mth.clamp(90f - yaw, -50f, 100f) * Mth.DEG_TO_RAD;
					model.rightArm.xRot = Mth.lerp(raise, model.rightArm.xRot, -1.45f);
					model.rightArm.yRot = Mth.lerp(raise, model.rightArm.yRot, armYaw);
					model.rightArm.zRot = Mth.lerp(raise, model.rightArm.zRot, 0f);
					// the left arm swings back for balance
					model.leftArm.xRot = Mth.lerp(raise, model.leftArm.xRot, 0.35f);
					model.leftArm.zRot = Mth.lerp(raise, model.leftArm.zRot, -0.45f);
				}
			}
			case FalconState.ANIM_BASH -> {
				if (age >= 0f && age < 8f) {
					float k = 1f - age / 8f;
					pose(model.rightArm, -1.5f * k, 0f, 1.0f * k);
					pose(model.leftArm, -1.5f * k, 0f, -1.0f * k);
				}
			}
			case FalconState.ANIM_MISSILES -> {
				if (age >= 0f && age < 10f) {
					float k = Mth.sin(age / 10f * (float) Math.PI);
					model.rightArm.zRot += 0.5f * k;
					model.leftArm.zRot -= 0.5f * k;
				}
			}
			case FalconState.ANIM_REDWING -> {
				if (age >= 0f && age < 12f && !gliding) {
					// pointing Redwing at the target
					boolean right = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
					ModelPart arm = right ? model.rightArm : model.leftArm;
					arm.xRot = model.head.xRot - (float) Math.PI / 2f;
					arm.yRot = model.head.yRot;
				}
			}
			case FalconState.ANIM_AFTERBURNER, FalconState.ANIM_TAKEOFF -> {
				if (age >= 0f && age < 8f && !gliding) {
					pose(model.rightArm, 0.35f, 0f, 0.2f);
					pose(model.leftArm, 0.35f, 0f, -0.2f);
				}
			}
			case FalconState.ANIM_DIVE -> {
				if (!gliding && age >= 0f && age < FalconConfig.DIVE_MAX_TICKS) {
					// arms tight to the sides, a missile
					pose(model.rightArm, 0.1f, 0f, 0.08f);
					pose(model.leftArm, 0.1f, 0f, -0.08f);
					pose(model.rightLeg, 0f, 0f, 0.02f);
					pose(model.leftLeg, 0f, 0f, -0.02f);
				}
			}
			default -> {
			}
		}
	}

	private static void pose(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	/** Down on one knee, the right fist on the ground: the crouch offsets, then the legs and arm. */
	private static void heroLanding(HumanoidModel<?> m, float k) {
		m.body.xRot = 0.55f * k;
		m.body.y = 3.2f * k;
		m.head.y = 4.2f * k;
		m.hat.y = m.head.y;
		m.rightArm.y = 2f + 3.2f * k;
		m.leftArm.y = 2f + 3.2f * k;
		m.rightLeg.z = 4f * k;
		m.leftLeg.z = 4f * k;
		m.rightLeg.y = 12f + 0.2f * k;
		m.leftLeg.y = 12f + 0.2f * k;
		// the left knee forward, the right leg back under him
		m.leftLeg.xRot = -1.25f * k;
		m.rightLeg.xRot = 0.55f * k;
		// the right fist down to the ground, the left arm out for balance
		m.rightArm.xRot = -0.25f * k;
		m.rightArm.zRot = 0.15f * k;
		m.leftArm.xRot = 0.4f * k;
		m.leftArm.zRot = -0.7f * k;
	}
}
