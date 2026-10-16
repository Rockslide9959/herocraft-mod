package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.mutation.ElasticityClient;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.5 Elasticity rework: the Rubber Ball and the Shape Shift block are drawn instead of the player, and the Parachute
 * canopy lies flat into the glide.
 */
@Mixin(PlayerRenderer.class)
public abstract class ElasticityPlayerRendererMixin {
	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$elasticBody(AbstractClientPlayer player, float entityYaw, float partialTicks, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		if (!player.isInvisible() && ElasticityClient.renderReplacement(player, partialTicks, poseStack, buffer, packedLight)) {
			ci.cancel();
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$elasticCanopy(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks, float scale, CallbackInfo ci) {
		ElasticityClient.applyBodyRotation(player, poseStack);
	}
}
