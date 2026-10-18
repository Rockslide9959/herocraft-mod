package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.allmight.AllMightPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.6: All Might's Oklahoma Smash (two full turns) and St. Louis Smash (one) spin his whole body -- costume and all -- and
 * Manchester Smash flips it head over heels about its middle. The limbs are posed by {@code AllMightPose}; driven by the synced
 * anim id, so every viewer sees it; the first-person camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class AllMightSpinMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$allMightSpin(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		float spin = AllMightPose.spinDegrees(player, partialTicks);
		if (spin != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(spin));
		}
		float flip = AllMightPose.flipDegrees(player, partialTicks);
		if (flip != 0f) {
			poseStack.translate(0.0f, 0.9f, 0.0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(flip));
			poseStack.translate(0.0f, -0.9f, 0.0f);
		}
	}
}
