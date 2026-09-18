package com.projecthero.mod.greenlantern;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Green Lantern's incoming-damage handling: Directional Shield/Protective Dome absorption (the
 * cancel-and-reapply-smaller pattern every Hero-Tier power in this mod uses, since Fabric's
 * {@code ALLOW_DAMAGE} is a boolean veto with no "reduce amount"), Ring Charge fall-damage immunity,
 * and battery-channel interruption on any hit taken.
 */
public final class GreenLanternDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private GreenLanternDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GreenLanternDamage::onAllowDamage);
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || !GreenLantern.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return true;
		}

		GreenLanternBattery.onDamaged(player);

		// v0.11.6: explicit user request -- "player cant take fall damage as long as the ring has
		// charge". Free, unconditional and not suit-gated (the ring's powers work unsuited too), unlike
		// the old cost-and-cooldown-gated Emergency Catch this replaces.
		if (source.is(DamageTypeTags.IS_FALL) && GreenLanternEnergy.get(player) > 0f) {
			player.resetFallDistance();
			return false;
		}

		if (!GreenLanternShield.isActive(player)) {
			return true;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_EXPLOSION)) {
			return true;
		}
		// Directional Shield only covers a 120-degree frontal arc; the Dome covers every direction.
		if (!GreenLanternShield.isDome(player) && !inFrontalArc(player, source)) {
			return true;
		}

		REENTRANT.set(true);
		try {
			float overflow = GreenLanternShield.absorb(player, amount);
			if (overflow >= 0.5f) {
				player.hurt(source, overflow);
			}
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}

	private static boolean inFrontalArc(ServerPlayer player, DamageSource source) {
		Vec3 origin = sourcePosition(source);
		if (origin == null) {
			return false;
		}
		Vec3 toSource = origin.subtract(player.position()).normalize();
		Vec3 look = player.getLookAngle();
		double dot = toSource.dot(look);
		// 120 degrees total = 60 degrees each side of centre; cos(60deg) = 0.5.
		return dot >= 0.5;
	}

	private static Vec3 sourcePosition(DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (direct != null) {
			return direct.position();
		}
		return source.getSourcePosition();
	}
}
