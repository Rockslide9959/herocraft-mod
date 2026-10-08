package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.greenlantern.PowerBatteryHeldRenderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.15, third person: the Power Battery hangs from the fist by its handle and swings
 * ({@link PowerBatteryHeldRenderer#renderThirdPerson}) instead of being held like a block.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerPowerBatteryMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$hangingPowerBattery(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		if (!PowerBatteryHeldRenderer.isBattery(stack)
				|| !(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
			return;
		}
		PowerBatteryHeldRenderer.renderThirdPerson(entity, model, arm, stack, poseStack, buffer, packedLight);
		ci.cancel();
	}
}
