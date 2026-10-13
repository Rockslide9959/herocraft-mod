package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.power.ThorStorm;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.1: the thunderstorm buff's one damage hook -- a storm-charged Thor's hits are +30% ({@link ThorStorm}). Scales
 * the incoming amount at the top of {@code LivingEntity#hurt}, server side only, like the Punisher's mark; the RETURN
 * half closes the frame {@link ThorStorm#enter} opened, so a hit re-issued from inside this one is never scaled twice.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityThorStormMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$thorStormBonus(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return amount;
		}
		return ThorStorm.enter(self, source, amount);
	}

	@Inject(method = "hurt", at = @At("RETURN"))
	private void projecthero$thorStormExit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!((LivingEntity) (Object) this).level().isClientSide()) {
			ThorStorm.exit();
		}
	}
}
