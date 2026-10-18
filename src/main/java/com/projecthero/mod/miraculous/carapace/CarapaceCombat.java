package com.projecthero.mod.miraculous.carapace;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: Carapace's shared hit code and his green light. Every hit goes through {@link #strike}: the mod-wide
 * targeting rules ({@link HeroTargets}: never himself, a squadmate or a friendly pet; players only with PvP on); bosses
 * take at most {@link CarapaceConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked about.
 */
public final class CarapaceCombat {
	/** The green of his energy shell. */
	public static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 0.95f, 0.45f), 1.3f);
	public static final DustParticleOptions GREEN_BIG = new DustParticleOptions(new Vector3f(0.4f, 1.0f, 0.5f), 2.0f);
	public static final DustParticleOptions SHELL = new DustParticleOptions(new Vector3f(0.16f, 0.5f, 0.24f), 1.4f);

	private CarapaceCombat() {
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= CarapaceConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
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
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * CarapaceConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
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
		ServerLevel level = (ServerLevel) owner.level();
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.2);
		level.sendParticles(GREEN, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	/** A stun (bosses: only a short heavy slow, as the other kits do). */
	public static void stun(LivingEntity e, int ticks) {
		if (isBoss(e)) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
		} else {
			PunisherControl.stun(e, ticks);
		}
	}

	/** A puff of green shell light. */
	public static void glow(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(GREEN, c.x, c.y, c.z, count, spread, spread * 1.4, spread, 0.0);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, Math.max(1, count / 3), spread, spread * 1.4, spread, 0.0);
	}
}
