package com.projecthero.mod.blackpanther;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Black Panther's shared hit code (v0.17.3): the mod-wide targeting rules ({@link HeroTargets}) and the boss cap. */
public final class BlackPantherCombat {
	private BlackPantherCombat() {
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= BlackPantherConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	/** A boss takes at most this much from one hit. */
	static float capped(LivingEntity target, float damage) {
		return isBoss(target) ? Math.min(damage, target.getMaxHealth() * BlackPantherConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
	}

	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1.0f, capped(target, damage)))) {
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
		level.sendParticles(BlackPanther.PURPLE, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.25, 0.25, 0.25, 0.1);
		BlackPanther.fought(owner);
		return true;
	}

	public static List<LivingEntity> within(ServerPlayer owner, Vec3 center, double radius, Predicate<LivingEntity> filter) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity e : ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), filter::test)) {
			if (e != owner && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius) {
				list.add(e);
			}
		}
		return list;
	}
}
