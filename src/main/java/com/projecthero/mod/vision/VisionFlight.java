package com.projecthero.mod.vision;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.vision.data.VisionAttachments;
import com.projecthero.mod.vision.data.VisionState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Vision: his flight. <b>Double-tap Space</b> in the air takes off and lands, built like Starfire's: vanilla
 * {@code mayfly}/{@code flying} so the server never kicks a hovering player, the movement itself the client's shared directional flight
 * (W along the look, Sprint faster, a dead hover with no input; 20 b/s cruise, 36 sprinting). It is his own density control, so it
 * costs nothing; Diamond Density is too heavy for it (flight won't start, and turning density on drops him out of the sky). It ends on
 * landing or another double-tap.
 */
public final class VisionFlight {
	public static final String FLIGHT = "flight";

	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

	private VisionFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_POS.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
		LAST_POS.remove(id);
	}

	/** The double-tap. */
	public static void toggle(ServerPlayer player) {
		if (Vision.isFlying(player)) {
			stop(player);
			Vision.startCooldown(player, FLIGHT, VisionConfig.FLIGHT_TOGGLE_COOLDOWN);
			return;
		}
		if (!Vision.hasPower(player) || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying()) {
			return;
		}
		if (Vision.cooldownRemaining(player, FLIGHT) > 0) {
			return;
		}
		if (Vision.dense(player)) {
			Vision.say(player, "message.projecthero.vision.too_dense", ChatFormatting.GRAY);
			return;
		}
		start(player);
		Vision.startCooldown(player, FLIGHT, VisionConfig.FLIGHT_TOGGLE_COOLDOWN);
	}

	public static void start(ServerPlayer player) {
		VisionState n = Vision.state(player).copy();
		n.flying = true;
		Vision.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		grantAbilities(player);
		player.resetFallDistance();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		level.sendParticles(Vision.dust(Vision.GOLD, 1.2f), c.x, c.y + 0.6, c.z, 12, 0.4, 0.4, 0.4, 0.0);
		Vision.sound(level, c, SoundEvents.BEACON_AMBIENT, 0.4f, 1.8f);
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
		VisionState s = player.getAttachedOrElse(VisionAttachments.STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			VisionState n = s.copy();
			n.flying = false;
			Vision.save(player, n);
		}
		STARTED.remove(player.getUUID());
		if (was && !player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	static void tick(ServerPlayer player) {
		Vec3 pos = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), pos);
		if (!Vision.isFlying(player)) {
			return;
		}
		if (player.isSpectator() || player.isPassenger() || player.isFallFlying() || !player.isAlive() || Vision.dense(player)) {
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
			if (now - start >= VisionConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				stop(player); // the client dropped vanilla flight on its own (landed / double-tapped): follow it
				Vision.startCooldown(player, FLIGHT, VisionConfig.FLIGHT_TOGGLE_COOLDOWN);
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		double speed = last == null ? 0.0 : pos.distanceTo(last);
		if (speed > 20.0) {
			speed = 0.0;
		}
		if (speed > 0.6 && player.tickCount % 3 == 0) {
			ServerLevel level = (ServerLevel) player.level();
			Vec3 travel = pos.subtract(last);
			Vec3 dir = travel.lengthSqr() > 1.0e-6 ? travel.normalize() : player.getLookAngle();
			Vec3 tail = pos.add(0, player.getBbHeight() * 0.6, 0).subtract(dir.scale(0.9));
			level.sendParticles(Vision.dust(Vision.GOLD, 0.8f), tail.x, tail.y, tail.z, 1, 0.1, 0.1, 0.1, 0.0);
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
