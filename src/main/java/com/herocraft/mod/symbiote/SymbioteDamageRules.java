package com.herocraft.mod.symbiote;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Symbiote's damage-side weaknesses and defences: fire/lava vulnerability, a small passive
 * explosion resistance, and Symbiote Shield's active reduction. One
 * {@link ServerLivingEntityEvents#ALLOW_DAMAGE} listener, following the exact "cancel the original,
 * re-apply a scaled amount behind a re-entrancy guard" pattern
 * {@code com.herocraft.mod.hero.power.HeroDamageRules#reduce} already uses (Fabric's ALLOW_DAMAGE is
 * a yes/no veto with no "change the amount" hook).
 *
 * <p>Base fall-damage/knockback resistance is handled separately as plain attribute modifiers
 * ({@link SymbiotePassives}) -- only rules that need to look at the {@link DamageSource} itself live
 * here. Applies to both host variants: the fire weakness and Shield are properties of the Symbiote
 * organism itself, not of which host it is wearing.
 */
public final class SymbioteDamageRules {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	/** Fire/lava deals 40% more while the Symbiote is active, per the spec's fire weakness. */
	private static final float FIRE_MULTIPLIER = 1.4f;
	/** A small passive cut to explosion damage while active -- "reduce... minor explosion damage". */
	private static final float EXPLOSION_FACTOR = 0.8f;
	/** Symbiote Shield: -60% incoming damage while up. */
	private static final float SHIELD_FACTOR = 0.4f;
	/** Frenzy's drawback: +15% incoming damage for a few seconds after it ends. */
	private static final float FRENZY_DEBUFF_VULNERABILITY = 1.15f;

	private SymbioteDamageRules() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SymbioteDamageRules::onAllowDamage);
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || !Symbiote.isActive(player)) {
			return true;
		}
		long now = player.level().getGameTime();

		// Symbiote Leap: the leap's OWN fall damage is negated outright (a short grace window after
		// launch), separate from the passive -30% fall-damage reduction every active host already has.
		if (source.is(DamageTypeTags.IS_FALL) && SymbioteAbilityManager.leapFallProtected(player, now)) {
			player.resetFallDistance();
			return false;
		}

		float factor = 1.0f;
		if (source.is(DamageTypeTags.IS_FIRE)) {
			factor *= FIRE_MULTIPLIER;
		} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			factor *= EXPLOSION_FACTOR;
		}
		if (SymbioteAbilityManager.shieldActive(player)) {
			factor *= SHIELD_FACTOR;
		}
		if (SymbioteAbilityManager.frenzyDebuffActive(player, now)) {
			factor *= FRENZY_DEBUFF_VULNERABILITY;
		}
		if (Math.abs(factor - 1.0f) < 0.001f) {
			return true;
		}
		return reapply(player, source, amount * factor);
	}

	private static boolean reapply(ServerPlayer player, DamageSource source, float amount) {
		if (amount < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}
