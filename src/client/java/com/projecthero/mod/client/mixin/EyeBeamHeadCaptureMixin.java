package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.eyebeam.EyeBeamFx;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.5 eye beams: just before a player's model is drawn (pose and head angles all set), remember where its head is, so
 * {@link EyeBeamFx} can start each beam exactly at the eyes whatever the pose -- crouching, flying, swimming, scaled.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class EyeBeamHeadCaptureMixin {
	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
			require = 0)
	private void projecthero$eyeBeamHead(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (entity instanceof net.minecraft.world.entity.player.Player) {
			EyeBeamFx.captureHead(entity, ((LivingEntityRenderer<?, ?>) (Object) this).getModel(), pose);
		}
	}
}
