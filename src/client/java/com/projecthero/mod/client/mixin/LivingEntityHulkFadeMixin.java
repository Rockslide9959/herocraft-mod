package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.hulk.HulkFade;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.15: Banner's half of the Hulk cross-fade. While the Hulk phases onto (or off) a Gamma player,
 * {@code PlayerRendererHulkMixin} draws the Hulk model at the Hulk's share of the fade and lets vanilla carry on drawing
 * Banner -- this makes that Banner draw see-through by the rest, the same way {@link LivingEntityPhaseMixin} does it
 * (the {@code translucent} flag picks {@code itemEntityTranslucentCull}; the colour's alpha is scaled). His render
 * layers (held items, cape, arrows, ...) are skipped for the length of the fade -- they cannot fade, and a solid sword
 * floating in a ghost's hand reads worse than none.
 *
 * <p>The alpha is stashed at the head of {@code render} for the two argument rewrites, which are not handed the entity;
 * entity rendering is single-threaded, so it cannot interleave with another entity's draw.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityHulkFadeMixin {
	/** 1 = draw normally; below 1 = Banner is fading while the Hulk takes (or gives back) his body. */
	@Unique
	private static float projecthero$hulkFade = 1.0f;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void projecthero$beginHulkFade(LivingEntity entity, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		projecthero$hulkFade = entity instanceof Player player ? HulkFade.bannerAlpha(player, partialTicks) : 1.0f;
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$endHulkFade(LivingEntity entity, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		projecthero$hulkFade = 1.0f;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"),
			index = 2)
	private boolean projecthero$hulkFadeTranslucent(boolean translucent) {
		return translucent || projecthero$hulkFade < 0.999f;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
			index = 4)
	private int projecthero$hulkFadeAlpha(int color) {
		if (projecthero$hulkFade >= 0.999f) {
			return color;
		}
		int alpha = Math.round(((color >>> 24) & 0xFF) * projecthero$hulkFade);
		return (alpha << 24) | (color & 0xFFFFFF);
	}

	@WrapWithCondition(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
	private boolean projecthero$hulkFadeLayers(RenderLayer<?, ?> layer, PoseStack poseStack, MultiBufferSource buffer, int light,
			Entity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		return projecthero$hulkFade >= 0.999f;
	}
}
