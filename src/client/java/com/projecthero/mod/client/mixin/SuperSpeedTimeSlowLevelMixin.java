package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.mutation.v0145.TimeSlowClient;

import net.minecraft.world.level.Level;

/**
 * Super Speed Time Slow (v0.14.7), the caster's own client: block entities (chest lids, bells, pistons, spawners,
 * beacons, enchanting books ...) animate once every 20 of its ticks, like the world around them.
 */
@Mixin(Level.class)
public abstract class SuperSpeedTimeSlowLevelMixin {
	@Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
	private void projecthero$slowBlockEntities(CallbackInfo ci) {
		if (((Level) (Object) this).isClientSide() && TimeSlowClient.skipWorldTick()) {
			ci.cancel();
		}
	}
}
