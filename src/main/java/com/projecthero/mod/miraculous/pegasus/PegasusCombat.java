package com.projecthero.mod.miraculous.pegasus;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: Pegasus's shared hit code and his golden light. Every hit goes through {@link #strike}: the mod-wide targeting
 * rules ({@link HeroTargets}: never himself, a squadmate or a friendly pet; players only with PvP on); bosses take at
 * most {@link PegasusConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked about.
 */
public final class PegasusCombat {
	/** His colours: the gold of the Horseshoe and the white-gold of the portals. */
	public static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.36f), 1.1f);
	public static final DustParticleOptions GOLD_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.55f), 1.8f);
	public static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0f, 0.98f, 0.9f), 1.0f);

	private PegasusCombat() {
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= PegasusConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin} and {@code lift}. True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * PegasusConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1f, dmg))) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
		ServerLevel level = owner.serverLevel();
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.2);
		level.sendParticles(GOLD, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	/** The creature under the crosshair, else the valid one nearest the crosshair within {@code degrees} and {@code range} that he can see. */
	public static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && isTarget(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && isTarget(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > range) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	/** Every valid target within {@code radius} of the segment {@code a}-{@code b} not yet in {@code hit} (added). */
	public static int alongPath(ServerPlayer owner, Vec3 a, Vec3 b, double radius, float damage, double knockback, double lift, Set<Integer> hit) {
		AABB box = new AABB(a, b).inflate(radius, 1.5, radius);
		int n = 0;
		Vec3 ab = b.subtract(a);
		double len2 = Math.max(1.0e-6, ab.lengthSqr());
		for (LivingEntity e : owner.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && isTarget(owner, e))) {
			if (hit.contains(e.getId())) {
				continue;
			}
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.4, 0);
			double t = Math.max(0.0, Math.min(1.0, c.subtract(a).dot(ab) / len2));
			Vec3 near = a.add(ab.scale(t));
			Vec3 flat = new Vec3(c.x - near.x, 0, c.z - near.z);
			if (flat.length() > radius + e.getBbWidth() * 0.5 || Math.abs(c.y - near.y) > 2.0) {
				continue;
			}
			hit.add(e.getId());
			// thrown aside, away from the path, and up
			Vec3 side = flat.lengthSqr() < 1.0e-4 ? new Vec3(-ab.z, 0, ab.x) : flat;
			if (strike(owner, e, c.subtract(side.normalize()), damage, knockback, lift)) {
				n++;
			}
		}
		return n;
	}

	/** A puff of golden light. */
	public static void glow(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(GOLD, c.x, c.y, c.z, count, spread, spread * 1.3, spread, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, Math.max(1, count / 4), spread, spread, spread, 0.02);
	}
}
