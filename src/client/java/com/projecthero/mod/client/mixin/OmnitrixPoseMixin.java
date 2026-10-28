package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.omnitrix.OmnitrixPose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

/** v0.17.1: the Omnitrix's third-person poses (the raised wrist, the slap, the helix, the alien kits' moves). */
@Mixin(HumanoidModel.class)
public abstract class OmnitrixPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$omnitrixPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof AbstractClientPlayer player) {
			OmnitrixPose.apply(player, (HumanoidModel<?>) (Object) this);
		}
	}
}
