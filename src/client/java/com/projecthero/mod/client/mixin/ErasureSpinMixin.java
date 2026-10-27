package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.quirk.erasure.ErasurePose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.7 (Erasure revamp): Bind and Spin turns Eraser Head's whole body with the creature swinging on his scarf, and the
 * Close Combat finisher is a full spinning kick ({@link ErasurePose#spinDegrees}; the AllMightSpinMixin pattern). Every
 * viewer sees it (synced state); the first-person camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class ErasureSpinMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$erasureSpin(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks,
			float scale, CallbackInfo ci) {
		float spin = ErasurePose.spinDegrees(player, partialTicks);
		if (spin != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(spin));
		}
		// v0.17.1: the Evade Flip -- a backflip about the body's middle
		float flip = ErasurePose.flipDegrees(player, partialTicks);
		if (flip != 0f) {
			poseStack.translate(0.0f, 0.9f, 0.0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(flip));
			poseStack.translate(0.0f, -0.9f, 0.0f);
		}
	}
}
