package com.projecthero.mod.quirk;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.7: the holder's {@link QuirkKit#modifyIncomingDamage} as an {@code ALLOW_DAMAGE} veto (Shadow Guard / Counter,
 * the cushioned landings...): 0 cancels the hit, a changed amount cancels and re-issues it behind a re-entrancy guard
 * (the {@code MiraculousDamage} pattern). Erased holders get no help from their kit. Damage that bypasses invulnerability
 * ({@code /kill}, the void) is never touched.
 */
public final class QuirkDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private QuirkDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(QuirkDamage::allowDamage);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer player)) {
			return true;
		}
		Quirk q = Quirks.quirk(player);
		if (q == null || com.projecthero.mod.hero.PowerSuppression.isSuppressed(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		float changed = QuirkKits.of(q).modifyIncomingDamage(player, source, amount);
		if (changed == amount) {
			return true;
		}
		if (changed > 0.01f) {
			reissue(player, source, changed);
		}
		return false;
	}

	private static void reissue(ServerPlayer player, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			// Player#hurt scales a mob's hit by the difficulty BEFORE this veto sees it and would scale it again
			amount = switch (player.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
