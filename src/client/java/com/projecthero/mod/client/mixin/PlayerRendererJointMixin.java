package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.JointedLimbs;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.16.2: bent limbs ({@link JointedLimbs}) only ever apply inside one player's render. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererJointMixin {
	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void projecthero$jointsBegin(AbstractClientPlayer player, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		JointedLimbs.begin();
	}

	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$jointsEnd(AbstractClientPlayer player, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		JointedLimbs.end();
	}
}
