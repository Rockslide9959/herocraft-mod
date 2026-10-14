package com.projecthero.mod.client.mixin;

import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.JointedLimbs;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;

/**
 * v0.16.2: a player model's sleeve / pants overlay parts bend with the arms / legs ({@link JointedLimbs}), and a held
 * item follows a bent forearm to the hand.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelLimbRegistryMixin {
	@Shadow
	@Final
	public ModelPart rightSleeve;
	@Shadow
	@Final
	public ModelPart leftSleeve;
	@Shadow
	@Final
	public ModelPart rightPants;
	@Shadow
	@Final
	public ModelPart leftPants;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void projecthero$registerOverlayLimbs(ModelPart root, boolean slim, CallbackInfo ci) {
		JointedLimbs.register(rightSleeve, JointedLimbs.RIGHT_ARM);
		JointedLimbs.register(leftSleeve, JointedLimbs.LEFT_ARM);
		JointedLimbs.register(rightPants, JointedLimbs.RIGHT_LEG);
		JointedLimbs.register(leftPants, JointedLimbs.LEFT_LEG);
	}

	@Inject(method = "translateToHand", at = @At("TAIL"))
	private void projecthero$heldItemFollowsForearm(HumanoidArm side, PoseStack pose, CallbackInfo ci) {
		int limb = side == HumanoidArm.RIGHT ? JointedLimbs.RIGHT_ARM : JointedLimbs.LEFT_ARM;
		Quaternionf q = JointedLimbs.joint(limb);
		if (q == null) {
			return;
		}
		float x = (side == HumanoidArm.RIGHT ? -1.0f : 1.0f) / 16.0f;
		float y = JointedLimbs.jointY(limb) / 16.0f;
		pose.translate(x, y, 0.0f);
		pose.mulPose(q);
		pose.translate(-x, -y, 0.0f);
	}
}
