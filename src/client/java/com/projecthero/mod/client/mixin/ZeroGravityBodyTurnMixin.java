package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.quirk.zerogravity.ZeroGravityPose;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.1 Zero Gravity revamp: Uravity's whole-body turns ({@link ZeroGravityPose#bodyTurn}) -- the Weightless Push's flat dive
 * and Float Self's slow weightless tilt -- about the body's middle, costume and all. Its own mixin (the shared
 * {@code QuirkBodyTurnMixin} is the Engine / Explosion builds'); every viewer sees it, the first-person camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class ZeroGravityBodyTurnMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$zeroGravityBodyTurn(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks, float scale, CallbackInfo ci) {
		float[] t = ZeroGravityPose.bodyTurn(player, partialTicks);
		if (t == null) {
			return;
		}
		poseStack.translate(0.0f, 0.9f, 0.0f);
		if (t[0] != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(t[0]));
		}
		if (t[1] != 0f) {
			poseStack.mulPose(Axis.XP.rotationDegrees(t[1]));
		}
		if (t[2] != 0f) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(t[2]));
		}
		poseStack.translate(0.0f, -0.9f, 0.0f);
	}
}
