package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.stand.TimeStop;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.2 Star Platinum: The World -- while time is stopped, every entity in the caster's dimension but the caster (and its
 * mount) skips its tick: no AI, no movement, no projectile flight, no falling blocks. The game clock keeps running, so the
 * mod's cooldowns and animations stay right ({@link TimeStop}).
 */
@Mixin(ServerLevel.class)
public abstract class StandTimeStopServerLevelMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeStop(Entity entity, CallbackInfo ci) {
		if (TimeStop.active() && TimeStop.frozen(entity)) {
			ci.cancel();
		}
	}
}
