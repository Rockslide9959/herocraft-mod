package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.spider.SpiderPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.1: Spider-Man's ledge perch in first person -- BOTH hands come down to the bottom of the view, planted on the
 * ledge just ahead of his feet (the off hand is drawn here too, since vanilla never draws an empty off-hand arm).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SpiderPerchHandMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equippedProgress,
			float swingProgress, HumanoidArm side);

	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
	private void projecthero$perchHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		float k = SpiderPose.perchAmount(player);
		if (k <= 0f) {
			return;
		}
		HumanoidArm main = player.getMainArm();
		float side = main == HumanoidArm.RIGHT ? 1f : -1f;
		// the off hand, mirrored (renderPlayerArm places it on its own side of the view)
		pose.pushPose();
		pose.translate(side * 0.42f * k, 0.07f * k, -0.12f * k);
		pose.mulPose(Axis.XP.rotationDegrees(6f * k));
		pose.mulPose(Axis.ZP.rotationDegrees(side * 14f * k));
		renderPlayerArm(pose, buffers, light, 0.0f, 0.0f, main.getOpposite());
		pose.popPose();
		// the main hand
		pose.translate(-side * 0.42f * k, 0.07f * k, -0.12f * k);
		pose.mulPose(Axis.XP.rotationDegrees(6f * k));
		pose.mulPose(Axis.ZP.rotationDegrees(-side * 14f * k));
	}
}
