package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: dodgeable bolts of Power Cosmic, simulated on the server by their shooter (the Surfer, a Punisher's gauntlet) --
 * no projectile entity, nothing saved: a bolt flies {@code speed} blocks a tick, draws itself, stops at the first block and
 * hurts the first target its path crosses. One small list per shooter (an instance field, never static).
 */
public final class CosmicBolts {
	private static final class Bolt {
		Vec3 pos;
		final Vec3 vel;
		int life;
		final float damage;
		final Predicate<LivingEntity> filter;
		final DustParticleOptions color;

		Bolt(Vec3 pos, Vec3 vel, int life, float damage, Predicate<LivingEntity> filter, DustParticleOptions color) {
			this.pos = pos;
			this.vel = vel;
			this.life = life;
			this.damage = damage;
			this.filter = filter;
			this.color = color;
		}
	}

	private final List<Bolt> bolts = new ArrayList<>();

	public int size() {
		return bolts.size();
	}

	public void clear() {
		bolts.clear();
	}

	/** Fires a bolt from {@code from} towards {@code to} at {@code speed} blocks a tick, reaching at most {@code range}. */
	public void fire(Vec3 from, Vec3 to, double speed, double range, float damage, Predicate<LivingEntity> filter, DustParticleOptions color) {
		if (bolts.size() >= 12) {
			return;
		}
		Vec3 d = to.subtract(from);
		if (d.lengthSqr() < 1.0e-4) {
			return;
		}
		Vec3 vel = d.normalize().scale(speed);
		bolts.add(new Bolt(from, vel, (int) Math.ceil(range / speed), damage, filter, color));
	}

	/** Moves every bolt one tick; {@code owner} is credited with the hits. */
	public void tick(ServerLevel level, LivingEntity owner) {
		for (Iterator<Bolt> it = bolts.iterator(); it.hasNext();) {
			Bolt b = it.next();
			Vec3 next = b.pos.add(b.vel);
			HitResult block = level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
			LivingEntity victim = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(b.pos, end).inflate(0.6), b.filter)) {
				if (e == owner) {
					continue;
				}
				var clip = e.getBoundingBox().inflate(0.35).clip(b.pos, end);
				if (clip.isPresent() || e.getBoundingBox().contains(b.pos)) {
					double d = clip.map(v -> v.distanceToSqr(b.pos)).orElse(0.0);
					if (d < best) {
						best = d;
						victim = e;
					}
				}
			}
			level.sendParticles(b.color, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0);
			level.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y, b.pos.z, 1, 0, 0, 0, 0);
			if (victim != null) {
				victim.invulnerableTime = 0;
				victim.hurt(level.damageSources().indirectMagic(owner, owner), b.damage);
				level.sendParticles(ParticleTypes.FLASH, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 1, 0, 0, 0, 0);
				it.remove();
				continue;
			}
			if (block.getType() != HitResult.Type.MISS || --b.life <= 0) {
				level.sendParticles(b.color, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.02);
				it.remove();
				continue;
			}
			b.pos = next;
		}
	}
}
