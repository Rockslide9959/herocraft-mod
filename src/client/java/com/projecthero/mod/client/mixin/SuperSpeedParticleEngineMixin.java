package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.mutation.v0145.SuperSpeedClientV0145;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

/**
 * Super Speed Time Slow (v0.14.7): particles inside a Time Slow field run at 5% too. On the 19 ticks in 20 they skip,
 * a particle neither ages nor runs its own physics -- it just creeps 1/20th of its current velocity (collision-aware),
 * so it drifts on smoothly instead of freezing and jumping. Inert (one list check) whenever no Time Slow is running.
 */
@Mixin(ParticleEngine.class)
public abstract class SuperSpeedParticleEngineMixin {
	@Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
	private void projecthero$timeSlowParticle(Particle particle, CallbackInfo ci) {
		if (SuperSpeedClientV0145.slowParticle(particle)) {
			ci.cancel();
		}
	}
}
