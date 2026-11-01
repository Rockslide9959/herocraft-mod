package com.projecthero.mod.client.stand;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.stand.data.StandPoseState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2: the pose a Stand user strikes with N (chosen in the Stand Menu), on the user's own body -- everyone sees this
 * one (it is the player, not the Stand), driven by the synced {@link StandPoseState}. Set from {@code HumanoidModelMixin}.
 * <ol>
 *   <li><b>Pointing</b>: the right arm straight out along the look ("You're next").</li>
 *   <li><b>Yare yare</b>: head down, tugging the brim of the cap.</li>
 *   <li><b>Hands in pockets</b>: shoulders back, chin up.</li>
 *   <li><b>JoJo pose</b>: twisted at the waist, one arm thrust out, the other hand at the brow, feet apart.</li>
 * </ol>
 */
public final class StarPlatinumPose {
	public static final int POINTING = 1;
	public static final int YARE_YARE = 2;
	public static final int POCKETS = 3;
	public static final int JOJO = 4;

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
		switch (s.pose()) {
			case POINTING -> {
				StandPoses.pose(model.rightArm, w, model.head.xRot - Mth.HALF_PI, model.head.yRot, 0f);
				StandPoses.pose(model.leftArm, w, 0.1f, 0f, -0.1f);
			}
			case YARE_YARE -> {
				StandPoses.pose(model.rightArm, w, -2.55f, -0.35f, 0.25f);
				StandPoses.pose(model.leftArm, w, 0.15f, 0f, -0.1f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.45f);
				model.hat.copyFrom(model.head);
			}
			case POCKETS -> {
				StandPoses.pose(model.rightArm, w, 0.28f, 0.1f, 0.1f);
				StandPoses.pose(model.leftArm, w, 0.28f, -0.1f, -0.1f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.12f);
				model.hat.copyFrom(model.head);
			}
			case JOJO -> {
				model.body.yRot = Mth.lerp(w, model.body.yRot, 0.45f);
				StandPoses.pose(model.rightArm, w, -1.45f, -0.25f, 0.15f);
				StandPoses.pose(model.leftArm, w, -2.35f, 0.65f, 0.35f);
				StandPoses.pose(model.rightLeg, w, 0.1f, 0.2f, 0.18f);
				StandPoses.pose(model.leftLeg, w, -0.25f, -0.15f, -0.1f);
				model.head.yRot = Mth.lerp(w, model.head.yRot, -0.35f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.1f);
				model.hat.copyFrom(model.head);
			}
			default -> {
			}
		}
	}
}
