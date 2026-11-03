package com.projecthero.mod.mixin;

import com.projecthero.mod.kryptonian.KryptoniteStrikes;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.17.2 (user: "should stop their healing and powers for 20 seconds after getting hit 3 times"): a Kryptonian being poisoned by
 * three kryptonite-tool strikes heals nothing at all -- food, potions, regeneration, his own powers -- until it wears off.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityKryptoniteHealMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void projecthero$kryptoniteBlocksHealing(float amount, CallbackInfo ci) {
		if (KryptoniteStrikes.healingBlocked((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
