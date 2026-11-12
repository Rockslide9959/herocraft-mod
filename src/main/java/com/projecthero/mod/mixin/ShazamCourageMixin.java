package com.projecthero.mod.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.17.3 Shazam's Courage of Achilles: while transformed, fear doesn't take -- the Warden's Darkness, and any effect whose id names
 * fear or terror (so another power's fear effect is refused without either side knowing the other). Inert for everyone else.
 */
@Mixin(LivingEntity.class)
public abstract class ShazamCourageMixin {
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void projecthero$shazamCourage(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (!com.projecthero.mod.shazam.Shazam.fearImmune((LivingEntity) (Object) this)) {
			return;
		}
		if (effect.is(MobEffects.DARKNESS)) {
			cir.setReturnValue(false);
			return;
		}
		ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
		if (id != null && (id.getPath().contains("fear") || id.getPath().contains("terror") || id.getPath().contains("frighten"))) {
			cir.setReturnValue(false);
		}
	}
}
