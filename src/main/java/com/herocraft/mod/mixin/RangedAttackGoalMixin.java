package com.herocraft.mod.mixin;

import com.herocraft.mod.spider.SpiderWebs;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The generic ranged-attack goal -- witches throwing harmful potions use this. A web-cocooned witch
 * therefore cannot lob a potion at you while she is wrapped up.
 */
@Mixin(RangedAttackGoal.class)
public class RangedAttackGoalMixin {
	@Shadow
	@Final
	private Mob mob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void herocraft$noRangedWhileCocooned(CallbackInfoReturnable<Boolean> cir) {
		if (SpiderWebs.isCocooned(mob)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void herocraft$stopRangedWhenCocooned(CallbackInfoReturnable<Boolean> cir) {
		if (SpiderWebs.isCocooned(mob)) {
			cir.setReturnValue(false);
		}
	}
}
