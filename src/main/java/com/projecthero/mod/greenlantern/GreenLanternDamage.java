package com.projecthero.mod.greenlantern;

import com.projecthero.mod.greenlantern.data.GreenLanternState;

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
 * {@code ALLOW_DAMAGE} is a boolean veto with no "reduce amount"), Emergency Catch fall protection,
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

		// Emergency Catch: a suited-passive. A dangerous fall consumes 100 emergency energy on a 4s
		// internal cooldown.
		if (GreenLantern.isSuited(player) && source.is(DamageTypeTags.IS_FALL) && amount >= 5.0f) {
			if (tryEmergencyCatch(player)) {
				return false;
			}
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
			GreenLanternShield.absorb(player, amount);
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

	private static boolean tryEmergencyCatch(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, "emergency_catch")) {
			return false;
		}
		GreenLanternState s = GreenLantern.state(player);
		if (s.ringCharge < GreenLanternConfig.EMERGENCY_CATCH_COST) {
			return false;
		}
		if (!GreenLanternEnergy.spendEmergency(player, GreenLanternConfig.EMERGENCY_CATCH_COST)) {
			return false;
		}
		GreenLantern.triggerCooldown(player, "emergency_catch", GreenLanternConfig.EMERGENCY_CATCH_COOLDOWN_TICKS);
		player.resetFallDistance();
		player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.5f);
		return true;
	}
}
