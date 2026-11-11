package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: the Spirit Bomb's pose (arms straight overhead while rising and charging, the throw). A later priority than
 * {@code HumanoidModelMixin}, so it runs after the other poses and wins while the move lasts.
 */
@Mixin(value = HumanoidModel.class, priority = 1500)
public abstract class SpiritBombPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$spiritBombPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player) {
			com.projecthero.mod.client.saiyan.kit.SpiritBombFx.pose(player, (HumanoidModel<?>) (Object) this);
		}
	}
}
