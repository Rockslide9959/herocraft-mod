package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight Phase 5 -- Jake Lockley is hard to notice. Every mob's target search ({@code TargetingConditions})
 * multiplies its detection range by {@code getVisibilityPercent}, so scaling that for a Jake halves how close a mob
 * must be before it notices him (and cuts it much further while he has Vanished). Players, and anyone not a
 * transformed Jake, are untouched.
 */
@Mixin(LivingEntity.class)
public abstract class MoonKnightVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void projecthero$jakeGoesUnnoticed(Entity looker, CallbackInfoReturnable<Double> cir) {
		if ((Object) this instanceof Player player && looker instanceof Mob) {
			double factor = com.projecthero.mod.moonknight.ability.MoonKnightAlters.detectionFactor(player);
			if (factor < 1.0) {
				cir.setReturnValue(cir.getReturnValue() * factor);
			}
		}
	}
}
