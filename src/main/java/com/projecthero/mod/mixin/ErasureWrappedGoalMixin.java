package com.projecthero.mod.mixin;

import com.projecthero.mod.quirk.erasure.ErasureMobs;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** v0.16.7 Erasure: an erased mob's power goals neither start nor keep running ({@link ErasureMobs#blocksGoal}). */
@Mixin(WrappedGoal.class)
public abstract class ErasureWrappedGoalMixin {
	@Shadow
	@Final
	private Goal goal;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasedCannotUse(CallbackInfoReturnable<Boolean> cir) {
		if (ErasureMobs.blocksGoal(goal)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasedStops(CallbackInfoReturnable<Boolean> cir) {
		if (ErasureMobs.blocksGoal(goal)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasedNoTick(CallbackInfo ci) {
		if (ErasureMobs.blocksGoal(goal)) {
			ci.cancel();
		}
	}
}
