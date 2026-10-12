package com.projecthero.mod.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.16.1: Assimilated mobs (the Ultron Contagion's half-metal zombies, skeletons, spiders, creepers and pillagers) are
 * immune to poison. Inert for every other entity.
 */
@Mixin(LivingEntity.class)
public abstract class ContagionPoisonImmunityMixin {
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void projecthero$assimilatedIgnorePoison(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect.is(MobEffects.POISON) && com.projecthero.mod.ultron.contagion.Assimilation.isAssimilated((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
