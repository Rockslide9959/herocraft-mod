package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.mutation.v0145.TimeSlowClient;

import net.minecraft.client.Minecraft;

/**
 * Super Speed Time Slow (v0.14.7): the synced slowed tick rate would stretch every client's tick to a second -- the
 * caster's own client keeps its normal 50 ms tick (the rest of its world is held back tick by tick instead, see
 * {@link TimeSlowClient}).
 */
@Mixin(Minecraft.class)
public abstract class SuperSpeedTimeSlowMinecraftMixin {
	@Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
	private void projecthero$casterKeepsPace(float defaultMillis, CallbackInfoReturnable<Float> cir) {
		if (TimeSlowClient.isLocalCaster()) {
			cir.setReturnValue(defaultMillis);
		}
	}
}
