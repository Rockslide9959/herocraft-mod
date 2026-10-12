package com.projecthero.mod.frostgiant;

import org.joml.Vector3f;

import com.projecthero.mod.network.TitanShakePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Frost Giant Assault's look on the server side -- pale blue / white dust, snowflakes, ice shards, the
 * forced (long-range) particles every fighter sees from across the arena, and the camera shake a big giant's landing or
 * footfall sends (the Titan Shifter's {@link TitanShakePayload}). Throttled by the callers.
 */
public final class FrostFx {
	public static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(0.62f, 0.78f, 0.91f), 1.6f);
	public static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.95f, 0.98f, 1.0f), 1.6f);
	public static final DustParticleOptions FROST = new DustParticleOptions(new Vector3f(0.55f, 0.88f, 1.0f), 1.2f);
	public static final DustParticleOptions FROST_BIG = new DustParticleOptions(new Vector3f(0.7f, 0.93f, 1.0f), 2.6f);
	/** Telegraph ink: the shadow of Ymir's palm, the crack lines. */
	public static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.08f, 0.1f, 0.18f), 2.4f);
	public static final DustParticleOptions RUNE = new DustParticleOptions(new Vector3f(0.45f, 0.95f, 1.0f), 1.0f);
	public static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.15f), 1.0f);
	public static final BlockParticleOption ICE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
	public static final BlockParticleOption SNOW = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());

	private FrostFx() {
	}

	/** Particles every player within 128 blocks sees (long-range / forced). */
	public static void forced(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(x, y, z) < 128 * 128) {
				level.sendParticles(p, particle, true, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	/** A ring of {@code particle} at {@code centre}'s height. */
	public static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int points, boolean force) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			double x = centre.x + Math.cos(a) * radius;
			double z = centre.z + Math.sin(a) * radius;
			if (force) {
				forced(level, particle, x, centre.y, z, 1, 0, 0.05, 0, 0);
			} else {
				level.sendParticles(particle, x, centre.y, z, 1, 0, 0.05, 0, 0);
			}
		}
	}

	/** A dotted line {@code a -> b}. */
	public static void line(ServerLevel level, ParticleOptions particle, Vec3 a, Vec3 b, double step, boolean force) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		int n = Math.max(1, (int) (len / Math.max(0.2, step)));
		for (int i = 0; i <= n; i++) {
			Vec3 p = a.add(d.scale(i / (double) n));
			if (force) {
				forced(level, particle, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
			} else {
				level.sendParticles(particle, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
			}
		}
	}

	/** Snow-burst where a giant lands. */
	public static void landing(ServerLevel level, Vec3 at, double size) {
		forced(level, ParticleTypes.SNOWFLAKE, at.x, at.y + 0.3, at.z, (int) (30 * size), size, 0.3, size, 0.15);
		forced(level, SNOW, at.x, at.y + 0.2, at.z, (int) (20 * size), size, 0.2, size, 0.2);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, (int) (8 * size), size * 0.6, 0.1, size * 0.6, 0.05);
	}

	/** Shatter into ice shards and snow. */
	public static void shatter(ServerLevel level, Vec3 at, double size) {
		forced(level, ICE, at.x, at.y, at.z, (int) (30 * size), size * 0.5, size * 0.6, size * 0.5, 0.25);
		forced(level, ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, (int) (20 * size), size * 0.5, size * 0.6, size * 0.5, 0.1);
		forced(level, WHITE, at.x, at.y, at.z, (int) (12 * size), size * 0.5, size * 0.5, size * 0.5, 0.05);
	}

	/**
	 * A tremor for everyone within {@code radius} of {@code at}: the Titan Shifter's camera shake, falling off with
	 * distance. Purely cosmetic.
	 */
	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(at);
			if (d > radius || !ServerPlayNetworking.canSend(p, TitanShakePayload.TYPE)) {
				continue;
			}
			float falloff = (float) (1.0 - d / radius);
			ServerPlayNetworking.send(p, new TitanShakePayload(intensity * falloff, ticks));
		}
	}
}
