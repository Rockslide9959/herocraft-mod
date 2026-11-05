package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.alien.DiamondheadClient;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.2: the Crystal Cyclone's real turn -- all of Diamondhead, not just his torso ({@link DiamondheadClient#spinDegrees}). Driven by
 * the synced anim, so every viewer sees it; the first-person camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class DiamondheadSpinMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$diamondheadSpin(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks,
			float scale, CallbackInfo ci) {
		float spin = DiamondheadClient.spinDegrees(player, partialTicks);
		if (spin != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(spin));
		}
	}
}
