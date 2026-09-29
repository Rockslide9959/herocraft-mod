package com.projecthero.mod.hero.revamp;

import java.util.Map;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.13.22 batch C: small particle-geometry helpers shared by Electrokinesis, Sonic Scream, Energy Absorption,
 * Shockwave and Wind -- rings perpendicular to a direction (sonic rings, distortion rings, gust rings), jagged
 * lightning arcs, inward/outward spirals -- plus the one-off save migration that removes resource keys a reworked
 * kit no longer uses. Everything here is bounded: every call sends a fixed, small number of particles.
 */
public final class BatchCFx {
	private BatchCFx() {
	}

	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f),
				size);
	}

	/** Two unit vectors perpendicular to {@code axis} (and to each other). */
	private static Vec3[] basis(Vec3 axis) {
		Vec3 a = axis.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : axis.normalize();
		Vec3 helper = Math.abs(a.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = a.cross(helper).normalize();
		Vec3 v = a.cross(u).normalize();
		return new Vec3[] { u, v };
	}

	/**
	 * A ring of {@code count} particles of radius {@code r} around {@code c}, in the plane perpendicular to {@code axis}.
	 * With {@code outward > 0} each particle is fired outward from the centre (a ring that expands).
	 */
	public static void ring(ServerLevel level, Vec3 c, Vec3 axis, double r, int count, ParticleOptions p, double outward) {
		Vec3[] b = basis(axis);
		count = Math.max(4, Math.min(64, count));
		for (int i = 0; i < count; i++) {
			double a = i * (Math.PI * 2 / count);
			Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
			Vec3 at = c.add(dir.scale(r));
			if (outward > 0) {
				level.sendParticles(p, at.x, at.y, at.z, 0, dir.x, dir.y, dir.z, outward);
			} else {
				level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
	}

	/** A horizontal ring (axis straight up). */
	public static void flatRing(ServerLevel level, Vec3 c, double r, int count, ParticleOptions p, double outward) {
		ring(level, c, new Vec3(0, 1, 0), r, count, p, outward);
	}

	/**
	 * Rings of growing radius marching down a direction: the Warden-style "visible sound" look (and, with another
	 * particle, a shockwave's distortion). {@code step} blocks between rings, radius grows by {@code grow} per block.
	 */
	public static void ringTrail(ServerLevel level, Vec3 origin, Vec3 dir, double length, double step, double r0, double grow,
			ParticleOptions p) {
		Vec3 d = dir.normalize();
		int rings = Math.max(1, Math.min(24, (int) (length / step)));
		for (int i = 1; i <= rings; i++) {
			double along = i * step;
			double r = r0 + along * grow;
			ring(level, origin.add(d.scale(along)), d, r, (int) (10 + r * 6), p, 0);
		}
	}

	/** A jagged lightning arc from {@code a} to {@code b}: straight-line particles with a random kink every block. */
	public static void arc(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double jitter) {
		RandomSource rnd = level.random;
		double len = a.distanceTo(b);
		int kinks = Math.max(1, Math.min(24, (int) len));
		Vec3 prev = a;
		for (int i = 1; i <= kinks; i++) {
			Vec3 target = a.lerp(b, (double) i / kinks);
			if (i < kinks) {
				target = target.add((rnd.nextDouble() - 0.5) * jitter, (rnd.nextDouble() - 0.5) * jitter,
						(rnd.nextDouble() - 0.5) * jitter);
			}
			double seg = prev.distanceTo(target);
			int n = Math.max(1, (int) (seg * 3));
			for (int k = 0; k <= n; k++) {
				Vec3 q = prev.lerp(target, (double) k / n);
				level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
			}
			prev = target;
		}
	}

	/** Particles spiralling INTO {@code c} from radius {@code r} (vacuum, absorption) -- fired with a velocity toward the centre. */
	public static void inwardSpiral(ServerLevel level, Vec3 c, double r, int count, ParticleOptions p, double speed, long phase) {
		count = Math.max(4, Math.min(48, count));
		for (int i = 0; i < count; i++) {
			double a = phase * 0.3 + i * (Math.PI * 2 / count);
			double y = (i % 4) * 0.5 - 0.75;
			Vec3 at = c.add(Math.cos(a) * r, y, Math.sin(a) * r);
			Vec3 to = c.subtract(at).normalize();
			// add a little swirl so it curls in
			Vec3 swirl = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.35);
			Vec3 v = to.add(swirl).normalize();
			level.sendParticles(p, at.x, at.y, at.z, 0, v.x, v.y, v.z, speed);
		}
	}

	// ---------------- save migration ----------------

	/**
	 * Removes resource keys a reworked kit no longer uses (so a stale reserve bar from the old design can never be
	 * drawn again) and stale toggle keys of abilities that no longer exist. A single attachment write, only when
	 * something is actually there -- call it freely from a passive tick.
	 */
	public static void purge(ServerPlayer player, String powerKey, Set<String> resources, Set<String> toggles) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null) {
			return;
		}
		boolean any = false;
		for (String r : resources) {
			if (st.resources.containsKey(powerKey + "/" + r)) {
				any = true;
				break;
			}
		}
		if (!any) {
			for (String t : toggles) {
				if (st.activeToggles.contains(powerKey + "/" + t)) {
					any = true;
					break;
				}
			}
		}
		if (!any) {
			return;
		}
		ExperimentalState copy = st.copy();
		for (String r : resources) {
			copy.resources.remove(powerKey + "/" + r);
		}
		for (String t : toggles) {
			copy.activeToggles.remove(powerKey + "/" + t);
		}
		player.setAttached(ModAttachments.EXPERIMENTAL_STATE, copy);
	}

	/** Removes one resource key outright (bookkeeping that must not linger in the synced map). */
	public static void removeResource(ServerPlayer player, String powerKey, String name) {
		purge(player, powerKey, Set.of(name), Set.of());
	}

	/** Read-only view for predicates that must be cheap (visual flags run for every player every 4 ticks). */
	public static boolean ownsQuick(ServerPlayer player, String powerKey) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(powerKey);
	}

	public static boolean toggledQuick(ServerPlayer player, String powerKey, String abilityId) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(powerKey) && st.activeToggles.contains(powerKey + "/" + abilityId);
	}

	public static float resourceQuick(ServerPlayer player, String powerKey, String name) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null) {
			return 0f;
		}
		Map<String, Float> r = st.resources;
		Float v = r.get(powerKey + "/" + name);
		return v == null ? 0f : v;
	}
}
