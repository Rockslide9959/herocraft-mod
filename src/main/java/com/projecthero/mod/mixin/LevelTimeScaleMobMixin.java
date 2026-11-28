package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.time.LevelTimeScale;

import net.minecraft.world.entity.Mob;

/**
 * v0.17.7 per-dimension time scale ({@link LevelTimeScale}): the despawn roll runs outside the entity tick, so a held mob
 * skips it too (it would otherwise despawn twenty times as fast as it lives).
 */
@Mixin(Mob.class)
public abstract class LevelTimeScaleMobMixin {
	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void projecthero$levelTimeScaleDespawn(CallbackInfo ci) {
		if (LevelTimeScale.holds((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
