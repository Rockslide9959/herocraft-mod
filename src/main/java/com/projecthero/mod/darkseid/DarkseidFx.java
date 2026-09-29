package com.projecthero.mod.darkseid;

import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.network.WorldEventZoomPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The raid's shared cosmetic cues. Camera shake and zoom reuse the mod's existing {@link TitanShakePayload} /
 * {@link WorldEventZoomPayload} (no new camera packets); the particle shapes -- rings, lines, discs, columns --
 * are what the telegraphs are drawn with. Server-side, purely cosmetic, never touches gameplay state.
 *
 * <p>Particle budgets are deliberately small and fixed per call (every shape takes a point count), so no attack
 * can turn into a packet flood however many players are watching.
 */
public final class DarkseidFx {
	private DarkseidFx() {
	}

	public static void shake(ServerLevel level, Vec3 center, double radius, float intensity, int ticks) {
		TitanShakePayload payload = new TitanShakePayload(intensity, ticks);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= radius * radius) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public static void zoom(ServerLevel level, Vec3 center, double radius, float amount, int ticks) {
		WorldEventZoomPayload payload = new WorldEventZoomPayload(amount, ticks);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= radius * radius) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** A flat ring of {@code points} particles at {@code radius} around {@code center}. */
	public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = (Math.PI * 2.0 * i) / points;
			level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Particles every {@code spacing} blocks along {@code from -> to}, capped at {@code maxPoints}. */
	public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double spacing, int maxPoints) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1.0e-4) {
			return;
		}
		int n = (int) Math.min(maxPoints, Math.ceil(len / spacing));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(d.scale(i / (double) n));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** A vertical column of particles -- the "you are marked" pillar over a target. */
	public static void column(ServerLevel level, ParticleOptions particle, Vec3 base, double height, int points) {
		for (int i = 0; i < points; i++) {
			level.sendParticles(particle, base.x, base.y + height * i / points, base.z, 1, 0.08, 0.0, 0.08, 0.0);
		}
	}
}
