package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.symbiote.SymbioteSkin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.14.4: a mob a Symbiote has taken over -- a hostile Symbiote Host or a loyal Symbiote Pet -- is drawn covered in
 * the organism: its own texture multiplied down to a glossy purple-black ({@link SymbioteSkin#TINT}). Done by
 * rewriting the colour vanilla hands to the body's {@code renderToBuffer}, exactly like
 * {@link LivingEntityPhaseMixin}, so it is one pass (no second model, no z-fighting) and every render layer drawn
 * afterwards -- a collar, wolf armour, sheep wool, the white {@code SymbioteEyesLayer} -- still sits on top.
 *
 * <p>The flag is stashed at the head of {@code render} and cleared at its return; entity rendering is
 * single-threaded, so it cannot leak into another entity's draw.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntitySymbioteSkinMixin {
	@Unique
	private static boolean projecthero$symbioteSkin;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void projecthero$beginSymbioteSkin(LivingEntity entity, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		projecthero$symbioteSkin = SymbioteSkin.covered(entity);
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$endSymbioteSkin(LivingEntity entity, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		projecthero$symbioteSkin = false;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
			index = 4)
	private int projecthero$symbioteSkinTint(int color) {
		return projecthero$symbioteSkin ? SymbioteSkin.tint(color) : color;
	}
}
