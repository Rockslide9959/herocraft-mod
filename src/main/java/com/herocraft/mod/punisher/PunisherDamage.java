package com.herocraft.mod.punisher;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * The one incoming-damage rule the Punisher power itself owns: a brief window of light damage
 * reduction while mid Tactical Roll (spec section 24). Same cancel-and-re-apply-smaller pattern as
 * {@code MaxSteelDamage} / {@code IronManDamage}, since Fabric's {@code ALLOW_DAMAGE} is a boolean
 * veto. The Punisher armour's projectile-damage reduction is a separate rule added in Phase 4.
 */
public final class PunisherDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private PunisherDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(PunisherDamage::onAllowDamage);
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player)) {
			return true;
		}
		boolean rolling = Punisher.rolling(player);
		boolean projectileSet = source.is(DamageTypeTags.IS_PROJECTILE) && PunisherArmorSet.active(player);
		if ((!rolling && !projectileSet) || amount <= 0f) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		float factor = 1f;
		if (rolling) {
			factor *= 1f - PunisherConfig.ROLL_DAMAGE_REDUCTION;
		}
		if (projectileSet) {
			factor *= 1f - PunisherArmorSet.PROJECTILE_REDUCTION;
		}
		float reduced = amount * factor;
		if (reduced < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, reduced);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}
