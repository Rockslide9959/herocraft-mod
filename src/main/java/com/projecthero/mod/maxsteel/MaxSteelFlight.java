package com.projecthero.mod.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 4 -- Turbo Flight Mode. A toggle. Built on vanilla's {@code mayfly}/{@code flying} the same
 * way {@code IronManFlight} is, but with a per-tick velocity model so it accelerates and decelerates
 * smoothly and hovers stably instead of feeling like Creative Mode: movement keys steer, jump
 * ascends, sneak descends, sprint boosts. Drain is 1.5/s hovering, 2/s moving, 4/s boosting.
 *
 * <p><b>Cleanup contract</b>: {@link #forceStop} is the one place the {@code mayfly} grant is revoked,
 * and it is called from every lifecycle path (death / respawn / disconnect / dimension change /
 * suit-down / power loss / energy depletion / mode switch). A leftover grant can never happen.
 */
public final class MaxSteelFlight {
	private MaxSteelFlight() {
	}

	public static boolean isFlying(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false);
	}

	public static void onEnter(ServerPlayer player) {
		player.setAttached(ModAttachments.MAX_STEEL_FLYING, true);
		if (!player.getAbilities().instabuild) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.5f, 1.3f);
	}

	/**
	 * Stop Turbo Flight if it is running: clear the flag, revoke {@code mayfly}/{@code flying} (unless
	 * something else still wants it), and optionally grant a short fall-damage grace.
	 */
	public static void forceStop(ServerPlayer player, boolean grantFallGrace) {
		boolean wasFlying = isFlying(player);
		if (wasFlying) {
			player.setAttached(ModAttachments.MAX_STEEL_FLYING, false);
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
		if (wasFlying && grantFallGrace) {
			com.projecthero.mod.maxsteel.data.MaxSteelState c = MaxSteel.state(player).copy();
			c.fallGraceUntil = player.level().getGameTime() + MaxSteelConfig.FLIGHT_LANDING_GRACE_TICKS;
			MaxSteel.save(player, c);
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.5f, 1.0f);
		}
	}

	/**
	 * Per-tick while Turbo Flight is the active mode. Returns the energy drained this tick.
	 *
	 * <p>Movement itself is left to vanilla's {@code flying} handling (WASD / jump / sneak, client-side
	 * and accepted by the server's movement tolerance -- the same division of labour the rest of the
	 * mod's player-movement powers use). This method only keeps the grant alive, tunes the fly speed
	 * for a less twitchy feel, drains by how fast the player is actually going, and emits thrusters.
	 */
	public static float drainPerTick(ServerPlayer player) {
		if (!isFlying(player)) {
			return 0f;
		}
		player.getAbilities().flying = true;
		// v0.9.2: unified hero-flight speed -- Turbo Flight, Thor flight and the experimental Flight
		// power all fly at HERO_FLYING_SPEED now (vanilla doubles it to ~0.12 while sprinting). This
		// used to be a bespoke 0.055/0.09 pair that made sprint-flight an odd ~0.18 outlier.
		float fly = com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED;
		if (player.getAbilities().getFlyingSpeed() != fly) {
			player.getAbilities().setFlyingSpeed(fly);
			player.onUpdateAbilities();
		}
		player.resetFallDistance();

		double speed = player.getDeltaMovement().horizontalDistance() + Math.abs(player.getDeltaMovement().y);
		if (player.tickCount % 2 == 0 && speed > 0.02) {
			ServerLevel level = player.serverLevel();
			Vec3 back = player.getLookAngle().scale(-0.4);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					player.getX() + back.x, player.getY() + 0.3, player.getZ() + back.z, 3, 0.18, 0.18, 0.18, 0.01);
			level.sendParticles(ParticleTypes.END_ROD,
					player.getX() + back.x, player.getY() + 0.3, player.getZ() + back.z, 1, 0.12, 0.12, 0.12, 0.02);
		}

		float perSec;
		if (player.isSprinting() && speed > 0.15) {
			perSec = MaxSteelConfig.FLIGHT_BOOST_DRAIN_PER_SEC;
		} else if (speed > 0.06) {
			perSec = MaxSteelConfig.FLIGHT_NORMAL_DRAIN_PER_SEC;
		} else {
			perSec = MaxSteelConfig.FLIGHT_HOVER_DRAIN_PER_SEC;
		}
		return perSec / 20f;
	}

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.isSpectator()
				|| player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| com.projecthero.mod.spider.SpiderSwing.isSwinging(player);
	}
}
