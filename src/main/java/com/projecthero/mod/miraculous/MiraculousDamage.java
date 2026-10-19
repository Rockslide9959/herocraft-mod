package com.projecthero.mod.miraculous;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.6: the shared passives of every Miraculous suit, as an {@code ALLOW_DAMAGE} veto: v0.16.7 (user) half fall
 * damage while transformed (none at all for the landing after a movement move), and the holder's kit gets {@link KwamiKit#modifyIncomingDamage} for every other hit (0 cancels it, a
 * changed amount cancels and re-issues it behind a re-entrancy guard, the pattern every power here uses). Damage that
 * bypasses invulnerability ({@code /kill}, the void) is never touched.
 */
public final class MiraculousDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private MiraculousDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MiraculousDamage::allowDamage);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer player) || !Miraculous.transformed(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			// v0.16.7 (user): a landing after a movement move (dash, vault, leap, swing...) is free; any other fall hurts half
			if (Miraculous.hasSafeLanding(player)) {
				Miraculous.clearSafeLanding(player);
				player.resetFallDistance();
				return false;
			}
			float half = amount * MiraculousConfig.FALL_DAMAGE_FACTOR;
			if (half > 0.01f) {
				reissue(player, source, half);
			}
			return false;
		}
		float changed = MiraculousKits.of(Miraculous.kwami(player)).modifyIncomingDamage(player, source, amount);
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
			// Player#hurt scales a mob's hit by the difficulty BEFORE this veto sees it, and would scale the re-issued
			// amount again: hand it the pre-scaling amount so it lands once
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
