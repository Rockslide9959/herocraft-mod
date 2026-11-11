package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.gyro.Gyro;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** v0.17.3 Gyro Zeppeli: no name tag over him while his Face Spin disguise lasts (whatever the team rules say). */
@Mixin(LivingEntityRenderer.class)
public abstract class GyroNameTagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void projecthero$gyroDisguiseNoNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && Gyro.disguised(player)) {
			cir.setReturnValue(false);
		}
	}
}
