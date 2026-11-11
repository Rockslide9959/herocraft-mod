package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.saiyan.kit.SaiyanKitPose;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 Saiyan revamp: whole-body turns for the move kit -- the finisher's spin, the Rising Dragon Kick's back flip, the
 * lean of a dash, a rush and a smash ({@link SaiyanKitPose#rotate}). The ErasureSpinMixin pattern; the camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class SaiyanKitSpinMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$saiyanKitSpin(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks,
			float scale, CallbackInfo ci) {
		SaiyanKitPose.rotate(player, poseStack, partialTicks);
	}
}
