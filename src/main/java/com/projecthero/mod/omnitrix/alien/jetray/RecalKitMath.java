package com.projecthero.mod.omnitrix.alien.jetray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, group B: Jetray, Big Chill, Chromastone): the small aiming and flight helpers the three basic
 * kits share -- hitscan beams along a direction (stopped by blocks), cones, and the creative-safe flight switch. Targets always
 * come from {@link AlienKit#around} (never the wearer, a squad ally or a mount / rider).
 */
public final class RecalKitMath {
	private RecalKitMath() {
	}

	/** Where a beam from the eyes along {@code dir} stops: the first block in the way, else {@code range} out. */
	public static Vec3 beamEnd(ServerPlayer p, Vec3 dir, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(dir.normalize().scale(range));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/**
	 * Every creature a beam from the eyes along {@code dir} passes within {@code radius} of (out to the first block, at most
	 * {@code range}), nearest first.
	 */
	public static List<LivingEntity> line(ServerPlayer p, Vec3 dir, double range, double radius) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = beamEnd(p, dir, range);
		Vec3 seg = end.subtract(eye);
		double len = seg.length();
		List<LivingEntity> out = new ArrayList<>();
		if (len < 1.0e-3) {
			return out;
		}
		Vec3 n = seg.scale(1.0 / len);
		Vec3 mid = eye.add(seg.scale(0.5));
		for (LivingEntity e : AlienKit.around(p, mid, len * 0.5 + radius + 2.0)) {
			Vec3 c = e.getBoundingBox().getCenter();
			double t = c.subtract(eye).dot(n);
			if (t < 0 || t > len + 0.5) {
				continue;
			}
			Vec3 closest = eye.add(n.scale(t));
			double reach = radius + e.getBbWidth() * 0.5 + 0.1;
			// the box, not just its centre: the beam passes the body anywhere from the feet to the head
			double dy = closest.y < e.getY() ? e.getY() - closest.y : closest.y > e.getY() + e.getBbHeight() ? closest.y - e.getY() - e.getBbHeight() : 0.0;
			double dx = closest.x - c.x, dz = closest.z - c.z;
			if (dx * dx + dz * dz <= reach * reach && dy <= radius + 0.1) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
		return out;
	}

	/** The first creature in {@link #line}, or null. */
	public static LivingEntity first(ServerPlayer p, Vec3 dir, double range, double radius) {
		List<LivingEntity> l = line(p, dir, range, radius);
		return l.isEmpty() ? null : l.get(0);
	}

	/** Creatures within {@code range} whose centre is within {@code halfAngleDeg} of the look. */
	public static List<LivingEntity> cone(ServerPlayer p, double range, double halfAngleDeg) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double cos = Math.cos(Math.toRadians(halfAngleDeg));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : AlienKit.around(p, eye, range + 1.0)) {
			Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
			double dist = d.length();
			if (dist > range + e.getBbWidth() * 0.5 || dist < 1.0e-3) {
				if (dist < 1.0e-3) {
					out.add(e);
				}
				continue;
			}
			if (d.scale(1.0 / dist).dot(look) >= cos || dist < 1.2) {
				out.add(e);
			}
		}
		return out;
	}

	/** The look turned {@code yawDeg} about the vertical. */
	public static Vec3 turned(Vec3 look, double yawDeg) {
		double a = Math.toRadians(yawDeg);
		double c = Math.cos(a), s = Math.sin(a);
		return new Vec3(look.x * c - look.z * s, look.y, look.x * s + look.z * c);
	}

	/** Creative / spectator by the game mode (a gametest mock answers {@code isCreative()} true whatever its mode). */
	public static boolean creative(ServerPlayer p) {
		GameType mode = p.gameMode.getGameModeForPlayer();
		return mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
	}

	/** The wings: {@code mayfly} on and the flying speed, flying now if {@code fly}. */
	public static void wings(ServerPlayer p, boolean fly, float speed) {
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = fly;
		p.getAbilities().setFlyingSpeed(speed);
		p.onUpdateAbilities();
		if (fly) {
			p.resetFallDistance();
		}
	}

	/** Turning back: flight taken away (unless creative / spectator) and the normal flying speed. */
	public static void groundWings(ServerPlayer p) {
		if (!creative(p)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		p.resetFallDistance();
	}
}
