package com.projecthero.mod.client.mixin;

import java.util.Map;
import java.util.Queue;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.fpbody.FirstPersonBody;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15 full-body first person ({@link FirstPersonBody}): while the wearer watches their own suit-up, every particle
 * (sparks, smoke, steam, puffs, dust -- any type, from the server or the client) within {@value #NEAR} blocks of the
 * camera is dropped: they are spawned round the body for everyone else to see, and from inside the body they smothered
 * the view. Third person and every other viewer still see them.
 *
 * <p>Two passes: a new particle near the lens is refused outright, and every tick the live ones near it are removed --
 * a suit-up's opening burst can land a moment before the state that starts the sequence, and smoke drifts in.
 */
@Mixin(ParticleEngine.class)
public abstract class FirstPersonBodyParticleMixin {
	private static final double NEAR = 2.0;

	@Shadow
	@Final
	private Map<ParticleRenderType, Queue<Particle>> particles;

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void projecthero$fpBodyNoParticlesAtTheLens(Particle particle, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		// distance first (cheap), then the sequence check
		if (particle.getBoundingBox().getCenter().distanceToSqr(cam) < NEAR * NEAR && FirstPersonBody.activeOrJustEnded()) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void projecthero$fpBodyClearTheLens(CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !FirstPersonBody.active()) {
			return;
		}
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		for (Queue<Particle> queue : particles.values()) {
			for (Particle p : queue) {
				if (p.isAlive() && p.getBoundingBox().getCenter().distanceToSqr(cam) < NEAR * NEAR) {
					p.remove();
				}
			}
		}
	}
}
