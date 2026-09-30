package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.moonknight.MoonKnightTruncheonPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.14.4: Moon Knight's Staff Spin turns his whole body a full circle (suit, cape and staff with it) -- the limbs are
 * posed by {@code MoonKnightTruncheonPose}. Driven by the synced anim id, so every viewer sees it; the first-person
 * camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class MoonKnightTruncheonSpinMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$moonKnightStaffSpin(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		float spin = MoonKnightTruncheonPose.spinDegrees(player, partialTicks);
		if (spin != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(spin));
		}
	}
}
