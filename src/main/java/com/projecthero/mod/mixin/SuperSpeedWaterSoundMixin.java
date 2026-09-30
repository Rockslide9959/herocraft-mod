package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Super Speed water running (v0.14.7): a speedster skimming the surface dips in and out of the water every few
 * ticks, and vanilla played its loud high-speed splash (and the swim sound) on every dip. Both are muted while
 * water running -- on the server (what everyone else hears) and on the runner's own client -- and
 * {@code SuperSpeedHandlers} plays a soft patter instead.
 */
@Mixin(Entity.class)
public abstract class SuperSpeedWaterSoundMixin {
	@Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
	private void projecthero$quietSplash(CallbackInfo ci) {
		if ((Object) this instanceof Player player && SuperSpeedHandlers.waterRunning(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "playSwimSound", at = @At("HEAD"), cancellable = true)
	private void projecthero$quietSwim(float volume, CallbackInfo ci) {
		if ((Object) this instanceof Player player && SuperSpeedHandlers.waterRunning(player)) {
			ci.cancel();
		}
	}
}
