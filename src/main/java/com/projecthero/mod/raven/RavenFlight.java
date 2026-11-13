package com.projecthero.mod.raven;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.raven.data.RavenState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven: her calm levitation. <b>Double-tap Space</b> in the air takes off / lands, built like the Saiyan's: vanilla
 * {@code mayfly}/{@code flying} so the server never kicks a hovering player, the movement the client's shared directional flight
 * ({@link RavenConfig#FLIGHT_CRUISE_BPS} cruising, {@link RavenConfig#FLIGHT_SPRINT_BPS} sprinting, a dead hover with no input). Free,
 * and it ends on landing or another double-tap. Meditation (Shift+V held) uses the same abilities to float her in place with no
 * movement at all (the client's tune is zero while she meditates).
 */
public final class RavenFlight {
	public static final String FLIGHT = "flight";

	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();

	private RavenFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
	}

	public static void toggle(ServerPlayer player) {
		if (Raven.isFlying(player)) {
			stop(player);
			Raven.startCooldown(player, FLIGHT, RavenConfig.FLIGHT_TOGGLE_COOLDOWN);
			return;
		}
		if (!Raven.hasPower(player) || player.isPassenger() || player.isFallFlying() || Raven.meditating(player) || !player.isAlive()) {
			return;
		}
		if (Raven.cooldownRemaining(player, FLIGHT) > 0) {
			return;
		}
		start(player);
		Raven.startCooldown(player, FLIGHT, RavenConfig.FLIGHT_TOGGLE_COOLDOWN);
	}

	static void start(ServerPlayer player) {
		RavenState n = Raven.state(player).copy();
		n.flying = true;
		Raven.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		player.removeEffect(MobEffects.SLOW_FALLING);
		grantAbilities(player);
		player.resetFallDistance();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		Raven.darkPuff(level, c.add(0, 0.3, 0), 14, 0.4);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.FLIGHT, SoundSource.PLAYERS, 0.35f, 0.7f);
	}

	private static void grantAbilities(ServerPlayer player) {
		if (!player.getAbilities().instabuild && !player.isSpectator()) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
	}

	private static void dropAbilities(ServerPlayer player) {
		if (!player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	/** Ends the flight if it is running. */
	public static void stop(ServerPlayer player) {
		RavenState s = player.getAttachedOrElse(ModAttachments.RAVEN_STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			RavenState n = s.copy();
			n.flying = false;
			Raven.save(player, n);
		}
		STARTED.remove(player.getUUID());
		if (was && !Raven.meditating(player)) {
			dropAbilities(player);
		}
	}

	// ---------------------------------------------------------------- meditation's float

	/** Shift+V down: she rises a little and floats in place. */
	static void startMeditation(ServerPlayer player) {
		RavenState n = Raven.state(player).copy();
		n.meditating = true;
		Raven.save(player, n);
		player.removeEffect(MobEffects.SLOW_FALLING);
		if (player.onGround() && player.level().noCollision(player, player.getBoundingBox().move(0, RavenConfig.MEDITATE_FLOAT, 0))) {
			player.teleportTo(player.getX(), player.getY() + RavenConfig.MEDITATE_FLOAT, player.getZ());
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		grantAbilities(player);
	}

	/** Shift+V up (or anything that interrupts her). */
	public static void stopMeditation(ServerPlayer player) {
		RavenState s = player.getAttachedOrElse(ModAttachments.RAVEN_STATE, null);
		if (s == null || !s.meditating) {
			return;
		}
		RavenState n = s.copy();
		n.meditating = false;
		Raven.save(player, n);
		if (!n.flying) {
			dropAbilities(player);
		}
	}

	/** Per tick for every Raven. */
	static void tick(ServerPlayer player) {
		RavenState s = Raven.state(player);
		if (s.meditating) {
			if (!player.isAlive() || player.isSpectator() || player.isPassenger()) {
				stopMeditation(player);
			} else {
				if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
					grantAbilities(player);
				}
				player.resetFallDistance();
			}
		}
		if (!s.flying) {
			return;
		}
		if (player.isSpectator() || player.isPassenger() || player.isFallFlying() || !player.isAlive()) {
			stop(player);
			return;
		}
		long now = player.level().getGameTime();
		Long start = STARTED.computeIfAbsent(player.getUUID(), k -> now);
		if (!s.meditating && com.projecthero.mod.flight.FlightLanding.landed(player, start)) {
			stop(player);
			return;
		}
		if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
			if (now - start >= RavenConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly && !s.meditating) {
				stop(player); // the client dropped vanilla flight on its own (landed / double-tapped): follow it
				Raven.startCooldown(player, FLIGHT, RavenConfig.FLIGHT_TOGGLE_COOLDOWN);
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		// the cloak's shadow trailing off her feet
		if (player.tickCount % 3 == 0) {
			ServerLevel level = (ServerLevel) player.level();
			Vec3 c = player.position().add(0, 0.2, 0);
			level.sendParticles(Raven.dust(Raven.VOID, 1.0f), c.x, c.y, c.z, 1, 0.2, 0.1, 0.2, 0.0);
			if (player.tickCount % 12 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 1, 0.2, 0.1, 0.2, 0.0);
			}
		}
	}

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
	}
}
