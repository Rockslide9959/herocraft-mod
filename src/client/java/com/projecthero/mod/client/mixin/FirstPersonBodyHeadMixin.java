package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.fpbody.FirstPersonBody;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.15.15 full-body first person ({@link FirstPersonBody}): once the body model is posed for this frame (setupAnim, with
 * every keyframed suit-up pose applied), remember where its head is, so the head volume can be culled from every layer.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FirstPersonBodyHeadMixin<T extends LivingEntity, M extends EntityModel<T>> {
	@Shadow
	protected M model;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
					shift = At.Shift.AFTER))
	private void projecthero$fpBodyHead(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (FirstPersonBody.renderingSelf(entity) && model instanceof HumanoidModel<?> humanoid) {
			FirstPersonBody.nudgeArms(humanoid);
			FirstPersonBody.captureHead(pose, humanoid.head, entity, partialTick);
		}
	}
}
