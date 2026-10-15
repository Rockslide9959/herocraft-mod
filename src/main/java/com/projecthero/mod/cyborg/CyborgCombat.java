package com.projecthero.mod.cyborg;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cyborg's shared hit code (v0.16.5): the mod-wide targeting rules ({@link HeroTargets}), boss caps (at most
 * {@link CyborgConfig#BOSS_MAX_FRACTION_PER_HIT} of max health a hit, never knocked about) and a forgiving look-ray.
 */
public final class CyborgCombat {
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private CyborgCombat() {
	}

	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= CyborgConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	public static boolean isHostile(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.isHostile(owner, e);
	}

	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * CyborgConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		boolean landed;
		ABILITY_HIT.set(true);
		try {
			landed = AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1.0f, dmg));
		} finally {
			ABILITY_HIT.set(false);
		}
		if (!landed) {
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
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.15);
		level.sendParticles(Cyborg.BLUE, c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.0);
		return true;
	}

	/**
	 * The look-ray: the first block, or the first harmable creature whose hitbox (grown by {@code slack}) it passes
	 * through. {@code out[0]} gets the creature, if any; the return value is where the ray stops.
	 */
	public static Vec3 ray(ServerPlayer p, double range, double slack, LivingEntity[] out) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		LivingEntity best = null;
		Vec3 bestAt = null;
		double bestSq = eye.distanceToSqr(end);
		for (Entity e : p.level().getEntities(p, new AABB(eye, end).inflate(slack + 1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && e.isPickable() && e != p.getVehicle())) {
			AABB box = e.getBoundingBox().inflate(e.getPickRadius() + slack);
			Vec3 at = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
			if (at != null && eye.distanceToSqr(at) < bestSq) {
				best = (LivingEntity) e;
				bestAt = at;
				bestSq = eye.distanceToSqr(at);
			}
		}
		if (best != null) {
			out[0] = best;
			return bestAt;
		}
		return end;
	}

	public static List<LivingEntity> within(ServerPlayer owner, Vec3 center, double radius, java.util.function.Predicate<LivingEntity> filter) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity e : ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				filter::test)) {
			if (e != owner && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius) {
				list.add(e);
			}
		}
		return list;
	}
}
