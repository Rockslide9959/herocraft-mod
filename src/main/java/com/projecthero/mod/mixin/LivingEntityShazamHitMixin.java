package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.shazam.ShazamCombat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.7 Shazam, Planet tier: every hit he deals passes {@link ShazamCombat#enter} at the top of {@code LivingEntity#hurt} -- the
 * Wisdom of Solomon crit, the 40 cap, the boss cap. Server side only; the RETURN half closes the frame (Thor's storm-buff pattern).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityShazamHitMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$shazamHit(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return amount;
		}
		return ShazamCombat.enter(self, source, amount);
	}

	@Inject(method = "hurt", at = @At("RETURN"))
	private void projecthero$shazamHitExit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!((LivingEntity) (Object) this).level().isClientSide()) {
			ShazamCombat.exit();
		}
	}
}
