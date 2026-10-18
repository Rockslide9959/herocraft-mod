package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7: the Explosion Quirk's server init (called from {@code QuirkMod.initialize}): the kit, and two damage hooks of
 * its own -- {@code ALLOW_DAMAGE} cancels the fall / wall damage of his own launches and fires the Explosive Counter
 * (the same check as {@link ExplosionKit#modifyIncomingDamage}, one-shot, so it can't fire twice whichever runs first),
 * {@code AFTER_DAMAGE} makes him sweat in a fight (hits he lands and takes).
 */
public final class ExplosionMod {
	private ExplosionMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.EXPLOSION, new ExplosionKit());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && Explosion.is(p) && ExplosionMoves.cancelsDamage(p, source)) {
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (dealtAmount <= 0f) {
				return;
			}
			if (entity instanceof ServerPlayer victim && Explosion.is(victim)) {
				ExplosionMoves.markCombat(victim, ExplosionConfig.SWEAT_HIT_TAKEN, false);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Explosion.is(attacker)) {
				ExplosionMoves.markCombat(attacker, ExplosionConfig.SWEAT_HIT_DEALT, true);
			}
		});
	}
}
