package com.projecthero.mod.supersoldier;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Super Soldier's damage rules (v0.14.8).
 *
 * <ul>
 *   <li><b>Taken:</b> 30% less from everything but the void, /kill and other invulnerability-bypassing sources. The
 *       Tactical Roll's frames make him untouchable; his own leaps never cause fall damage.</li>
 *   <li><b>Dealt, Tactical Focus:</b> his punches on a marked enemy are critical (x1.5).</li>
 *   <li><b>Dealt, Onslaught:</b> every punch shocks the enemies around the target.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a boolean veto, so a changed amount cancels the hit and re-applies the new one behind a
 * re-entrancy guard (the same pattern as every other power's damage rules). The rejection death of the unrefined serum
 * uses its own {@link #SERUM_REJECTION} damage type (tagged to bypass invulnerability, armour and effects).
 */
public final class SuperSoldierDamage {
	public static final ResourceKey<DamageType> SERUM_REJECTION = ResourceKey.create(Registries.DAMAGE_TYPE,
			ProjectHeroMod.id("serum_rejection"));

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SuperSoldierDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SuperSoldierDamage::onAllowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (entity instanceof ServerPlayer victim && SuperSoldier.hasPower(victim)) {
				SuperSoldier.markCombat(victim);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && SuperSoldier.hasPower(attacker)) {
				SuperSoldier.markCombat(attacker);
				if (dealtAmount > 0f && isPunch(attacker, source) && SuperSoldier.onslaughtActive(attacker)) {
					SuperSoldierAbilities.onslaughtShock(attacker, entity);
				}
			}
		});
	}

	public static DamageSource serumRejection(ServerLevel level) {
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SERUM_REJECTION));
	}

	/** A plain melee hit by {@code attacker} (not one of his moves). */
	private static boolean isPunch(ServerPlayer attacker, DamageSource source) {
		return !SuperSoldierAbilities.isAbilityHit() && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker;
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f) {
			return true;
		}
		float factor = 1.0f;
		// outgoing: Tactical Focus crits on a marked enemy
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && SuperSoldier.hasPower(attacker)
				&& isPunch(attacker, source) && SuperSoldierAbilities.isMarked(attacker, entity)) {
			factor *= SuperSoldierConfig.FOCUS_CRIT_MULTIPLIER;
			ServerLevel level = (ServerLevel) attacker.level();
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
					10, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.2f);
		}
		// incoming
		if (entity instanceof ServerPlayer player && SuperSoldier.hasPower(player)
				&& !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			SuperSoldierState s = SuperSoldier.state(player);
			long now = player.level().getGameTime();
			if (s.iframeUntil > now) {
				return false; // the roll's frames
			}
			if ((source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL))
					&& (s.noFallUntil > now || SuperSoldierAbilities.slamming(player))) {
				player.resetFallDistance();
				return false;
			}
			factor *= SuperSoldierConfig.DAMAGE_TAKEN_FACTOR;
		}
		if (Math.abs(factor - 1.0f) < 1.0e-3f) {
			return true;
		}
		float changed = amount * factor;
		if (changed < 0.05f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			entity.hurt(source, changed);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}
