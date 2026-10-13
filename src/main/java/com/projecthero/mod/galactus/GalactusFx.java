package com.projecthero.mod.galactus;

import org.joml.Vector3f;

import com.projecthero.mod.frostgiant.FrostFx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: Galactus' look on the server side -- Power Cosmic violet and silver dust, the long-range (forced) particles every
 * fighter sees across the 80-block arena, and the Titan Shifter's camera shake (through {@link FrostFx}). Every caller
 * throttles itself; nothing here loops over the world.
 */
public final class GalactusFx {
	public static final DustParticleOptions COSMIC = new DustParticleOptions(new Vector3f(0.62f, 0.25f, 0.95f), 2.0f);
	public static final DustParticleOptions COSMIC_SMALL = new DustParticleOptions(new Vector3f(0.75f, 0.45f, 1.0f), 1.0f);
	public static final DustParticleOptions SILVER = new DustParticleOptions(new Vector3f(0.86f, 0.9f, 0.98f), 1.4f);
	public static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(0.12f, 0.03f, 0.2f), 2.6f);
	public static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.3f, 0.55f, 1.0f), 1.6f);
	public static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0f, 0.4f, 0.75f), 1.2f);

	private GalactusFx() {
	}

	public static void forced(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed) {
		FrostFx.forced(level, particle, x, y, z, count, dx, dy, dz, speed);
	}

	public static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int points) {
		FrostFx.ring(level, particle, centre, radius, Math.min(points, 160), true);
	}

	public static void line(ServerLevel level, ParticleOptions particle, Vec3 a, Vec3 b, double step) {
		double len = a.distanceTo(b);
		// never more than ~120 points a call, however long the line
		FrostFx.line(level, particle, a, b, Math.max(step, len / 120.0), true);
	}

	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		FrostFx.shake(level, at, intensity, ticks, radius);
	}

	/** A burst of Power Cosmic (arrivals, teleports, breaks). */
	public static void burst(ServerLevel level, Vec3 at, double size) {
		forced(level, ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		forced(level, COSMIC, at.x, at.y, at.z, (int) Math.min(120, 30 * size), size, size, size, 0.1);
		forced(level, ParticleTypes.END_ROD, at.x, at.y, at.z, (int) Math.min(80, 20 * size), size * 0.6, size * 0.6, size * 0.6, 0.25);
	}
}
