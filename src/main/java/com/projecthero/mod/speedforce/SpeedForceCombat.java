package com.projecthero.mod.speedforce;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Speed Force Speedster's shared hit code (v0.17.1): the mod-wide targeting rules ({@link HeroTargets#canHarm}), boss
 * caps (at most {@link SpeedForceConfig#BOSS_MAX_FRACTION_PER_HIT} of max health a hit, never knocked about, no crowd
 * control). Every hit goes through {@link AbilityHelpers#hurtBurst}, so each blow of a flurry lands in full.
 */
public final class SpeedForceCombat {
	private SpeedForceCombat() {
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= SpeedForceConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && e.getVehicle() != owner && HeroTargets.canHarm(owner, e);
	}

	/** One blow: capped on bosses (no knockback / lift on them), sparks at the target. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * SpeedForceConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1.0f, dmg))) {
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
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		ServerLevel level = (ServerLevel) owner.level();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.15);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.2);
		return true;
	}

	public static List<LivingEntity> within(ServerPlayer owner, Vec3 center, double radius) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity e : ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				e -> isTarget(owner, e))) {
			if (e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius) {
				list.add(e);
			}
		}
		return list;
	}

	/** The nearest target to {@code center} within {@code radius} (excluding {@code skip}), or null. */
	public static LivingEntity nearest(ServerPlayer owner, Vec3 center, double radius, List<LivingEntity> skip) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : within(owner, center, radius)) {
			if (skip != null && skip.contains(e)) {
				continue;
			}
			double d = e.distanceToSqr(center);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}
}
