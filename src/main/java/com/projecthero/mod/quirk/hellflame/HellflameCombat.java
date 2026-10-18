package com.projecthero.mod.quirk.hellflame;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Hellflame's shared hit code and its flame colours. Every hit goes through {@link #strike}: the mod-wide
 * targeting rules ({@link HeroTargets#canHarm}: never himself, a squadmate, a friendly pet; players only with PvP on);
 * bosses take at most {@link HellflameConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked
 * about. The damage is a player attack (so it lands on fire-proof Nether mobs too); the burn is the target set alight.
 * No move ever places a fire block.
 */
public final class HellflameCombat {
	/** Endeavor's flames: the white-hot core, the yellow, the orange and the deep red-orange edge. */
	/** (Full-bright: {@link HellflameParticles}.) */
	public static final ParticleOptions WHITE_HOT = HellflameParticles.CORE;
	public static final ParticleOptions YELLOW = HellflameParticles.GLOW;
	public static final ParticleOptions ORANGE = HellflameParticles.BLAZE;
	public static final ParticleOptions BIG_ORANGE = HellflameParticles.BIG;
	/** A deep red ember (world-lit dust: only for the dying sparks). */
	public static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.85f, 0.22f, 0.04f), 1.1f);

	private HellflameCombat() {
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= HellflameConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	/** The damage a hit of {@code damage} really does to {@code target} (the boss cap). */
	public static float capped(LivingEntity target, float damage) {
		return isBoss(target) ? Math.min(damage, target.getMaxHealth() * HellflameConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
	}

	/**
	 * One hit: damage (boss-capped), set alight for {@code burnSeconds}, knockback away from {@code origin} and
	 * {@code lift} (never on a boss). {@code burst}: a one-off hit that ignores the hit-invulnerability window (not for
	 * per-tick damage). True if it landed.
	 */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, int burnSeconds, double knockback, double lift,
			boolean burst) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = Math.max(1f, capped(target, damage));
		var src = owner.level().damageSources().playerAttack(owner);
		boolean landed = burst ? AbilityHelpers.hurtBurst(owner, target, src, dmg) : AbilityHelpers.hurt(owner, target, src, dmg);
		if (!landed) {
			return false;
		}
		if (burnSeconds > 0 && target.isAlive()) {
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
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
		ServerLevel level = owner.serverLevel();
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 8, 0.25, 0.35, 0.25, 0.04);
		level.sendParticles(ORANGE, c.x, c.y, c.z, 5, 0.3, 0.35, 0.3, 0.0);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 1, 0.2, 0.2, 0.2, 0.0);
		return true;
	}

	/** Every valid target in {@code radius} of {@code c} (to the nearest point of its box). */
	public static List<LivingEntity> around(ServerPlayer owner, Vec3 c, double radius) {
		return AbilityHelpers.enemiesAround(owner, c, radius);
	}

	/** Every valid target within {@code radius} of the segment a-b not yet in {@code hit} (added to it). */
	public static List<LivingEntity> alongPath(ServerPlayer owner, Vec3 a, Vec3 b, double radius, @Nullable Set<Integer> hit) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(a, b).inflate(radius + 1.0);
		Vec3 ab = b.subtract(a);
		double len2 = Math.max(1.0e-6, ab.lengthSqr());
		for (LivingEntity e : owner.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && isTarget(owner, e))) {
			if (hit != null && hit.contains(e.getId())) {
				continue;
			}
			AABB eb = e.getBoundingBox().inflate(radius);
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = Math.max(0.0, Math.min(1.0, c.subtract(a).dot(ab) / len2));
			Vec3 near = a.add(ab.scale(t));
			if (!eb.contains(near)) {
				continue;
			}
			if (hit != null) {
				hit.add(e.getId());
			}
			out.add(e);
		}
		return out;
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

	/** A puff of flame: white-hot core, yellow and orange around it, a few embers. */
	public static void flare(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(WHITE_HOT, c.x, c.y, c.z, Math.max(1, count / 3), spread * 0.4, spread * 0.4, spread * 0.4, 0.0);
		level.sendParticles(YELLOW, c.x, c.y, c.z, Math.max(1, count / 2), spread * 0.7, spread * 0.7, spread * 0.7, 0.0);
		level.sendParticles(ORANGE, c.x, c.y, c.z, count, spread, spread, spread, 0.0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, count, spread, spread, spread, 0.03);
		level.sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, Math.max(1, count / 2), spread, spread, spread, 0.02);
	}

	/** A flame streak a-b, {@code perBlock} puffs a block. */
	public static void streak(ServerLevel level, Vec3 a, Vec3 b, double perBlock, boolean core) {
		double length = a.distanceTo(b);
		int steps = Math.max(1, (int) (length * perBlock));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.lerp(b, (double) i / steps);
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.005);
			if (core && (i & 1) == 0) {
				level.sendParticles(WHITE_HOT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			} else if ((i % 3) == 0) {
				level.sendParticles(ORANGE, p.x, p.y, p.z, 1, 0.06, 0.06, 0.06, 0.0);
			}
		}
	}
}
