package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.blackpanther.BlackPantherPose;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.4: Black Panther's acrobatics turn his whole body -- the Vault's front flip and the Backflip head over heels about the
 * hips, the combo's spinning kick and the Leg Sweep round his own axis. The limbs are posed by {@link BlackPantherPose}; driven
 * by the synced anim id, so every viewer sees it; the first-person camera is untouched.
 */
@Mixin(PlayerRenderer.class)
public abstract class BlackPantherFlipMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$blackPantherFlip(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks, float scale, CallbackInfo ci) {
		float spin = BlackPantherPose.spinDegrees(player, partialTicks);
		if (spin != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(spin));
		}
		float flip = BlackPantherPose.flipDegrees(player, partialTicks);
		if (flip != 0f) {
			poseStack.translate(0.0f, 0.9f, 0.0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(flip));
			poseStack.translate(0.0f, -0.9f, 0.0f);
		}
	}
}
