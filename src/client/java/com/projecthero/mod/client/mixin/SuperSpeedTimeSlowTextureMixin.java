package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.mutation.v0145.TimeSlowClient;

import net.minecraft.client.renderer.texture.TextureManager;

/**
 * Super Speed Time Slow (v0.14.7), the caster's own client: animated textures (water, lava, fire, portals, sea
 * lanterns ...) step once every 20 of its ticks, like the world.
 */
@Mixin(TextureManager.class)
public abstract class SuperSpeedTimeSlowTextureMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$slowTextures(CallbackInfo ci) {
		if (TimeSlowClient.skipWorldTick()) {
			ci.cancel();
		}
	}
}
