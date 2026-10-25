package com.projecthero.mod.falcon;

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
 * The Falcon's shared hit code (v0.17.1): the mod-wide targeting rules ({@link HeroTargets#canHarm}), boss caps (at most
 * {@link FalconConfig#BOSS_MAX_FRACTION_PER_HIT} of max health a hit, never knocked about, never grabbed) and a forgiving
 * look-ray. Every hit goes through {@link AbilityHelpers#hurtBurst}.
 */
public final class FalconCombat {
	private FalconCombat() {
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= FalconConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && HeroTargets.canHarm(owner, e);
	}

	public static boolean isHostile(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.isHostile(owner, e);
	}

	/** A hit: capped on bosses (who are never knocked back or lifted). True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * FalconConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
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
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.2);
		return true;
	}

	/** Whether this creature can be picked up (Lift & Carry / Talon Drop): not a boss, not too big, not a passenger. */
	public static boolean grabbable(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && !isBoss(e) && !e.isPassenger() && e.getBbHeight() <= 2.6f && e.getBbWidth() <= 1.6f
				&& AbilityHelpers.isValidGrabTarget(e, owner);
	}

	/**
	 * The look-ray: the first block, or the first creature (matching {@code filter}) whose hitbox (grown by {@code slack}) it
	 * passes through. {@code out[0]} gets the creature, if any; the return value is where the ray stops.
	 */
	public static Vec3 ray(ServerPlayer p, Vec3 from, Vec3 dir, double range, double slack, java.util.function.Predicate<LivingEntity> filter,
			LivingEntity[] out) {
		Vec3 far = from.add(dir.normalize().scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		LivingEntity best = null;
		Vec3 bestAt = null;
		double bestSq = from.distanceToSqr(end);
		for (Entity e : p.level().getEntities(p, new AABB(from, end).inflate(slack + 1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && e.isPickable() && e != p.getVehicle() && filter.test(le))) {
			AABB box = e.getBoundingBox().inflate(e.getPickRadius() + slack);
			Vec3 at = box.contains(from) ? from : box.clip(from, end).orElse(null);
			if (at != null && from.distanceToSqr(at) < bestSq) {
				best = (LivingEntity) e;
				bestAt = at;
				bestSq = from.distanceToSqr(at);
			}
		}
		if (best != null) {
			out[0] = best;
			return bestAt;
		}
		return end;
	}

	/** The look-ray from the eyes, for anything harmable. */
	public static Vec3 ray(ServerPlayer p, double range, double slack, LivingEntity[] out) {
		return ray(p, p.getEyePosition(), p.getLookAngle(), range, slack, e -> isTarget(p, e), out);
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
