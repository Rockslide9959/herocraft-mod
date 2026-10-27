package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.quirk.erasure.ErasureExposed;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.1 Erasure revamp ({@link ErasureExposed}): Exposed -- an erased creature takes +20% from every hit, cannot sprint and
 * gets no jump boost -- and Underground (an Eraser sneaking in the dark is seen at half range).
 */
@Mixin(LivingEntity.class)
public abstract class ErasureExposedMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$erasureExposed(float amount, DamageSource source) {
		return ErasureExposed.scale((LivingEntity) (Object) this, source, amount);
	}

	@ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
	private boolean projecthero$erasureNoSprint(boolean sprinting) {
		return sprinting && !ErasureExposed.exposed((LivingEntity) (Object) this);
	}

	@Inject(method = "getJumpBoostPower", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasureNoJumpBoost(CallbackInfoReturnable<Float> cir) {
		if (ErasureExposed.exposed((LivingEntity) (Object) this)) {
			cir.setReturnValue(0f);
		}
	}

	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void projecthero$erasureUnderground(Entity looking, CallbackInfoReturnable<Double> cir) {
		double v = cir.getReturnValueD();
		double w = ErasureExposed.visibility((LivingEntity) (Object) this, v);
		if (w != v) {
			cir.setReturnValue(w);
		}
	}
}
