package com.projecthero.mod.hero.revamp.d;

import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.13.22 revamp, batch D: shared particle vocabulary and small animation helpers for Telekinesis,
 * Teleportation, Light, Shadow, Gravity and Magnetism. Everything here is server-side and bounded (a fixed
 * particle count per call), so a move can call it every tick without flooding clients.
 */
public final class BatchDFx {
	public static final DustParticleOptions PSI = new DustParticleOptions(new Vector3f(0.72f, 0.3f, 1.0f), 1.3f);
	public static final DustParticleOptions PSI_BIG = new DustParticleOptions(new Vector3f(0.62f, 0.2f, 0.95f), 2.2f);
	public static final DustParticleOptions TELE_SMOKE = new DustParticleOptions(new Vector3f(0.45f, 0.12f, 0.6f), 2.6f);
	public static final DustParticleOptions TELE_GLOW = new DustParticleOptions(new Vector3f(0.85f, 0.55f, 1.0f), 1.0f);
	public static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.45f), 1.4f);
	public static final DustParticleOptions PRISM = new DustParticleOptions(new Vector3f(0.7f, 0.95f, 1.0f), 1.1f);
	public static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.03f, 0.02f, 0.05f), 2.4f);
	public static final DustParticleOptions SHADOW_EYE = new DustParticleOptions(new Vector3f(0.7f, 0.25f, 1.0f), 0.9f);
	public static final DustParticleOptions GRAVITY = new DustParticleOptions(new Vector3f(0.3f, 0.08f, 0.45f), 1.8f);
	public static final DustParticleOptions MAGNET = new DustParticleOptions(new Vector3f(0.55f, 0.62f, 0.72f), 1.0f);

	/** A one-shot pose is assumed finished this many ticks after it started (every library one-shot is shorter). */
	private static final int ONE_SHOT_TICKS = 26;

	private BatchDFx() {
	}

	/**
	 * Keeps a looping idle pose (hover, float) on the player without cutting off a one-shot move that is still
	 * playing: the loop only (re)starts once the previous animation has had time to finish.
	 */
	public static void ensureIdle(ServerPlayer p, String anim) {
		var s = MutationVisuals.state(p);
		if (anim.equals(s.anim())) {
			return;
		}
		if (s.anim().isEmpty() || p.level().getGameTime() - s.animStart() > ONE_SHOT_TICKS) {
			MutationVisuals.play(p, anim);
		}
	}

	/** A ring of {@code count} particles around {@code c} in the horizontal plane. */
	public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions particle, int count, double phase) {
		for (int i = 0; i < count; i++) {
			double a = phase + i * (Math.PI * 2.0 / count);
			level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	/** A dotted line from {@code a} to {@code b}, at most {@code maxPoints} points. */
	public static void tether(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, int maxPoints) {
		double len = a.distanceTo(b);
		int n = Math.max(2, Math.min(maxPoints, (int) (len * 2)));
		for (int i = 0; i <= n; i++) {
			Vec3 p = a.lerp(b, i / (double) n);
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
		}
	}

	/**
	 * A brief body-shaped afterimage at {@code feet}, facing {@code yawDeg}: head, torso, arms and legs traced in
	 * {@code particle}. Teleportation leaves one at every departure point; ~46 particles.
	 */
	public static void silhouette(ServerLevel level, Vec3 feet, float yawDeg, ParticleOptions particle) {
		double yaw = Math.toRadians(yawDeg);
		// the body's local "right" vector (perpendicular to the facing, horizontal)
		double rx = Math.cos(yaw);
		double rz = Math.sin(yaw);
		// head: a small ring
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			double side = Math.cos(a) * 0.22;
			double up = 1.72 + Math.sin(a) * 0.22;
			level.sendParticles(particle, feet.x + rx * side, feet.y + up, feet.z + rz * side, 1, 0, 0, 0, 0);
		}
		// torso: two vertical edges
		for (int i = 0; i < 6; i++) {
			double up = 0.78 + i * 0.13;
			for (double side : new double[] { -0.24, 0.24 }) {
				level.sendParticles(particle, feet.x + rx * side, feet.y + up, feet.z + rz * side, 1, 0, 0, 0, 0);
			}
		}
		// arms, hanging a little out from the body
		for (int i = 0; i < 5; i++) {
			double up = 0.78 + i * 0.14;
			for (double side : new double[] { -0.5, 0.5 }) {
				double s = side * (1.0 - (4 - i) * 0.04);
				level.sendParticles(particle, feet.x + rx * s, feet.y + up, feet.z + rz * s, 1, 0, 0, 0, 0);
			}
		}
		// legs
		for (int i = 0; i < 5; i++) {
			double up = 0.05 + i * 0.16;
			for (double side : new double[] { -0.13, 0.13 }) {
				level.sendParticles(particle, feet.x + rx * side, feet.y + up, feet.z + rz * side, 1, 0, 0, 0, 0);
			}
		}
	}

	/** Teleportation's purple smoke puff (used at every departure and arrival). */
	public static void puff(ServerLevel level, Vec3 feet) {
		level.sendParticles(TELE_SMOKE, feet.x, feet.y + 0.9, feet.z, 22, 0.35, 0.6, 0.35, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 0.9, feet.z, 6, 0.3, 0.5, 0.3, 0.02);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1.0, feet.z, 14, 0.3, 0.6, 0.3, 0.08);
	}

	/** The dark purple "space bending" shimmer Gravity uses everywhere. */
	public static void distortion(ServerLevel level, Vec3 c, double spread, int count) {
		level.sendParticles(GRAVITY, c.x, c.y, c.z, count, spread, spread, spread, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, Math.max(1, count / 2), spread, spread, spread, 0.02);
	}

	public static Vec3 centre(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}
}
