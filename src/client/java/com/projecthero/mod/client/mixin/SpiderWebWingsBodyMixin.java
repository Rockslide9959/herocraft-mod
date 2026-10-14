package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.16.2: the Suit v2's Web Wings glide -- the body pitched forward into the glide and rolled into turns. */
@Mixin(PlayerRenderer.class)
public abstract class SpiderWebWingsBodyMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void projecthero$webWingsBody(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks, float scale, CallbackInfo ci) {
		com.projecthero.mod.client.spider.SpiderWebWingsClient.applyBodyRotation(player, poseStack, partialTicks);
		// v0.16.3: the head-first dive on a long fall
		com.projecthero.mod.client.spider.SpiderDiveClient.applyBodyRotation(player, poseStack, partialTicks);
	}
}
