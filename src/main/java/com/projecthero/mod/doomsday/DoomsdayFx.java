package com.projecthero.mod.doomsday;

import org.joml.Vector3f;

import com.projecthero.mod.frostgiant.FrostFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** v0.17.2: Doomsday's particles and shakes (forced, long-range: the arena is 72 blocks across). */
public final class DoomsdayFx {
	public static final DustParticleOptions ASH = new DustParticleOptions(new Vector3f(0.42f, 0.42f, 0.45f), 2.2f);
	public static final DustParticleOptions BONE = new DustParticleOptions(new Vector3f(0.93f, 0.9f, 0.82f), 1.4f);
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95f, 0.08f, 0.05f), 1.6f);
	public static final DustParticleOptions RED_BIG = new DustParticleOptions(new Vector3f(0.85f, 0.05f, 0.03f), 2.6f);
	public static final DustParticleOptions CABLE = new DustParticleOptions(new Vector3f(0.22f, 0.36f, 0.24f), 1.3f);
	public static final DustParticleOptions PHANTOM = new DustParticleOptions(new Vector3f(0.75f, 0.95f, 1.0f), 1.8f);
	public static final BlockParticleOption STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
	public static final BlockParticleOption DIRT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());

	private DoomsdayFx() {
	}

	public static void forced(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed) {
		FrostFx.forced(level, particle, x, y, z, count, dx, dy, dz, speed);
	}

	public static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int points) {
		FrostFx.ring(level, particle, centre, radius, points, true);
	}

	public static void line(ServerLevel level, ParticleOptions particle, Vec3 a, Vec3 b, double step) {
		FrostFx.line(level, particle, a, b, step, true);
	}

	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		FrostFx.shake(level, at, intensity, ticks, radius);
	}

	/** Dirt and stone thrown up where something huge hits the ground. */
	public static void impact(ServerLevel level, Vec3 at, double size) {
		forced(level, DIRT, at.x, at.y + 0.2, at.z, (int) Math.min(140, 30 * size), size, 0.3, size, 0.3);
		forced(level, STONE, at.x, at.y + 0.2, at.z, (int) Math.min(100, 20 * size), size * 0.8, 0.3, size * 0.8, 0.25);
		forced(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, (int) Math.min(8, 2 * size), size * 0.5, 0.2, size * 0.5, 0);
		forced(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, (int) Math.min(30, 6 * size), size * 0.6, 0.2, size * 0.6, 0.02);
	}
}
