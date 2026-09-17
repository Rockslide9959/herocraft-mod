package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Ring Flight (double-tap Space) / Boost (Shift+Sprint while flying). Built on vanilla
 * {@code mayfly}/{@code flying} with a per-tick velocity model, cloned from {@code MaxSteelFlight}:
 * movement keys steer, jump ascends, sneak descends, sprint boosts. v0.11.5: a flat 1 energy/sec
 * whether hovering or cruising (was a 12/5 split); Boost still drains 40/sec plus its own trail cost.
 * The flight toggle itself moved off the X ability slot to a double-tap of the vanilla jump key (see
 * {@code ProjectHeroModClient#handleDoubleJump} and {@code GreenLanternActionPayload}) -- X now fires
 * Ring Grapple instead (see {@code GreenLanternGrapple}).
 *
 * <p><b>Cleanup contract</b>: {@link #forceStop} is the one place the {@code mayfly} grant is revoked,
 * called from every lifecycle path (death/respawn/disconnect/dimension change/power loss/energy
 * depletion) -- but deliberately NOT from suit-down, since flight (like every ring power) works
 * unsuited and suiting down mid-flight must not interrupt it.
 */
public final class GreenLanternFlight {
	/** Lantern-Corps green, matching {@code GreenLanternCombat}'s dust colour -- the sprint-flying trail. */
	private static final ParticleOptions TRAIL_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.4f);

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

	/** Per-tick while Ring Flight is the active mode. Returns the charge drained this tick. */
	public static float tick(ServerPlayer player, boolean boosting) {
		if (!isFlying(player)) {
			return 0f;
		}
		player.getAbilities().flying = true;
		// v0.11.2: cruise now sets the exact same vanilla flying-speed value the Flight power uses
		// (HeroFlight.HERO_FLYING_SPEED) instead of GL's own hand-derived figure, so the two feel
		// identical at rest; Boost scales that by the existing cruise->boost ratio
		// (BOOST_SPEED_BPS / FLIGHT_CRUISE_SPEED_BPS) so the boost-over-cruise feel is unchanged.
		float fly = boosting
				? com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED
						* (float) (GreenLanternConfig.BOOST_SPEED_BPS / GreenLanternConfig.FLIGHT_CRUISE_SPEED_BPS)
				: com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED;
		if (player.getAbilities().getFlyingSpeed() != fly) {
			player.getAbilities().setFlyingSpeed(fly);
			player.onUpdateAbilities();
		}
		player.setAttached(ModAttachments.GREEN_LANTERN_BOOSTING, boosting);
		player.resetFallDistance();

		double speed = player.getDeltaMovement().horizontalDistance() + Math.abs(player.getDeltaMovement().y);

		if (player.tickCount % 2 == 0 && speed > 0.02) {
			ServerLevel level = player.serverLevel();
			Vec3 back = player.getLookAngle().scale(-0.4);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					player.getX() + back.x, player.getY() + 0.3, player.getZ() + back.z, 2, 0.15, 0.15, 0.15, 0.01);
			level.sendParticles(ParticleTypes.END_ROD,
					player.getX() + back.x, player.getY() + 0.3, player.getZ() + back.z, 1, 0.1, 0.1, 0.1, 0.02);
		}

		// v0.11.4: a green particle trail while sprint-flying (Boost -- Shift+Sprint, per the caller),
		// continuously emitted at the player's position so it reads as a trail behind fast movement --
		// same single-point-emission trick used for Super Speed's trail, no dedicated trail entity
		// needed. Costs its own small per-second drain on top of Boost's existing cost. Gated on the
		// same tickCount%2/speed>0.02 cadence as the flame/end-rod effect above, so it doesn't spray
		// packets while hovering nearly in place and doubles up on the same tick as that effect.
		if (boosting) {
			if (player.tickCount % 2 == 0 && speed > 0.02) {
				player.serverLevel().sendParticles(TRAIL_DUST, player.getX(), player.getY() + 0.9, player.getZ(),
						1, 0.05, 0.05, 0.05, 0.0);
			}
			return (GreenLanternConfig.BOOST_COST_PER_SEC + GreenLanternConfig.FLIGHT_TRAIL_COST_PER_SEC) / 20f;
		}
		// v0.11.5: a flat 1 energy/sec regardless of hovering or cruising -- replaces the old cruise/hover split.
		return GreenLanternConfig.FLIGHT_COST_PER_SEC / 20f;
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
