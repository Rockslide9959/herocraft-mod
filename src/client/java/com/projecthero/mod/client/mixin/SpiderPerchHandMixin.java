package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
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
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.1: Spider-Man's ledge perch in first person -- the empty main hand drops to the bottom of the view and turns
 * palm-down, planted on the ledge between his feet.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SpiderPerchHandMixin {
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
	private void projecthero$perchHand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		float k = SpiderPose.perchAmount(player);
		if (k <= 0f) {
			return;
		}
		pose.translate(-0.34f * k, 0.06f * k, -0.12f * k);
		pose.mulPose(Axis.XP.rotationDegrees(6f * k));
		pose.mulPose(Axis.ZP.rotationDegrees(10f * k));
	}
}
