package com.projecthero.mod.oathbreaker;

import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.network.WorldEventZoomPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Oathbreaker's shared cosmetic cues: camera shake / zoom to nearby players (reusing the existing
 * {@link TitanShakePayload} and {@link WorldEventZoomPayload} -- no new camera packets) and the
 * particle shapes several attacks share (rings, lines). Server-side only; every call is a pure cosmetic
 * send, nothing here touches gameplay state.
 */
public final class OathbreakerFx {
	/** Default reach of shake/zoom cues. */
	public static final double CUE_RADIUS = 32.0;

	private OathbreakerFx() {
	}

	public static void shake(ServerLevel level, Vec3 center, float intensity, int ticks) {
		TitanShakePayload payload = new TitanShakePayload(intensity, ticks);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= CUE_RADIUS * CUE_RADIUS) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public static void zoom(ServerLevel level, Vec3 center, float amount, int ticks) {
		WorldEventZoomPayload payload = new WorldEventZoomPayload(amount, ticks);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= CUE_RADIUS * CUE_RADIUS) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** A flat ring of {@code points} particles at {@code radius} around {@code center}. */
	public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double speed) {
		for (int i = 0; i < points; i++) {
			double a = (Math.PI * 2.0 * i) / points;
			level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius,
					1, 0.05, 0.02, 0.05, speed);
		}
	}

	/** Particles every {@code spacing} blocks along the segment {@code from -> to}. */
	public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double spacing, double spread) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1.0e-4) {
			return;
		}
		Vec3 step = d.scale(1.0 / len);
		for (double t = 0; t <= len; t += spacing) {
			Vec3 p = from.add(step.scale(t));
			level.sendParticles(particle, p.x, p.y, p.z, 1, spread, spread, spread, 0.0);
		}
	}
}
