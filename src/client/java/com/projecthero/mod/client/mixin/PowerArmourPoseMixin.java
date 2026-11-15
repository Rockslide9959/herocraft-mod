package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.powerarmour.PowerArmourPose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** v0.17.5 Power Armour: the suit's move poses (punch wind-up, stomp, charge, overload, weapons) over vanilla's own pose. */
@Mixin(HumanoidModel.class)
public abstract class PowerArmourPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$powerArmourPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player) {
			PowerArmourPose.apply(player, (HumanoidModel<?>) (Object) this, ageInTicks);
		}
	}
}
