package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.syndicate.KingpinCanePose;
import com.projecthero.mod.syndicate.KingpinCaneSwing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.31: during a Kingpin's Cane strike ({@link KingpinCanePose}) the held cane is turned about the hand's X axis
 * by the strike's grip, so it follows the arm out of the fist (laid flat for the swipe, pointed at the target for the
 * thrust, angled down at the end of the chop) instead of standing at its resting angle. Main hand only, players and
 * mobs alike; inside vanilla's own push / pop, so nothing leaks.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerCaneMixin {
	@Inject(method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$caneGrip(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		if (arm != entity.getMainArm() || !KingpinCaneSwing.isCane(stack)) {
			return;
		}
		float grip = KingpinCanePose.grip(entity);
		if (Math.abs(grip) > 0.001f) {
			poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f * grip));
		}
	}
}
