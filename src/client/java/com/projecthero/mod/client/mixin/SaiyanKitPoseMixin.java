package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.saiyan.kit.SaiyanKitPose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** v0.17.3 Saiyan revamp: the move kit's limb poses on every player model ({@link SaiyanKitPose#apply}). */
@Mixin(HumanoidModel.class)
public abstract class SaiyanKitPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$saiyanKitPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player) {
			SaiyanKitPose.apply(player, (HumanoidModel<?>) (Object) this);
		}
	}
}
