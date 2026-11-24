package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.supersoldier.SuperSoldierPose;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.7: the Shield Storm turns the Super Soldier's whole body -- a full spin about his own axis as he hurls the shield, and a
 * tucked front flip about the hips after the catch. The limbs are posed by {@link SuperSoldierPose}; driven by the synced anim id,
 * so every viewer sees it; the first-person camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class SuperSoldierBodyMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$superSoldierBody(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks, float scale, CallbackInfo ci) {
		float spin = SuperSoldierPose.spinDegrees(player, partialTicks);
		if (spin != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(spin));
		}
		float flip = SuperSoldierPose.flipDegrees(player, partialTicks);
		if (flip != 0f) {
			poseStack.translate(0.0f, 0.9f, 0.0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(flip));
			poseStack.translate(0.0f, -0.9f, 0.0f);
		}
	}
}
