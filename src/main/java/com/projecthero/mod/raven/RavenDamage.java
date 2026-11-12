package com.projecthero.mod.raven;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 Raven: the hits she takes. Nothing touches her while she is a streak of shadow (Shadow Dash, sunk for Shadow Step); the Dark
 * Shield soaks hits first (40 per raise); what gets through is 10% lighter. Every hit that lands pushes her Composure toward Rage.
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (as the Spin's).
 */
public final class RavenDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private RavenDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RavenDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, dealt, blocked) -> {
			if (dealt > 0f && entity instanceof ServerPlayer p && Raven.hasPower(p)) {
				Raven.onHurt(p, dealt);
			}
		});
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim) || !Raven.hasPower(victim)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (Raven.inShadow(victim)) {
			return false;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		float left = amount;
		if (Raven.shielding(victim) && !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_DROWNING)
				&& !source.is(DamageTypes.STARVE)) {
			left = RavenMoves.shieldAbsorb(victim, amount);
			if (left <= 0f) {
				return false;
			}
			reissue(victim, source, left * (1f - RavenConfig.DAMAGE_RESIST), true);
			return false;
		}
		reissue(victim, source, left * (1f - RavenConfig.DAMAGE_RESIST), true);
		return false;
	}

	/** {@code amount} with vanilla's difficulty scaling taken off (what the hit is really worth). */
	private static float scaled(LivingEntity target, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			return switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		return amount;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount, boolean unscale) {
		if (unscale) {
			amount = scaled(target, source, amount);
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
