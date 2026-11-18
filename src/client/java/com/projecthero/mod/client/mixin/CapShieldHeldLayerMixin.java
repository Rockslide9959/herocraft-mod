package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.supersoldier.CapShieldModel;
import com.projecthero.mod.client.supersoldier.CapShieldPose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5, third person: a Captain America shield in a humanoid's hand is strapped to the forearm
 * ({@link CapShieldPose#renderStrapped}) instead of being drawn as a held item at the hand.
 */
@Mixin(ItemInHandLayer.class)
public abstract class CapShieldHeldLayerMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$capShieldStrapped(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!CapShieldModel.isCapShield(stack)) {
			return;
		}
		if (!(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
			return;
		}
		ci.cancel();
		pose.pushPose();
		model.translateToHand(arm, pose);
		CapShieldPose.renderStrapped(entity, stack, arm, pose, buffers, light);
		pose.popPose();
	}
}
