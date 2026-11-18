package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.supersoldier.CapShieldPose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

/** v0.17.5: the Captain America shield arm poses -- eased block, bash, throw, reach and catch ({@link CapShieldPose}). */
@Mixin(HumanoidModel.class)
public abstract class CapShieldPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$capShieldPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		CapShieldPose.applyPose(entity, (HumanoidModel<?>) (Object) this, limbSwing, limbSwingAmount, ageInTicks);
	}
}
