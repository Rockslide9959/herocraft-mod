package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.14.4: wearing a boss trophy head halves how far matching undead notice you -- the same hook vanilla's own mob
 * heads use ({@code getVisibilityPercent}, which every mob's target search multiplies its range by). Applies to any
 * wearer, like vanilla heads do on armour stands and mobs. See {@link com.projecthero.mod.grave.TrophyHeads}.
 */
@Mixin(LivingEntity.class)
public abstract class TrophyHeadVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void projecthero$trophyHeadDisguise(Entity looker, CallbackInfoReturnable<Double> cir) {
		double factor = com.projecthero.mod.grave.TrophyHeads.detectionFactor((LivingEntity) (Object) this, looker);
		if (factor < 1.0) {
			cir.setReturnValue(cir.getReturnValue() * factor);
		}
	}
}
