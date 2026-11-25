package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7: Shazam's move animations ({@link com.projecthero.mod.client.shazam.ShazamPose}) at the end of the humanoid pose, a late
 * priority so they win over the flight pose while a move plays. Inert for anyone who isn't a transformed Shazam.
 */
@Mixin(value = HumanoidModel.class, priority = 1500)
public abstract class ShazamPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$shazamPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player) {
			com.projecthero.mod.client.shazam.ShazamPose.apply(player, (HumanoidModel<?>) (Object) this);
		}
	}
}
