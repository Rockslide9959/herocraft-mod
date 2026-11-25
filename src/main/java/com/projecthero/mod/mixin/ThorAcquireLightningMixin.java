package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.thoracquire.LivingBranch;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;

/**
 * v0.17.7: the Living Branch -- on a lightning bolt's first tick (natural or any of Thor's, visual-only ones included) the
 * jungle tree it struck may drop one ({@link LivingBranch#onStrike}).
 */
@Mixin(LightningBolt.class)
public abstract class ThorAcquireLightningMixin {
	@Shadow
	private int life;

	@Inject(method = "tick", at = @At("HEAD"))
	private void projecthero$livingBranch(CallbackInfo ci) {
		LightningBolt self = (LightningBolt) (Object) this;
		if (life == 2 && self.level() instanceof ServerLevel level) {
			LivingBranch.onStrike(level, self.blockPosition());
		}
	}
}
