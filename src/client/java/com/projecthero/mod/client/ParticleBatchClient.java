package com.projecthero.mod.client;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.network.ParticleBatchPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;

/**
 * v0.16.7 (perf): spawns a {@link ParticleBatchPayload} exactly the way vanilla's
 * {@code ClientPacketListener.handleParticleEvent} spawns each of the separate packets it replaces -- count 0 = one
 * particle moving at (dx, dy, dz) x speed, otherwise {@code count} particles scattered by gaussian x (dx, dy, dz) with
 * gaussian x speed velocity -- so batched effects look identical.
 */
public final class ParticleBatchClient {
	private static final RandomSource RANDOM = RandomSource.createNewThreadLocalInstance();

	private ParticleBatchClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(ParticleBatchPayload.TYPE, (payload, context) -> context.client().execute(() -> spawn(payload)));
	}

	private static void spawn(ParticleBatchPayload p) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		RandomSource r = RANDOM;
		for (int i = 0; i < p.size(); i++) {
			ParticleOptions particle = p.palette().get(p.pal()[i]);
			boolean force = p.force()[i];
			double x = p.ox() + p.x()[i], y = p.oy() + p.y()[i], z = p.oz() + p.z()[i];
			double dx = p.dx()[i], dy = p.dy()[i], dz = p.dz()[i], speed = p.speed()[i];
			int count = p.count()[i];
			try {
				if (count == 0) {
					level.addParticle(particle, force, x, y, z, speed * dx, speed * dy, speed * dz);
				} else {
					for (int k = 0; k < count; k++) {
						double ox = r.nextGaussian() * dx, oy = r.nextGaussian() * dy, oz = r.nextGaussian() * dz;
						double vx = r.nextGaussian() * speed, vy = r.nextGaussian() * speed, vz = r.nextGaussian() * speed;
						level.addParticle(particle, force, x + ox, y + oy, z + oz, vx, vy, vz);
					}
				}
			} catch (Throwable t) {
				ProjectHeroMod.LOGGER.warn("Could not spawn batched particle {}", particle, t);
				return;
			}
		}
	}
}
