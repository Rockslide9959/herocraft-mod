package com.projecthero.mod.gyro;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 Gyro Zeppeli: his hits, both ways.
 * <ul>
 *   <li><b>Taken</b>: 15% off every hit; no fall damage after a Spin Leap until he lands.</li>
 *   <li><b>Dealt</b>: a hit he lands while disguised makes witnesses ({@link GyroDisguise#onStrike}). The riding bonus is in
 *   {@link Gyro#hit} (Spin moves only).</li>
 * </ul>
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (as the Saiyan's).
 */
public final class GyroDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private GyroDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GyroDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, dealt, blocked) -> {
			if (dealt > 0f && source.getEntity() instanceof ServerPlayer p && p != entity && Gyro.hasPower(p)) {
				GyroDisguise.onStrike(p, entity);
			}
		});
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim) || !Gyro.hasPower(victim)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) && Gyro.state(victim).leapUntil > victim.level().getGameTime()) {
			victim.resetFallDistance();
			return false;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		reissue(victim, source, amount * (1f - GyroConfig.DAMAGE_RESIST));
		return false;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			amount = switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
