package com.projecthero.mod.vision;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 Vision: his hits, both ways.
 * <ul>
 *   <li><b>Taken</b>: 30% off every hit, another 50% in Diamond Density (65% in all); no fall, drowning, suffocation or wither damage
 *   (poison is cleared every tick); <b>nothing at all</b> lands while he is phased (Intangibility, a Phase Dash, the Density Dive's
 *   rush through a target) except {@code /kill} and the void.</li>
 *   <li><b>Dealt</b>: while intangible he can't hurt anything (his fists and any damage he'd cause pass through).</li>
 * </ul>
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (as Starfire's).
 */
public final class VisionDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private VisionDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(VisionDamage::allowDamage);
	}

	/** Damage that never touches him. Public for the tests. */
	public static boolean immune(ServerPlayer victim, DamageSource source) {
		return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL)
				|| source.is(DamageTypes.WITHER);
	}

	/** What's left of {@code amount} after his resistances. Public for the tests. */
	public static float reduced(ServerPlayer victim, float amount) {
		float k = 1f - VisionConfig.DAMAGE_RESIST;
		if (Vision.dense(victim)) {
			k *= 1f - VisionConfig.DENSITY_RESIST;
		}
		return amount * k;
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f) {
			return true;
		}
		boolean lethalAdmin = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		// an intangible Vision can't hurt anything
		if (!lethalAdmin && source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Vision.intangible(attacker)) {
			return false;
		}
		if (!(entity instanceof ServerPlayer victim) || !Vision.hasPower(victim)) {
			return true;
		}
		if (lethalAdmin) {
			return true;
		}
		if (immune(victim, source)) {
			if (source.is(DamageTypeTags.IS_FALL)) {
				victim.resetFallDistance();
			}
			return false;
		}
		if (Vision.phased(victim)) {
			return false;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		reissue(victim, source, reduced(victim, amount));
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
