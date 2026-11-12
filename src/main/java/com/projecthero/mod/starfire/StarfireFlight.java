package com.projecthero.mod.starfire;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Starfire: her flight. <b>Double-tap Space</b> in the air takes off and lands, built like the Saiyan's: vanilla
 * {@code mayfly}/{@code flying} so the server never kicks a hovering player, the movement itself the client's shared directional
 * flight (W along the look, Sprint faster, a dead hover with no input). <b>Joy</b> sets its speed (up to 40% faster, Fury up to 30%
 * slower) and its cost (1 Starbolt a second, 60% less at full Joy, 60% more at full Fury). It ends at 0 Starbolt, on landing or on
 * another double-tap. Flying builds Joy ({@link Starfire#tickMood}).
 */
public final class StarfireFlight {
	public static final String FLIGHT = "flight";

	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

	private StarfireFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_POS.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
		LAST_POS.remove(id);
	}

	static boolean isFlying(ServerPlayer player) {
		return Starfire.isFlying(player);
	}

	/** The double-tap. */
	public static void toggle(ServerPlayer player) {
		if (isFlying(player)) {
			stop(player);
			Starfire.startCooldown(player, FLIGHT, StarfireConfig.FLIGHT_TOGGLE_COOLDOWN);
			return;
		}
		if (!Starfire.hasPower(player) || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying()) {
			return;
		}
		if (Starfire.cooldownRemaining(player, FLIGHT) > 0) {
			return;
		}
		if (Starfire.starbolt(player) <= 0f && !player.getAbilities().instabuild) {
			Starfire.say(player, "message.projecthero.starfire.no_starbolt", ChatFormatting.GRAY);
			return;
		}
		start(player);
		Starfire.startCooldown(player, FLIGHT, StarfireConfig.FLIGHT_TOGGLE_COOLDOWN);
	}

	public static void start(ServerPlayer player) {
		StarfireState n = Starfire.state(player).copy();
		n.flying = true;
		Starfire.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		grantAbilities(player);
		player.resetFallDistance();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		level.sendParticles(Starfire.dust(Starfire.GREEN, 1.4f), c.x, c.y + 0.6, c.z, 16, 0.4, 0.4, 0.4, 0.0);
		Starfire.sound(level, c, SoundEvents.FIRECHARGE_USE, 0.25f, 1.6f);
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
		StarfireState s = player.getAttachedOrElse(ModAttachments.STARFIRE_STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			StarfireState n = s.copy();
			n.flying = false;
			Starfire.save(player, n);
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
		if (!isFlying(player)) {
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
			if (now - start >= StarfireConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				stop(player); // the client dropped vanilla flight on its own (landed / double-tapped): follow it
				Starfire.startCooldown(player, FLIGHT, StarfireConfig.FLIGHT_TOGGLE_COOLDOWN);
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		// the upkeep (in place; re-sent with the bar)
		if (!player.getAbilities().instabuild) {
			StarfireState s = Starfire.state(player);
			s.starbolt = Math.max(0f, s.starbolt - Starfire.flightCostPerSecond(player) / 20f);
			if (s.starbolt <= 0f) {
				stop(player);
				Starfire.say(player, "message.projecthero.starfire.flight_out", ChatFormatting.GRAY);
				return;
			}
		}
		double speed = last == null ? 0.0 : pos.distanceTo(last);
		if (speed > 20.0) {
			speed = 0.0;
		}
		if (speed > 0.5 && player.tickCount % 2 == 0) {
			ServerLevel level = (ServerLevel) player.level();
			Vec3 travel = pos.subtract(last);
			Vec3 dir = travel.lengthSqr() > 1.0e-6 ? travel.normalize() : player.getLookAngle();
			Vec3 tail = pos.add(0, player.getBbHeight() * 0.6, 0).subtract(dir.scale(0.9));
			level.sendParticles(Starfire.dust(Starfire.GREEN, 1.0f), tail.x, tail.y, tail.z, 2, 0.12, 0.12, 0.12, 0.0);
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
