package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.mutation.v0145.TimeSlowClient;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * Super Speed Time Slow (v0.14.7), the caster's own client: the cloud scroll and falling rain / snow (both driven by
 * the level renderer's own tick counter) advance once every 20 of its ticks, like the world.
 */
@Mixin(LevelRenderer.class)
public abstract class SuperSpeedTimeSlowRendererMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$slowClouds(CallbackInfo ci) {
		if (TimeSlowClient.skipWorldTick()) {
			ci.cancel();
		}
	}

	@Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
	private void projecthero$slowRain(Camera camera, CallbackInfo ci) {
		if (TimeSlowClient.skipWorldTick()) {
			ci.cancel();
		}
	}
}
