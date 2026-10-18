package com.projecthero.mod.mixin;

import com.projecthero.mod.quirk.erasure.ErasureMobs;

import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.7 Erasure: marks the mob whose server AI step is running as erased or not, so {@code ErasureWrappedGoalMixin}
 * can refuse that mob's power goals (ranged attacks, creeper swell, spells, the guardian beam). See {@link ErasureMobs}.
 */
@Mixin(Mob.class)
public abstract class ErasureMobAiMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"))
	private void projecthero$erasureEnter(CallbackInfo ci) {
		ErasureMobs.enterAi((Mob) (Object) this);
	}

	@Inject(method = "serverAiStep", at = @At("RETURN"))
	private void projecthero$erasureExit(CallbackInfo ci) {
		ErasureMobs.exitAi();
	}
}
