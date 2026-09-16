package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Ring Flight (X) / Boost (Shift+X). Built on vanilla {@code mayfly}/{@code flying} with a per-tick
 * velocity model, cloned from {@code MaxSteelFlight}: movement keys steer, jump ascends, sneak
 * descends, sprint boosts. Cruise drains 12/sec (5/sec hovering), Boost drains 40/sec.
 *
 * <p><b>Cleanup contract</b>: {@link #forceStop} is the one place the {@code mayfly} grant is revoked,
 * called from every lifecycle path (death/respawn/disconnect/dimension change/power loss/energy
 * depletion) -- but deliberately NOT from suit-down, since flight (like every ring power) works
 * unsuited and suiting down mid-flight must not interrupt it.
 */
public final class GreenLanternFlight {
	private GreenLanternFlight() {
	}

	public static boolean isFlying(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
	}

	public static void onEnter(ServerPlayer player) {
		player.setAttached(ModAttachments.GREEN_LANTERN_FLYING, true);
		if (!player.getAbilities().instabuild) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 1.7f);
	}

	/**
	 * Stop Ring Flight if it is running. {@code emergencyDescent} grants a brief controlled-descent
	 * velocity-arrest instead of an instant fall, used when charge hits 0 mid-flight (Phase 9's
	 * "emergency depletion" rule).
	 */
	public static void forceStop(ServerPlayer player, boolean emergencyDescent) {
		boolean wasFlying = isFlying(player);
		if (wasFlying) {
			player.setAttached(ModAttachments.GREEN_LANTERN_FLYING, false);
			player.setAttached(ModAttachments.GREEN_LANTERN_BOOSTING, false);
		}
		if (player.getAbilities().getFlyingSpeed() != 0.05f) {
			player.getAbilities().setFlyingSpeed(0.05f);
			player.onUpdateAbilities();
		}
		if (player.getAbilities().mayfly && !player.getAbilities().instabuild && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
		if (wasFlying && emergencyDescent) {
			// Arrest most of the fall speed and cap the descent rather than dropping the player outright;
			// gravity still applies afterwards, this just prevents an instant plummet from height.
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * 0.3, Math.max(v.y, -0.3), v.z * 0.3);
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 0.8f);
		}
	}

	/**
	 * Per-tick while Ring Flight is the active mode. Returns the charge drained this tick and reports
	 * distance flown for Mastery III tracking.
	 */
	public static float tick(ServerPlayer player, boolean boosting) {
		if (!isFlying(player)) {
			return 0f;
		}
		player.getAbilities().flying = true;
		double topSpeedBps = boosting ? GreenLanternConfig.BOOST_SPEED_BPS : GreenLanternConfig.FLIGHT_CRUISE_SPEED_BPS;
		float fly = (float) (topSpeedBps / 20.0 / 2.0); // vanilla doubles flying speed while sprinting
		if (player.getAbilities().getFlyingSpeed() != fly) {
			player.getAbilities().setFlyingSpeed(fly);
			player.onUpdateAbilities();
		}
		player.setAttached(ModAttachments.GREEN_LANTERN_BOOSTING, boosting);
		player.resetFallDistance();

		double speed = player.getDeltaMovement().horizontalDistance() + Math.abs(player.getDeltaMovement().y);
		com.projecthero.mod.greenlantern.GreenLanternMastery.onFlightDistance(player, speed);

		if (player.tickCount % 2 == 0 && speed > 0.02) {
			ServerLevel level = player.serverLevel();
			Vec3 back = player.getLookAngle().scale(-0.4);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					player.getX() + back.x, player.getY() + 0.3, player.getZ() + back.z, 2, 0.15, 0.15, 0.15, 0.01);
			level.sendParticles(ParticleTypes.END_ROD,
					player.getX() + back.x, player.getY() + 0.3, player.getZ() + back.z, 1, 0.1, 0.1, 0.1, 0.02);
		}

		if (boosting) {
			return GreenLanternConfig.BOOST_COST_PER_SEC / 20f;
		}
		float perSec = speed > 0.06 ? GreenLanternConfig.FLIGHT_CRUISE_COST_PER_SEC : GreenLanternConfig.FLIGHT_HOVER_COST_PER_SEC;
		return perSec / 20f;
	}

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.isSpectator()
				|| player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| com.projecthero.mod.spider.SpiderSwing.isSwinging(player);
	}
}
