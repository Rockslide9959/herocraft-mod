package com.projecthero.mod.client.stand;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.stand.data.StandPoseState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2 (user: "create this pose please, recreate it exactly how it looks in these reference images, make this the only pose thats
 * availible", {@code docs/reference/jotaro_pose/}): the one pose N strikes, on the user's own body -- everyone sees this one (it is the
 * player, not the Stand), driven by the synced {@link StandPoseState}. Set from {@code HumanoidModelMixin}.
 *
 * <p>Jotaro's point: turned three-quarters with the right shoulder driven forward and the chest open, the right arm thrust out down
 * the look with a slight bend, the left hand on the hip with the elbow out, chin turned over the shoulder, legs wide apart with the
 * right one forward. The Stand looms over the left shoulder ({@link com.projecthero.mod.stand.StandClips}).
 */
public final class StarPlatinumPose {
	public static final int POINTING = 1;
	/** How far the torso turns from the look (radians, toward the user's left). */
	public static final float TURN = -0.62f;

	private StarPlatinumPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		StandPoseState s = player.getAttachedOrElse(ModAttachments.STAND_POSE, null);
		if (s == null || s.pose() == 0 || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = player.level().getGameTime() - s.start() + partial;
		float w = Mth.clamp(age / 4f, 0f, 1f);
		float look = model.head.xRot;

		// the torso side-on, shoulders swung round with it (as vanilla's attack swing moves them)
		float yaw = TURN * w;
		model.body.yRot = yaw;
		model.rightArm.z = Mth.sin(yaw) * 5f;
		model.rightArm.x = -Mth.cos(yaw) * 5f;
		model.leftArm.z = -Mth.sin(yaw) * 5f;
		model.leftArm.x = Mth.cos(yaw) * 5f;

		// the right arm straight down the look, the elbow barely soft
		StandPoses.pose(model.rightArm, w, look - Mth.HALF_PI - 0.12f, 0.18f, 0.05f);
		StandPoses.joint(JointedLimbs.RIGHT_ARM, -0.3f * w);
		// the left hand on the hip: elbow out and back, the forearm folded in to the belt
		StandPoses.pose(model.leftArm, w, 0.38f, 1.4f + yaw * 0.5f, -0.5f);
		StandPoses.joint(JointedLimbs.LEFT_ARM, -1.55f * w);
		// legs wide apart, the right one forward and straight, the left trailing, knee soft
		StandPoses.pose(model.rightLeg, w, -0.3f, -0.2f, 0.36f);
		StandPoses.joint(JointedLimbs.RIGHT_LEG, 0.12f * w);
		StandPoses.pose(model.leftLeg, w, 0.28f, -0.2f, -0.42f);
		StandPoses.joint(JointedLimbs.LEFT_LEG, 0.22f * w);
		// the chin turned over the left shoulder, eyes on the target, chest open
		model.head.yRot = Mth.lerp(w, model.head.yRot, model.head.yRot - 0.3f);
		model.head.xRot = Mth.lerp(w, model.head.xRot, look + 0.06f);
		model.hat.copyFrom(model.head);
		HipBend.pitch(model, w, -0.1f);
	}
}
