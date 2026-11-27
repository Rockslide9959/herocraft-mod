package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 Thor x Super Soldier: the lent-weapon moves' third-person poses ({@code client.worthy.WorthyPose}). Its own mixin (priority
 * 1100, so its TAIL hook runs after {@code HumanoidModelMixin}'s flight, Thor and Super Soldier poses) -- a move made mid-flight wins
 * over the flight arm pose, the flight body lean still applies underneath.
 */
@Mixin(value = HumanoidModel.class, priority = 1100)
public abstract class WorthyPoseMixin<T extends LivingEntity> {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$worthyPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player) {
			com.projecthero.mod.client.worthy.WorthyPose.apply(player, (HumanoidModel<?>) (Object) this);
		}
	}
}
