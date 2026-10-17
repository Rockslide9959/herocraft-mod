package com.projecthero.mod.miraculous.catnoir;

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
 * v0.16.6: Cat Noir's shared hit code. Every baton hit goes through {@link #strike}: the mod-wide targeting rules
 * ({@link HeroTargets}: never himself, a squadmate or a friendly pet, players only with PvP on), bosses take at most
 * {@link CatNoirConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked about.
 */
public final class CatNoirCombat {
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private CatNoirCombat() {
	}

	/** True while one of his moves is dealing its damage (damage hooks can tell it from a plain punch). */
	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= CatNoirConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && HeroTargets.canHarm(owner, e);
	}

	/** One hit: damage (boss-capped at {@code bossFraction}), knockback away from {@code origin}, {@code lift}. True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift,
			float bossFraction) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * bossFraction) : damage;
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
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
		((ServerLevel) owner.level()).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.25);
		return true;
	}

	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		return strike(owner, target, origin, damage, knockback, lift, CatNoirConfig.BOSS_MAX_FRACTION_PER_HIT);
	}

	/**
	 * Everything harmable in a cone in front of {@code owner}: within {@code reach} of the eyes (to the nearest point of
	 * its box) and inside the cone of half-angle cos {@code cone} round the flat look direction.
	 */
	public static List<LivingEntity> cone(ServerPlayer owner, double reach, double cone) {
		Vec3 eye = owner.getEyePosition();
		Vec3 look = owner.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		List<LivingEntity> out = new ArrayList<>();
		AABB box = owner.getBoundingBox().inflate(reach + 1.0);
		for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(owner, e))) {
			if (AbilityHelpers.distanceSqToBox(e, eye) > reach * reach
					&& AbilityHelpers.distanceSqToBox(e, owner.position().add(0, 0.5, 0)) > reach * reach) {
				continue;
			}
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(owner.position());
			Vec3 toFlat = new Vec3(to.x, 0, to.z);
			if (toFlat.lengthSqr() < 0.36 || toFlat.normalize().dot(flat) >= cone) {
				out.add(e);
			}
		}
		return out;
	}
}
