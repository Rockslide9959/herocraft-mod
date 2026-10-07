package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.suit.IronManManualSuitUp;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.11: the body pose of a hand-built suit-up ({@link IronManManualSuitUp}), read off the synced
 * {@link IronManSuitFx} pose clock so every viewer sees the same frame. Besides the limbs it bends the torso forward at
 * the hips ({@code LEAN}: the body pivots about its bottom edge and the head / arms move with its top) and, for the Mark
 * 1's floor work, sits the wearer down ({@code SIT}: the legs straight out in front, the whole body lowered by
 * {@link #sitDrop} in {@code PlayerRendererManualSuitUpMixin}).
 */
public final class IronManManualSuitUpPose {
	private IronManManualSuitUpPose() {
	}

	/** {weight, key...} for {@code player} this frame, or null when no hand build is running. */
	public static float[] sample(Player player, float partial) {
		if (player.level() == null) {
			return null;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		int kind = IronManManualSuitUp.kindOfPose(fx.poseKind());
		if (kind < 0) {
			return null;
		}
		float age = fx.poseAge(player.level().getGameTime(), partial);
		if (age < 0f) {
			return null;
		}
		return IronManManualSuitUp.schedule(kind, IronManManualSuitUp.planOf(fx.poseVariant())).pose(age);
	}

	/** How far the body is lowered this frame (blocks, before the wearer's scale), for the sitting floor work. */
	public static float sitDrop(Player player, float partial) {
		float[] s = sample(player, partial);
		if (s == null) {
			return 0f;
		}
		return s[0] * s[1 + IronManManualSuitUp.SIT] * IronManManualSuitUp.SIT_DROP_PX / 16f;
	}

	/** Lay the pose over vanilla's: limbs, head, the forward lean (torso about the hips) and the sitting legs. */
	public static boolean apply(Player player, HumanoidModel<?> model) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float[] s = sample(player, partial);
		if (s == null || s[0] <= 0.001f) {
			return false;
		}
		float w = s[0];
		float[] k = new float[IronManManualSuitUp.SIZE];
		System.arraycopy(s, 1, k, 0, k.length);
		model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, k[IronManManualSuitUp.RAX]);
		model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, k[IronManManualSuitUp.RAY]);
		model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, k[IronManManualSuitUp.RAZ]);
		model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, k[IronManManualSuitUp.LAX]);
		model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, k[IronManManualSuitUp.LAY]);
		model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, k[IronManManualSuitUp.LAZ]);
		model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, k[IronManManualSuitUp.RLX]);
		model.rightLeg.yRot = Mth.lerp(w, model.rightLeg.yRot, k[IronManManualSuitUp.RLY]);
		model.rightLeg.zRot = Mth.lerp(w, model.rightLeg.zRot, k[IronManManualSuitUp.RLZ]);
		model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, k[IronManManualSuitUp.LLX]);
		model.leftLeg.yRot = Mth.lerp(w, model.leftLeg.yRot, k[IronManManualSuitUp.LLY]);
		model.leftLeg.zRot = Mth.lerp(w, model.leftLeg.zRot, k[IronManManualSuitUp.LLZ]);
		model.head.xRot = Mth.lerp(w, model.head.xRot, k[IronManManualSuitUp.HX]);
		model.head.yRot = Mth.lerp(w * 0.8f, model.head.yRot, k[IronManManualSuitUp.HY]);

		// the torso bends forward about its bottom edge (the hips); vanilla's pivot is its top edge, so the top moves
		float lean = k[IronManManualSuitUp.LEAN];
		float topY = 12f - 12f * Mth.cos(lean);
		float topZ = -12f * Mth.sin(lean);
		model.body.xRot = Mth.lerp(w, model.body.xRot, lean);
		model.body.yRot = Mth.lerp(w, model.body.yRot, 0f);
		model.body.x = Mth.lerp(w, model.body.x, 0f);
		model.body.y = Mth.lerp(w, model.body.y, topY);
		model.body.z = Mth.lerp(w, model.body.z, topZ);
		model.head.x = Mth.lerp(w, model.head.x, 0f);
		model.head.y = Mth.lerp(w, model.head.y, topY);
		model.head.z = Mth.lerp(w, model.head.z, topZ);
		float armY = topY + 2f * Mth.cos(lean);
		float armZ = topZ + 2f * Mth.sin(lean);
		model.rightArm.x = Mth.lerp(w, model.rightArm.x, -5f);
		model.rightArm.y = Mth.lerp(w, model.rightArm.y, armY);
		model.rightArm.z = Mth.lerp(w, model.rightArm.z, armZ);
		model.leftArm.x = Mth.lerp(w, model.leftArm.x, 5f);
		model.leftArm.y = Mth.lerp(w, model.leftArm.y, armY);
		model.leftArm.z = Mth.lerp(w, model.leftArm.z, armZ);
		// legs at the hips (cancels a crouch held mid-build)
		model.rightLeg.y = Mth.lerp(w, model.rightLeg.y, 12f);
		model.rightLeg.z = Mth.lerp(w, model.rightLeg.z, 0f);
		model.leftLeg.y = Mth.lerp(w, model.leftLeg.y, 12f);
		model.leftLeg.z = Mth.lerp(w, model.leftLeg.z, 0f);
		return true;
	}
}
