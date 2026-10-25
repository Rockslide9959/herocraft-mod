package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.silversurfer.SilverSurferClientState;
import com.projecthero.mod.client.silversurfer.SilverSurferRender;
import com.projecthero.mod.silversurfer.SilverSurfer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Silver Surfer, for every viewer: a creature Transmutation turned to silver is drawn as solid silver (its own
 * model washed over with a brushed-silver texture right after vanilla draws it), and the Surfer himself goes see-through
 * while Molecular Phase runs (the Density Manipulation approach: the translucent render type and a 60% alpha colour).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SilverSurferStatueMixin {
	@Shadow
	protected EntityModel<?> model;

	@Unique
	private static boolean projecthero$surferPhaseNow;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void projecthero$surferBegin(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		projecthero$surferPhaseNow = entity instanceof Player p && SilverSurfer.phasing(p);
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$surferEnd(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		projecthero$surferPhaseNow = false;
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
					shift = At.Shift.AFTER))
	private void projecthero$surferStatue(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (SilverSurferClientState.statue(entity)) {
			model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(SilverSurferRender.STATUE)), light, OverlayTexture.NO_OVERLAY,
					0xE6FFFFFF);
		}
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"),
			index = 2)
	private boolean projecthero$surferPhaseTranslucent(boolean translucent) {
		return translucent || projecthero$surferPhaseNow;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
			index = 4)
	private int projecthero$surferPhaseAlpha(int color) {
		return projecthero$surferPhaseNow ? 0x99FFFFFF : color;
	}
}
