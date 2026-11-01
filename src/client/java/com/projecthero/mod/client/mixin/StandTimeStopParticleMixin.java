package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.stand.TimeStopClient;

import net.minecraft.client.particle.ParticleEngine;

/** v0.17.2 Star Platinum: The World -- particles hang in the air (no tick, drawn at partial tick 0) while time is stopped. */
@Mixin(ParticleEngine.class)
public abstract class StandTimeStopParticleMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeStopParticles(CallbackInfo ci) {
		if (TimeStopClient.active()) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float projecthero$timeStopParticlePartial(float partialTick) {
		return TimeStopClient.active() ? 0f : partialTick;
	}
}
