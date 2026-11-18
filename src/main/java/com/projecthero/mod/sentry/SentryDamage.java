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
 *   <li>A heavy hit (8+ after the cut) feeds the Void: 0.4 a point, 6 at most a hit.</li>
 *   <li>Every kill feeds the Void (2, a player 6); in the takeover a kill is absorbed instead (it heals the body).</li>
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
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer p && damageTaken >= SentryConfig.HEAVY_HIT && Sentry.hasPower(p) && !Sentry.inTakeover(p)) {
				Sentry.addVoid(p, Math.min(SentryConfig.HEAVY_HIT_VOID_CAP, damageTaken * SentryConfig.HEAVY_HIT_VOID_PER_DAMAGE));
			}
		});
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
		return f;
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim)) {
			return true;
		}
		if (Sentry.hasPower(victim)) {
			SentryMoves.deflect(victim, source); // moveset 2: melee into the Molecular Shield comes back
		}
		float f = factor(victim, source);
		if (f == 1f) {
			return true;
		}
		if (f <= 0f) {
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
			} else {
				Sentry.addVoid(killer, entity instanceof Player ? SentryConfig.PLAYER_KILL_VOID : SentryConfig.KILL_VOID);
			}
		}
	}
}
