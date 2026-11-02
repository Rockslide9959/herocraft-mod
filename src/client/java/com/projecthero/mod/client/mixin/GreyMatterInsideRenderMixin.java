package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.alien.GreyMatterClient;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 Grey Matter revamp, Inside Job: he shrinks into the creature over 4 ticks and is not drawn while inside
 * ({@link GreyMatterClient#hiddenInside}); the creature he is in shudders and bulges in small pulses
 * ({@link GreyMatterClient#insideJitter}).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class GreyMatterInsideRenderMixin {
	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$greyMatterHidden(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (GreyMatterClient.hiddenInside(entity, partial)) {
			ci.cancel();
		}
	}

	@Inject(method = "scale", at = @At("TAIL"))
	private void projecthero$greyMatterJitter(LivingEntity entity, PoseStack pose, float partial, CallbackInfo ci) {
		GreyMatterClient.insideJitter(entity, pose, partial);
	}
}
