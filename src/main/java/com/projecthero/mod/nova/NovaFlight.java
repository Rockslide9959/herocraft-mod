package com.projecthero.mod.nova;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.nova.data.NovaState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Nova flight (v0.15.13): double-tap jump in the air while suited to take off or drop out of it. Vanilla
 * {@code mayfly}/{@code flying} keep the server from kicking a hovering player; the movement itself is the shared
 * client-side directional flight ({@code client.flight.DirectionalFlight} with {@code DirectionalFlightModel.nova}):
 * 20 blocks a second, 40 sprinting, W/S along the look, Space/Sneak straight up/down, a dead hover with no input.
 * The server lands him on the ground, draws the golden trail and measures his speed for the fast-flight drain.
 */
public final class NovaFlight {
	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** Server-measured speed (blocks/tick) of the last tick. */
	private static final Map<UUID, Double> SPEED = new ConcurrentHashMap<>();

	private NovaFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_POS.clear();
		SPEED.clear();
	}

	/** True while he flies faster than {@link NovaConfig#FAST_FLIGHT_SPEED} (the Nova Force trickles away). */
	public static boolean flyingFast(ServerPlayer player) {
		return SPEED.getOrDefault(player.getUUID(), 0.0) > NovaConfig.FAST_FLIGHT_SPEED;
	}

	/** Gametests: pretend the server measured this speed (mock players never move by themselves). */
	public static void setMeasuredSpeedForTests(ServerPlayer player, double blocksPerTick) {
		SPEED.put(player.getUUID(), blocksPerTick);
	}

	/** The double-tap. Server-validated. */
	public static void toggle(ServerPlayer player) {
		if (Nova.isFlying(player)) {
			stop(player, false);
			return;
		}
		if (!Nova.canAct(player) || player.isPassenger() || player.isFallFlying()) {
			return;
		}
		start(player);
	}

	public static void start(ServerPlayer player) {
		NovaState n = Nova.state(player).copy();
		if (!n.suited) {
			return;
		}
		n.flying = true;
		Nova.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		grantAbilities(player);
		player.resetFallDistance();
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8f, 2.0f);
		level.sendParticles(Nova.GOLD, player.getX(), player.getY(), player.getZ(), 16, 0.4, 0.1, 0.4, 0.02);
	}

	static void resume(ServerPlayer player) {
		STARTED.put(player.getUUID(), player.level().getGameTime());
		grantAbilities(player);
	}

	private static void grantAbilities(ServerPlayer player) {
		if (!player.getAbilities().instabuild && !player.isSpectator()) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
	}

	/** Stops flight if it is running. */
	public static void stop(ServerPlayer player, boolean dropped) {
		NovaState s = player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			NovaState n = s.copy();
			n.flying = false;
			Nova.save(player, n);
		}
		STARTED.remove(player.getUUID());
		SPEED.remove(player.getUUID());
		if (was && !player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	static void tick(ServerPlayer player) {
		NovaState s = Nova.state(player);
		Vec3 pos = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), pos);
		if (!s.flying) {
			return;
		}
		if (!s.suited || player.isSpectator() || player.isPassenger() || player.isFallFlying() || !player.isAlive()) {
			stop(player, false);
			return;
		}
		long now = player.level().getGameTime();
		Long start = STARTED.computeIfAbsent(player.getUUID(), k -> now);
		if (player.onGround() && now - start >= NovaConfig.FLIGHT_LIFTOFF_GRACE) {
			stop(player, false);
			return;
		}
		if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
			if (now - start >= NovaConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				stop(player, false); // the client dropped vanilla flight on its own
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		double speed = last == null ? 0.0 : pos.distanceTo(last);
		if (speed > 20.0) {
			speed = 0.0; // a teleport, not flight
		}
		SPEED.put(player.getUUID(), speed);
		// the golden trail
		ServerLevel level = (ServerLevel) player.level();
		Vec3 travel = last == null ? Vec3.ZERO : pos.subtract(last);
		Vec3 dir = travel.lengthSqr() > 1.0e-6 ? travel.normalize() : player.getLookAngle();
		Vec3 mid = pos.add(0, player.getBbHeight() * 0.45, 0);
		if (speed > 0.15) {
			Vec3 tail = mid.subtract(dir.scale(0.9));
			level.sendParticles(Nova.GOLD, tail.x, tail.y, tail.z, speed > 1.2 ? 4 : 2, 0.12, 0.12, 0.12, 0.0);
			if (player.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, tail.x, tail.y, tail.z, 1, 0.08, 0.08, 0.08, 0.0);
			}
			if (speed > 1.2 && player.tickCount % 3 == 0) {
				Vec3 far = mid.subtract(dir.scale(1.8));
				level.sendParticles(Nova.GOLD_BIG, far.x, far.y, far.z, 2, 0.2, 0.2, 0.2, 0.0);
			}
		} else if (player.tickCount % 6 == 0) {
			level.sendParticles(Nova.GOLD, pos.x, pos.y - 0.1, pos.z, 2, 0.2, 0.05, 0.2, 0.0);
		}
	}

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false)
				|| com.projecthero.mod.kryptonian.Kryptonian.isFlying(player);
	}
}
