package com.projecthero.mod.quirk.engine;

import java.util.Set;
import java.util.function.BiPredicate;

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
 * v0.16.7: Engine's shared hit code and its exhaust colours. Every hit goes through {@link #strike} (x1.5 in Recipro Turbo): the mod-wide
 * targeting rules ({@link HeroTargets}: never himself, a squadmate or a friendly pet; players only with PvP on); bosses
 * take at most {@link EngineConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked about.
 */
public final class EngineCombat {
	/** The blue-white of the exhaust flames. */
	public static final DustParticleOptions EXHAUST = new DustParticleOptions(new Vector3f(0.55f, 0.82f, 1.0f), 1.0f);
	public static final DustParticleOptions EXHAUST_WHITE = new DustParticleOptions(new Vector3f(0.9f, 0.96f, 1.0f), 1.3f);

	private EngineCombat() {
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= EngineConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
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
		if (Engine.turbo(owner)) {
			damage *= EngineConfig.TURBO_DAMAGE_MULT; // v0.16.7: Recipro Turbo -- every hit +50%
		}
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * EngineConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
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
		level.sendParticles(EXHAUST, c.x, c.y, c.z, 4, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	/** The creature under the crosshair, else the one nearest the crosshair within {@code degrees} and {@code range} he can see. */
	public static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees, BiPredicate<ServerPlayer, LivingEntity> ok) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && ok.test(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && ok.test(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.3 || d > range + le.getBbWidth() * 0.5) {
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

	/** Every target ({@code ok}) within {@code radius} of the segment {@code a}-{@code b} not yet in {@code hit} (added) is struck. */
	public static int alongPath(ServerPlayer owner, Vec3 a, Vec3 b, double radius, float damage, double knockback, double lift, Set<Integer> hit,
			BiPredicate<ServerPlayer, LivingEntity> ok) {
		AABB box = new AABB(a, b).inflate(radius, 1.5, radius);
		int n = 0;
		Vec3 ab = b.subtract(a);
		double len2 = Math.max(1.0e-6, ab.lengthSqr());
		for (LivingEntity e : owner.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && ok.test(owner, e))) {
			if (hit.contains(e.getId()) || e.getVehicle() == owner) {
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
			Vec3 side = flat.lengthSqr() < 1.0e-4 ? new Vec3(-ab.z, 0, ab.x) : flat;
			if (side.lengthSqr() < 1.0e-6) {
				side = new Vec3(1, 0, 0);
			}
			if (strike(owner, e, c.subtract(side.normalize()), damage, knockback, lift)) {
				n++;
			}
		}
		return n;
	}

	/** Where the calf engines sit (server approximation of the drawn nozzles: the outer back of each calf). */
	public static Vec3 calf(ServerPlayer p, int side) {
		float yaw = p.yBodyRot * ((float) Math.PI / 180f);
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		return p.position().add(right.scale(side * 0.22 * p.getScale())).add(fwd.scale(-0.1 * p.getScale())).add(0, 0.38 * p.getScale(), 0);
	}

	/** Blue-white exhaust fire out of both calves' engines. */
	public static void exhaust(ServerLevel level, ServerPlayer p, int count, double speed) {
		for (int side = -1; side <= 1; side += 2) {
			Vec3 at = calf(p, side);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, count, 0.04, 0.06, 0.04, 0.01 * speed);
			level.sendParticles(EXHAUST, at.x, at.y, at.z, count, 0.06, 0.06, 0.06, 0.0);
		}
	}
}
