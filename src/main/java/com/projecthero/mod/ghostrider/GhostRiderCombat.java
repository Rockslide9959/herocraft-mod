package com.projecthero.mod.ghostrider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ghost Rider's shared hit code (v0.16.5). Every move goes through {@link #strike}, so every move follows the mod-wide
 * targeting rules ({@link HeroTargets}: never himself, never a squadmate or a friendly pet, other players only with PvP
 * on), bosses take at most {@link GhostRiderConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never
 * knocked about, and hitting a <i>guilty</i> creature feeds Vengeance. Targets are set alight with hellfire.
 */
public final class GhostRiderCombat {
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private GhostRiderCombat() {
	}

	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= GhostRiderConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	/** Rule 1: what an aimed move may hit. */
	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	/** Rule 2: a threat, for area / automatic picks. */
	public static boolean isHostile(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.isHostile(owner, e);
	}

	public static boolean isMovable(ServerPlayer owner, LivingEntity e) {
		return isTarget(owner, e) && !isBoss(e);
	}

	/**
	 * One hit: damage (boss-capped), knockback away from {@code origin}, {@code lift}, and {@code burnSeconds} of fire.
	 * Lands through a recent hit's invulnerability window. True if it landed.
	 */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift,
			int burnSeconds) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = damage;
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * GhostRiderConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
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
		if (burnSeconds > 0 && !target.fireImmune()) {
			target.igniteForSeconds(burnSeconds);
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		if (GhostRiderDamage.guilt(target) > 0) {
			GhostRider.feedVengeance(owner, dmg);
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		ServerLevel level = (ServerLevel) owner.level();
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.03);
		level.sendParticles(GhostRider.EMBER, c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.0);
		return true;
	}

	/** Everything harmable within {@code radius} of {@code center}; damage falls off to 70% at the edge. */
	public static int radial(ServerPlayer owner, Vec3 center, double radius, float damage, double knockback, double lift, int burnSeconds,
			Set<Integer> hit) {
		AABB box = new AABB(center, center).inflate(radius, Math.min(radius, 5.0), radius);
		int n = 0;
		for (LivingEntity e : ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(owner, e))) {
			if (hit != null && hit.contains(e.getId())) {
				continue;
			}
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, center));
			if (dist > radius) {
				continue;
			}
			float scale = (float) (1.0 - 0.3 * (dist / radius));
			if (strike(owner, e, center, damage * scale, knockback, lift, burnSeconds)) {
				n++;
				if (hit != null) {
					hit.add(e.getId());
				}
			}
		}
		return n;
	}

	public static List<LivingEntity> within(ServerPlayer owner, Vec3 center, double radius, java.util.function.Predicate<LivingEntity> filter) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(center, center).inflate(radius);
		for (LivingEntity e : ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, box, filter::test)) {
			if (e != owner && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius) {
				out.add(e);
			}
		}
		return out;
	}

	/** Players near {@code at} get a camera shake (cosmetic). */
	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		double r2 = radius * radius;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2) {
				float falloff = (float) (1.0 - Math.sqrt(d2 / r2) * 0.8);
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * falloff, ticks));
			}
		}
	}

	/** Is {@code e} a player (the Penance Stare reads their kill count)? */
	static boolean isPlayer(LivingEntity e) {
		return e instanceof Player;
	}
}
