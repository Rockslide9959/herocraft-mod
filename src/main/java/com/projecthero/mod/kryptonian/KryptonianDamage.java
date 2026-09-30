package com.projecthero.mod.kryptonian;

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
 * What hurts a Kryptonian (v0.14.8), and his passive punches.
 *
 * <ul>
 *   <li>At full strength: falls, fire, lava, drowning, suffocation-free flying into walls and freezing never get through
 *       at all; everything else is cut by {@link KryptonianConfig#DAMAGE_REDUCTION} (75%).</li>
 *   <li>Near kryptonite (or burnt out by a Solar Flare) none of that applies -- he takes everything in full.</li>
 *   <li>{@code /kill} and the void always go through.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a veto, so a reduction cancels the hit and re-issues a smaller one behind a
 * re-entrancy guard (the pattern every power here uses).
 */
public final class KryptonianDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private KryptonianDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(KryptonianDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Kryptonian.empowered(attacker)) {
				onPunch(attacker, entity, source, taken);
			}
		});
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || amount <= 0.0f || !Kryptonian.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (!Kryptonian.empowered(player)) {
			return true; // kryptonite / burnt out: an ordinary human
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			player.resetFallDistance();
			return false;
		}
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)) {
			player.clearFire();
			return false;
		}
		if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypes.IN_WALL)
				|| source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.STARVE)) {
			return false;
		}
		float reduced = amount * Kryptonian.damageTakenFactor(player);
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

	/** A plain melee hit: heavy knockback (more when sprinting) and a small burst. */
	private static void onPunch(ServerPlayer attacker, LivingEntity target, DamageSource source, float dealt) {
		if (KryptonianCombat.isAbilityHit() || dealt <= 0f || !source.is(DamageTypes.PLAYER_ATTACK) || source.getDirectEntity() != attacker) {
			return;
		}
		if (!KryptonianCombat.isBoss(target)) {
			double kb = attacker.isSprinting() ? KryptonianConfig.SPRINT_PUNCH_KNOCKBACK : KryptonianConfig.PUNCH_KNOCKBACK;
			AbilityHelpers.knockbackFrom(target, attacker.position(), kb);
			if (attacker.isSprinting()) {
				AbilityHelpers.push(target, new Vec3(0.0, 0.3, 0.0));
			}
		}
		ServerLevel level = (ServerLevel) attacker.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.25);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.08);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
	}
}
