package com.projecthero.mod.ultron;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: Ultron's look. All of his energy is <b>red</b>: the beams go to the clients as {@link UltronBeamPayload}s
 * (drawn glow-first, core-second by the client), the rest is dust.
 */
public final class UltronFx {
	/** Beam kinds (width / brightness on the client). */
	public static final int BOLT = 0;
	public static final int SNIPER_SHOT = 1;
	public static final int TELEGRAPH = 2;
	public static final int ENCEPHALO = 3;
	public static final int CANNON = 4;
	public static final int STREAK = 5;
	public static final int LASER_SIGHT = 6;
	public static final int TETHER = 7;

	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.05f), 1.4f);
	public static final DustParticleOptions RED_SMALL = new DustParticleOptions(new Vector3f(1.0f, 0.12f, 0.08f), 0.8f);
	public static final DustParticleOptions RED_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.05f, 0.05f), 2.6f);
	public static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.2f, 0.9f, 1.0f), 1.6f);
	public static final DustParticleOptions STEEL = new DustParticleOptions(new Vector3f(0.62f, 0.64f, 0.7f), 1.2f);

	private UltronFx() {
	}

	/** A red beam {@code a -> b} for every player within 96 blocks, for {@code life} ticks. */
	public static void beam(ServerLevel level, Vec3 a, Vec3 b, int kind, int life) {
		UltronBeamPayload payload = new UltronBeamPayload(a, b, kind, life);
		Vec3 mid = a.add(b).scale(0.5);
		double reach = 96.0 + a.distanceTo(b) * 0.5;
		for (ServerPlayer p : PlayerLookup.around(level, mid, reach)) {
			if (ServerPlayNetworking.canSend(p, UltronBeamPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** Particles every fighter sees from anywhere in the arena (long-range / forced). */
	public static void forced(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(x, y, z) < 128 * 128) {
				level.sendParticles(p, particle, true, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	/** A spray of red sparks and steel debris (a robot breaking up). */
	public static void wreck(ServerLevel level, Vec3 at, double size) {
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(RED, at.x, at.y, at.z, (int) (10 * size), 0.4 * size, 0.4 * size, 0.4 * size, 0.05);
		level.sendParticles(STEEL, at.x, at.y, at.z, (int) (12 * size), 0.5 * size, 0.5 * size, 0.5 * size, 0.1);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (int) (10 * size), 0.4 * size, 0.4 * size, 0.4 * size, 0.2);
	}

	/** A ring of dust at height {@code y}. */
	public static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			level.sendParticles(particle, centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0);
		}
	}
}
