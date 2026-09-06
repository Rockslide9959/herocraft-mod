package com.projecthero.mod.client;

import com.projecthero.mod.network.IronManBeamPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the repulsor / Unibeam beam client-side from an {@link IronManBeamPayload}. A client particle
 * line is visible from the shooter's own eyes (a server-spawned line starting at the eye is not),
 * which is what makes the first-person beam work.
 */
public final class IronManBeamClient {
	private IronManBeamClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(IronManBeamPayload.TYPE, (payload, context) ->
				context.client().execute(() -> draw(context.client(), payload)));
	}

	private static void draw(Minecraft client, IronManBeamPayload beam) {
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}
		Vec3 a = beam.start();
		Vec3 b = beam.end();
		double length = a.distanceTo(b);
		// kind 3 = the Mark 4 wrist laser: a thin, dense red line.
		boolean redLaser = beam.kind() == 3;
		int steps = Math.max(2, (int) (length * (beam.kind() == 2 ? 8 : redLaser ? 12 : 5)));
		ParticleOptions core = redLaser
				? new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.05f, 0.05f), 0.7f)
				: ParticleTypes.END_ROD;
		ParticleOptions spark = redLaser ? ParticleTypes.FLAME
				: beam.kind() == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.GLOW;

		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			Vec3 p = a.lerp(b, t);
			level.addParticle(core, p.x, p.y, p.z, 0, 0, 0);
			if (i % (redLaser ? 4 : 2) == 0) {
				level.addParticle(spark, p.x, p.y, p.z, 0, 0, 0);
			}
		}
		// impact flash
		level.addParticle(beam.kind() == 2 || redLaser ? ParticleTypes.EXPLOSION : ParticleTypes.FLASH, b.x, b.y, b.z, 0, 0, 0);
		if (beam.kind() == 2) {
			level.playLocalSound(a.x, a.y, a.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 0.6f, false);
		}
	}
}
