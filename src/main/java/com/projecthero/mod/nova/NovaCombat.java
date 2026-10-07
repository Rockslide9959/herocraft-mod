package com.projecthero.mod.nova;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Nova's shared hit code (v0.15.13). Every move goes through here, so every move follows the mod-wide targeting rules
 * ({@link HeroTargets}): never himself, never a squadmate (or a squadmate's pet, or his own), other players only with PvP
 * on. Bosses ({@link #isBoss}) take at most {@link NovaConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are
 * never knocked back, grabbed, lifted or pulled. The Overload's +50% is applied here.
 */
public final class NovaCombat {
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private NovaCombat() {
	}

	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= NovaConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	/** Rule 1: something an aimed / area Nova move may affect. */
	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	/** Rule 2: a threat, for automatic picks (homing bolts, Orbital Launch, the Worldmind mark). */
	public static boolean isHostile(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.isHostile(owner, e);
	}

	/** A mob (not a player) that gravity moves may lift / pull / freeze: harmable and not a boss. */
	public static boolean isMovableMob(ServerPlayer owner, LivingEntity e) {
		return !(e instanceof Player) && isTarget(owner, e) && !isBoss(e);
	}

	public static List<LivingEntity> targets(ServerPlayer owner, AABB box) {
		return ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(owner, e));
	}

	/**
	 * One hit: damage (Overload-boosted, boss-capped), then knockback away from {@code origin} and {@code lift}. Lands
	 * through a recent hit's invulnerability window. True if it landed.
	 */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = damage * Nova.damageMultiplier(owner);
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * NovaConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		boolean landed;
		ABILITY_HIT.set(true);
		try {
			landed = AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1.0f, dmg));
		} finally {
			ABILITY_HIT.set(false);
		}
		if (landed && !boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		if (landed) {
			Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
			((ServerLevel) owner.level()).sendParticles(Nova.GOLD, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.0);
		}
		return landed;
	}

	/** Everything within {@code radius} of {@code center}; damage falls off to 70% at the edge. Returns how many were hit. */
	public static int radial(ServerPlayer owner, Vec3 center, double radius, float damage, double knockback, double lift, Set<Integer> hit) {
		AABB box = new AABB(center, center).inflate(radius, Math.min(radius, 6.0), radius);
		int n = 0;
		for (LivingEntity e : targets(owner, box)) {
			if (hit != null && hit.contains(e.getId())) {
				continue;
			}
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, center));
			if (dist > radius) {
				continue;
			}
			float scale = (float) (1.0 - 0.3 * (dist / radius));
			if (strike(owner, e, center, damage * scale, knockback, lift)) {
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
			if (e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius) {
				out.add(e);
			}
		}
		return out;
	}

	/** A flat ring of particles. */
	public static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double radius, int points) {
		int n = Math.min(64, points);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.0, 0.03, 0.0, 0.0);
		}
	}

	/** An expanding shockwave look: several rings out to {@code radius}. */
	public static void shockwave(ServerLevel level, Vec3 c, double radius) {
		for (double r = 1.0; r <= radius + 0.01; r += Math.max(1.0, radius / 5.0)) {
			ring(level, Nova.GOLD_BIG, c, r, (int) (8 + r * 5));
		}
		ring(level, Nova.CYAN, c.add(0, 0.2, 0), radius * 0.5, 20);
	}

	/** Camera shake for players near {@code at} (purely cosmetic). */
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
}
