package com.projecthero.mod.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.17.5 gear: a full Celestial armour set makes its wearer immune to Blindness and Darkness
 * ({@link com.projecthero.mod.gear.CelestialSet}). Inert for everyone else.
 */
@Mixin(LivingEntity.class)
public abstract class CelestialSightMixin {
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void projecthero$celestialSight(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if ((effect.is(MobEffects.BLINDNESS) || effect.is(MobEffects.DARKNESS))
				&& com.projecthero.mod.gear.CelestialSet.isWorn((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
