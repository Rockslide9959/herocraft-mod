package com.projecthero.mod.speedforce;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * What the Speed Force does to damage (v0.17.1).
 * <ul>
 *   <li>Phasing: nothing lands on the runner, and they can't hurt anything.</li>
 *   <li>No fall damage above 25% momentum (or while running up a wall).</li>
 *   <li>Rescue Run: whatever is carried (and for 3 s after it is set down) takes no fall or suffocation damage, and
 *       can't hurt the one carrying it.</li>
 *   <li>A hit costs momentum; lightning fills the Speed Force (and is one of the awakening's two surges).</li>
 * </ul>
 */
public final class SpeedForceDamage {
	private SpeedForceDamage() {
	}

	static void clearSessionState() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpeedForceDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(entity instanceof ServerPlayer p) || !p.isAlive()) {
				return;
			}
			if (source.is(DamageTypes.LIGHTNING_BOLT)) {
				SpeedForceUpgrade.onLightning(p);
				return;
			}
			if (SpeedForceSpeedster.hasPower(p) && taken >= SpeedForceConfig.HIT_MIN_DAMAGE && !source.is(DamageTypeTags.IS_FALL)
					&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				SpeedForceSpeedster.loseMomentum(p, SpeedForceConfig.HIT_LOSS);
			}
		});
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (amount <= 0f) {
			return true;
		}
		boolean bypass = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		// a phasing runner deals nothing
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && SpeedForceSpeedster.phasing(attacker)) {
			return false;
		}
		// Rescue Run: the carried can't be hurt by the fall / walls, and can't hurt the carrier
		if ((source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FLY_INTO_WALL))
				&& SpeedForceAbilities.rescued(entity)) {
			entity.resetFallDistance();
			return false;
		}
		if (entity instanceof ServerPlayer carrier && SpeedForceSpeedster.hasPower(carrier) && source.getEntity() != null
				&& source.getEntity().getVehicle() == carrier && SpeedForceSpeedster.state(carrier).carryId == source.getEntity().getId()) {
			return false;
		}
		if (!(entity instanceof ServerPlayer player) || !SpeedForceSpeedster.hasPower(player) || bypass) {
			return true;
		}
		if (SpeedForceSpeedster.phasing(player)) {
			return false;
		}
		if ((source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL))
				&& (SpeedForceSpeedster.state(player).momentum >= SpeedForceConfig.NO_FALL_MOMENTUM
						|| SpeedForceSpeedster.surging(player))) {
			player.resetFallDistance();
			return false;
		}
		return true;
	}
}
