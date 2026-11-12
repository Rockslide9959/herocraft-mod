package com.projecthero.mod.shazam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.shazam.data.ShazamState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shazam: flight. <b>Double-tap Space</b> in the air while transformed, built like the Saiyan's: vanilla
 * {@code mayfly}/{@code flying} so the server never kicks a hovering player, the movement itself the client's shared directional
 * flight (22 b/s, 44 sprinting; the cape streams). Free; it ends on landing, on another double-tap, or when he turns back.
 */
public final class ShazamFlight {
	public static final String FLIGHT = "flight";

	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

	private ShazamFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_POS.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
		LAST_POS.remove(id);
	}

	public static void toggle(ServerPlayer player) {
		if (Shazam.isFlying(player)) {
			stop(player);
			Shazam.startCooldown(player, FLIGHT, ShazamConfig.FLIGHT_TOGGLE_COOLDOWN);
			return;
		}
		if (!Shazam.transformed(player) || player.isPassenger() || player.isFallFlying() || player.isSpectator()) {
			return;
		}
		if (Shazam.cooldownRemaining(player, FLIGHT) > 0 || Shazam.eternityAge(player) >= 0) {
			return;
		}
		start(player);
		Shazam.startCooldown(player, FLIGHT, ShazamConfig.FLIGHT_TOGGLE_COOLDOWN);
	}

	static void start(ServerPlayer player) {
		ShazamState n = Shazam.state(player).copy();
		n.flying = true;
		Shazam.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		grantAbilities(player);
		player.resetFallDistance();
		ServerLevel level = player.serverLevel();
		Vec3 c = player.position();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.4, c.z, 16, 0.4, 0.2, 0.4, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 8, 0.4, 0.1, 0.4, 0.05);
		level.playSound(null, c.x, c.y, c.z, ShazamSounds.CRACKLE, net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 0.8f);
	}

	private static void grantAbilities(ServerPlayer player) {
		if (!player.getAbilities().instabuild && !player.isSpectator()) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
	}

	/** Ends the flight if it is running. */
	public static void stop(ServerPlayer player) {
		ShazamState s = player.getAttachedOrElse(ModAttachments.SHAZAM_STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			ShazamState n = s.copy();
			n.flying = false;
			Shazam.save(player, n);
		}
		STARTED.remove(player.getUUID());
		if (was && !player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	/** Per tick while transformed. */
	static void tick(ServerPlayer player) {
		Vec3 pos = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), pos);
		if (!Shazam.isFlying(player)) {
			return;
		}
		if (player.isSpectator() || player.isPassenger() || player.isFallFlying() || !player.isAlive()) {
			stop(player);
			return;
		}
		long now = player.level().getGameTime();
		Long start = STARTED.computeIfAbsent(player.getUUID(), k -> now);
		if (com.projecthero.mod.flight.FlightLanding.landed(player, start)) {
			stop(player);
			return;
		}
		if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
			if (now - start >= ShazamConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				stop(player); // the client dropped vanilla flight on its own: follow it
				Shazam.startCooldown(player, FLIGHT, ShazamConfig.FLIGHT_TOGGLE_COOLDOWN);
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		double speed = last == null ? 0.0 : pos.distanceTo(last);
		if (speed > 20.0) {
			speed = 0.0;
		}
		if (speed > 0.8 && player.tickCount % 3 == 0) {
			ServerLevel level = player.serverLevel();
			Vec3 travel = pos.subtract(last);
			Vec3 dir = travel.lengthSqr() > 1.0e-6 ? travel.normalize() : player.getLookAngle();
			Vec3 tail = pos.add(0, player.getBbHeight() * 0.5, 0).subtract(dir.scale(1.2));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tail.x, tail.y, tail.z, 2, 0.2, 0.2, 0.2, 0.02);
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
