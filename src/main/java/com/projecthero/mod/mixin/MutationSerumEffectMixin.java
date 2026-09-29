package com.projecthero.mod.mixin;

import com.projecthero.mod.hero.mutation.ModMobEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.13.22: the newest mutation serum always wins. Every serum is the same marker effect with the power's index
 * as its amplifier, and vanilla's effect merging keeps the HIGHER amplifier -- so drinking, say, Super Strength
 * (index 0) while a Size serum (index 26) was active did nothing at all. Removing the old marker first makes the
 * new drink replace it cleanly; {@code MutationManager} then notices the new target and restarts the attempt.
 */
@Mixin(LivingEntity.class)
public abstract class MutationSerumEffectMixin {
	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"))
	private void projecthero$newestSerumWins(MobEffectInstance instance, Entity source, CallbackInfoReturnable<Boolean> cir) {
		if (instance == null || !instance.is(ModMobEffects.UNSTABLE_MUTATION)) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		MobEffectInstance current = self.getEffect(ModMobEffects.UNSTABLE_MUTATION);
		if (current != null && current.getAmplifier() != instance.getAmplifier()) {
			self.removeEffect(ModMobEffects.UNSTABLE_MUTATION);
		}
	}
}
