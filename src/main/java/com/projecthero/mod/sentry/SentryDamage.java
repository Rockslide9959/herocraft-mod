package com.projecthero.mod.sentry;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Sentry: hits on him and kills by him.
 * <ul>
 *   <li>70% less damage taken (Void Form and the takeover 75%); no fall damage while flying, diving or in the takeover. Moveset 2:
 *   halved again while A Million Suns charges, and -40% more under the Molecular Shield (which also deflects melee).</li>
 *   <li>Round 3 (user: "only make the players void bar increase when they use their powers"): heavy hits taken and kills no longer feed
 *   the Void. In the takeover a kill is still absorbed (it heals the body). Merged, the Umbral Shroud cuts hits by 40% more and
 *   withers whoever strikes him in melee.</li>
 *   <li>Dying in the takeover ends it (Void 60 on respawn).</li>
 * </ul>
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (Shazam's pattern).
 */
public final class SentryDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SentryDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SentryDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(SentryDamage::afterDeath);
	}

	/** The multiplier on a hit the victim would take ({@code 0} = cancelled). For the tests too. */
	public static float factor(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer victim) || !Sentry.hasPower(victim)) {
			return 1f;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1f;
		}
		// v0.17.5 (user: "Make it so that sentry cant be set on fire please"): fire, lava and magma never hurt him (the flames are
		// put out in allowDamage) -- the same rule as the Kryptonian
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)) {
			return 0f;
		}
		if (source.is(DamageTypeTags.IS_FALL) && (Sentry.isFlying(victim) || SentryMoves.diving(victim) || Sentry.inTakeover(victim))) {
			return 0f;
		}
		float f = 1f - (Sentry.voidForm(victim) ? SentryConfig.VOID_RESIST : SentryConfig.SENTRY_RESIST);
		if (SentryMoves.chargingSuns(victim)) {
			f *= 1f - SentryConfig.SUNS_CHARGE_RESIST; // moveset 2: the light gathering around him
		}
		if (SentryMoves.shielded(victim)) {
			f *= 1f - SentryConfig.MSHIELD_RESIST; // moveset 2: the Molecular Shield
		}
		if (VoidMoves.shrouded(victim)) {
			f *= 1f - SentryConfig.SHROUD_RESIST; // round 3: the Umbral Shroud
		}
		return f;
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim)) {
			return true;
		}
		if (Sentry.hasPower(victim)) {
			SentryMoves.deflect(victim, source); // moveset 2: melee into the Molecular Shield comes back
			VoidMoves.retaliate(victim, source); // round 3: melee into the Umbral Shroud withers the attacker
		}
		float f = factor(victim, source);
		if (f == 1f) {
			return true;
		}
		if (f <= 0f) {
			if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)) {
				victim.clearFire();
			}
			if (source.is(DamageTypeTags.IS_FALL)) {
				victim.resetFallDistance();
			}
			return false;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		float amountOut = amount * f;
		if (source.scalesWithDifficulty()) {
			amountOut = switch (victim.level().getDifficulty()) {
				case HARD -> amountOut / 1.5f;
				case EASY -> amountOut >= 2f ? 2f * (amountOut - 1f) : amountOut;
				default -> amountOut;
			};
		}
		REENTRANT.set(true);
		try {
			victim.hurt(source, amountOut);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer dead && Sentry.inTakeover(dead)) {
			VoidTakeover.end(dead, VoidTakeover.End.DIED);
		}
		if (source.getEntity() instanceof ServerPlayer killer && killer != entity && Sentry.hasPower(killer)) {
			if (Sentry.inTakeover(killer)) {
				VoidTakeover.absorb(killer, entity);
			}
		}
	}
}
