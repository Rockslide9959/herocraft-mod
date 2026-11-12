package com.projecthero.mod.spin;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 the Spin: his hits, both ways.
 * <ul>
 *   <li><b>Taken</b>: 15% off every hit; no fall damage after a Spin Leap until he lands.</li>
 *   <li><b>Dealt</b>: a hit he lands while disguised makes witnesses ({@link SpinDisguise#onStrike}). The riding bonus is in
 *   {@link Spin#hit} (Spin moves only).</li>
 * </ul>
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (as the Saiyan's).
 */
public final class SpinDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SpinDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpinDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, dealt, blocked) -> {
			if (dealt > 0f && source.getEntity() instanceof ServerPlayer p && p != entity && Spin.hasPower(p)) {
				SpinDisguise.onStrike(p, entity);
			}
		});
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		// Ball Breaker's aging: the Wither effect is only the look; SpinMarks' bites (scaled with the strike) are the damage
		if (source.is(DamageTypes.WITHER) && !SpinMarks.BITING.get() && SpinMarks.aged(entity)) {
			return false;
		}
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim) || !Spin.hasPower(victim)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) && Spin.state(victim).leapUntil > victim.level().getGameTime()) {
			victim.resetFallDistance();
			return false;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		reissue(victim, source, amount * (1f - SpinConfig.DAMAGE_RESIST));
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
