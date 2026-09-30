package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Super Speed Time Slow (v0.14.5): inside a field every non-player entity only gets one server tick in twenty.
 * {@link SuperSpeedTimeSlow#skipTick} returns on its first check whenever no Time Slow is running.
 */
@Mixin(ServerLevel.class)
public abstract class SuperSpeedTimeSlowMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeSlow(Entity entity, CallbackInfo ci) {
		if (SuperSpeedTimeSlow.skipTick(entity)) {
			ci.cancel();
		}
	}
}
