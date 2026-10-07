package com.projecthero.mod.nova;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * What hurts Nova and what his Worldmind mark does (v0.15.13).
 *
 * <ul>
 *   <li>While suited: no fall damage (nor flying into a wall); while the Force Shield is up, every projectile and every
 *       melee blow is absorbed outright (the attacker is pushed back); everything else is cut by
 *       {@link NovaConfig#DAMAGE_REDUCTION} (60%). {@code /kill} and the void always go through.</li>
 *   <li>The creature the Worldmind Scan marked takes +25% from every source while the mark lasts.</li>
 *   <li>A creature carried up by Orbital Launch or held in a Gravity Lock does not suffocate in the blocks it is dragged
 *       through.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a veto, so a change cancels the hit and re-issues the new amount behind a re-entrancy
 * guard (the pattern every power here uses).
 */
public final class NovaDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private NovaDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(NovaDamage::allowDamage);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0.0f) {
			return true;
		}
		if (source.is(DamageTypes.IN_WALL) && (NovaAbilities.isLocked(entity) || isLaunchVictim(entity))) {
			return false;
		}
		boolean bypass = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!(entity instanceof ServerPlayer player) || !Nova.suited(player)) {
			// the Worldmind mark: +25%
			float mark = NovaAbilities.markMultiplier(entity);
			if (mark > 1.0f && !bypass) {
				reissue(entity, source, amount * mark);
				return false;
			}
			return true;
		}
		if (bypass) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			player.resetFallDistance();
			return false;
		}
		if (Nova.shieldUp(player) && (isProjectile(source) || isMelee(source))) {
			absorb(player, source);
			return false;
		}
		float reduced = amount * Nova.damageTakenFactor(player);
		if (reduced < 0.05f) {
			return false;
		}
		reissue(player, source, reduced);
		return false;
	}

	private static void reissue(LivingEntity entity, DamageSource source, float amount) {
		REENTRANT.set(true);
		try {
			entity.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}

	private static boolean isProjectile(DamageSource source) {
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
	}

	/** A blow struck in person: the attacker is the thing that hit (not an arrow, not an explosion, not magic). */
	private static boolean isMelee(DamageSource source) {
		return source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
				&& !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FIRE)
				&& !source.is(DamageTypes.MAGIC) && !source.is(DamageTypes.INDIRECT_MAGIC);
	}

	private static void absorb(ServerPlayer player, DamageSource source) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		if (source.getDirectEntity() != null) {
			Vec3 from = source.getDirectEntity().position().add(0, source.getDirectEntity().getBbHeight() * 0.5, 0);
			Vec3 at = c.add(from.subtract(c).normalize().scale(NovaConfig.SHIELD_RADIUS));
			level.sendParticles(Nova.GOLD_BIG, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.0);
			level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.4f);
		if (isMelee(source) && source.getEntity() instanceof LivingEntity attacker && !NovaCombat.isBoss(attacker)) {
			AbilityHelpers.knockbackFrom(attacker, player.position(), 0.9);
		}
	}

	private static boolean isLaunchVictim(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return false;
		}
		for (ServerPlayer p : level.players()) {
			if (NovaAbilities.launchVictim(p) == e) {
				return true;
			}
		}
		return false;
	}
}
