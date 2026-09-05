package com.herocraft.mod.mixin;

import com.herocraft.mod.spider.SpiderWebs;

import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A web-cocooned crossbow user (pillager, piglin) cannot raise or fire a crossbow, for the same
 * reason as the bow goal -- a cocoon should take the target out of the fight, ranged included.
 */
@Mixin(RangedCrossbowAttackGoal.class)
public class RangedCrossbowAttackGoalMixin {
	@Shadow
	@Final
	private Monster mob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void herocraft$noCrossbowWhileCocooned(CallbackInfoReturnable<Boolean> cir) {
		if (SpiderWebs.isCocooned(mob)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void herocraft$dropCrossbowWhenCocooned(CallbackInfoReturnable<Boolean> cir) {
		if (SpiderWebs.isCocooned(mob)) {
			cir.setReturnValue(false);
		}
	}
}
