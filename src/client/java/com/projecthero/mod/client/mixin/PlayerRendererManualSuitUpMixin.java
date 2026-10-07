package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.15.11: the Mark 1 hand build sits on the floor for the boots and greaves -- the whole body is lowered so the hips
 * rest just above the ground ({@code IronManManualSuitUpPose#sitDrop}; the legs are posed straight out in front).
 * Applied after the yaw in {@code setupRotations}, so it is world-down; the wearer's scale has already been applied.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererManualSuitUpMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$manualSuitUpSit(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		float drop = com.projecthero.mod.client.ironman.IronManManualSuitUpPose.sitDrop(player, partialTicks);
		if (drop > 0.0005f) {
			poseStack.translate(0.0f, -drop, 0.0f);
		}
	}
}
