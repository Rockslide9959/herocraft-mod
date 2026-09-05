package com.herocraft.mod.mixin;

import com.herocraft.mod.spider.SpiderWebs;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A web-cocooned archer cannot draw a bow. Skeletons, strays and illusioners all use this goal, so
 * webbing one up now actually stops it shooting -- the point of the cocoon is that the target is
 * "out of the fight", and standing there plinking arrows was not that.
 */
@Mixin(RangedBowAttackGoal.class)
public class RangedBowAttackGoalMixin {
	@Shadow
	@Final
	private Monster mob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void herocraft$noBowWhileCocooned(CallbackInfoReturnable<Boolean> cir) {
		if (SpiderWebs.isCocooned(mob)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void herocraft$dropBowWhenCocooned(CallbackInfoReturnable<Boolean> cir) {
		if (SpiderWebs.isCocooned(mob)) {
			cir.setReturnValue(false);
		}
	}
}
