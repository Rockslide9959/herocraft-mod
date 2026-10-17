package com.projecthero.mod.miraculous.renarouge;

import java.util.Set;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: Rena Rouge's shared hit code and her orange light. Every hit goes through {@link #strike}: the mod-wide
 * targeting rules ({@link HeroTargets}: never herself, her illusions, a squadmate or a friendly pet; players only with
 * PvP on), bosses take at most {@link RenaRougeConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are
 * never knocked about.
 */
public final class RenaRougeCombat {
	/** Her colour: the orange of the illusions' light. */
	public static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1.0f, 0.5f, 0.12f), 1.3f);
	public static final DustParticleOptions ORANGE_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.58f, 0.18f), 2.0f);
	public static final DustParticleOptions CREAM = new DustParticleOptions(new Vector3f(1.0f, 0.93f, 0.8f), 1.0f);

	private RenaRougeCombat() {
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= RenaRougeConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	/** Rule 1: what an aimed move may hit. */
	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	/** A monster her tricks may fool (lure, confuse, redirect): hostile to her and not a boss. */
	public static boolean isFoolable(ServerPlayer owner, LivingEntity e) {
		return e instanceof Mob && e instanceof Enemy && HeroTargets.canHarm(owner, e) && !isBoss(e);
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin} and {@code lift}. True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		return strike(owner, target, origin, damage, knockback, lift, owner.level().damageSources().playerAttack(owner), true);
	}

	/**
	 * {@link #strike}, with its own damage source (the Illusion Double's hits are its own, so they never end her
	 * camouflage); {@code hers} false leaves Fox Camouflage running.
	 */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift,
			net.minecraft.world.damagesource.DamageSource source, boolean hers) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = damage;
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * RenaRougeConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		boolean landed = AbilityHelpers.hurtBurst(owner, target, source, Math.max(1f, dmg));
		if (!landed) {
			return false;
		}
		if (hers) {
			RenaRouge.breakCamouflage(owner);
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
		ServerLevel level = (ServerLevel) owner.level();
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.2);
		level.sendParticles(ORANGE, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	/** Everything she may hit within {@code radius} of {@code center} (once each, through {@code hit}). */
	public static int radial(ServerPlayer owner, Vec3 center, double radius, float damage, double knockback, double lift, Set<Integer> hit) {
		AABB box = new AABB(center, center).inflate(radius, Math.min(radius, 3.0), radius);
		int n = 0;
		for (LivingEntity e : ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(owner, e))) {
			if (hit != null && hit.contains(e.getId())) {
				continue;
			}
			if (AbilityHelpers.distanceSqToBox(e, center) > radius * radius) {
				continue;
			}
			if (strike(owner, e, center, damage, knockback, lift)) {
				n++;
				if (hit != null) {
					hit.add(e.getId());
				}
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- the orange light

	/** A puff of orange light (an illusion conjured, a fox dash...). */
	public static void glow(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(ORANGE, c.x, c.y, c.z, count, spread, spread * 1.4, spread, 0.0);
		level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, Math.max(1, count / 3), spread, spread * 1.4, spread, 0.6);
	}

	/** An illusion dissolving: orange sparkles and motes of light lifting off a body-sized column. */
	public static void dissolveBurst(ServerLevel level, Vec3 feet, float height) {
		Vec3 c = feet.add(0, height * 0.5, 0);
		level.sendParticles(ORANGE_BIG, c.x, c.y, c.z, 26, 0.3, height * 0.35, 0.3, 0.0);
		level.sendParticles(ORANGE, c.x, c.y, c.z, 18, 0.35, height * 0.4, 0.35, 0.02);
		level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 14, 0.3, height * 0.4, 0.3, 1.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 8, 0.25, height * 0.35, 0.25, 0.04);
	}
}
