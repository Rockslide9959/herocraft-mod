package com.projecthero.mod.allmight;

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
import net.minecraft.world.phys.Vec3;

/**
 * All Might's incoming damage rules and his passive punches (v0.12.33).
 *
 * <ul>
 *   <li><b>Transformation:</b> nothing hurts him during the H transformation window (the void and /kill excepted).</li>
 *   <li><b>Fall damage:</b> completely cancelled after his own launches; otherwise reduced 75% (90% full power).</li>
 *   <li><b>Everything else:</b> reduced 35% (50% full power), multiplied by a further 20% during Full Cowl -- so it
 *       compounds (0.5 x 0.8 = 0.4 taken) and never adds up to invulnerability. Melee, projectiles, explosions, fire and
 *       environmental damage are all covered; only {@code BYPASSES_INVULNERABILITY} sources are left alone.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a boolean veto, so a partial reduction cancels the hit and re-applies a smaller one
 * behind a re-entrancy guard (the same pattern as every other power's damage rules).
 */
public final class AllMightDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private AllMightDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(AllMightDamage::onAllowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (entity instanceof ServerPlayer victim && AllMight.hasPower(victim)) {
				AllMight.markCombat(victim);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && AllMight.hasPower(attacker)) {
				onPunch(attacker, entity, source, dealtAmount);
			}
		});
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || amount <= 0f || !AllMight.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		long now = player.level().getGameTime();
		if (AllMight.transforming(player)) {
			return false;
		}
		float factor;
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			if (AllMight.state(player).noFallUntil > now) {
				player.resetFallDistance();
				return false; // his own launches can never hurt him
			}
			factor = 1.0f - AllMight.fallReduction(player);
		} else {
			factor = AllMight.damageTakenFactor(player);
		}
		if (factor >= 0.999f) {
			return true;
		}
		float reduced = amount * factor;
		if (reduced < 0.1f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, reduced);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}

	/** A plain melee hit by All Might: strong knockback, a compressed-air burst and an impact sound. */
	private static void onPunch(ServerPlayer attacker, LivingEntity target, DamageSource source, float dealt) {
		if (AllMightShockwave.isAbilityHit() || dealt <= 0f || !source.is(DamageTypes.PLAYER_ATTACK)
				|| source.getDirectEntity() != attacker) {
			return;
		}
		AllMight.markCombat(attacker);
		boolean boss = AllMightShockwave.isBoss(target);
		if (!boss) {
			double kb = attacker.isSprinting() ? AllMightConfig.SPRINT_PUNCH_KNOCKBACK : AllMightConfig.PUNCH_KNOCKBACK;
			AbilityHelpers.knockbackFrom(target, attacker.position(), kb);
			if (attacker.isSprinting()) {
				AbilityHelpers.push(target, new Vec3(0.0, 0.25, 0.0));
			}
		}
		ServerLevel level = (ServerLevel) attacker.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
		AllMightShockwave.burst(level, ParticleTypes.CRIT, c, attacker.isSprinting() ? 12 : 6, 0.3, 0.25);
		AllMightShockwave.burst(level, ParticleTypes.CLOUD, c, attacker.isSprinting() ? 6 : 3, 0.2, 0.08);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
	}
}
