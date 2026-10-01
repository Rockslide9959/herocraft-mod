package com.projecthero.mod.kryptonian;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.kryptonian.data.KryptonianState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Kryptonian flight (v0.14.8): double-tap jump in the air to take off or drop out of it. Built like Green Lantern's Ring
 * Flight -- vanilla {@code mayfly}/{@code flying} so the server never kicks a hovering player -- but the movement itself is
 * the client's ({@code KryptonianFlightClient}): true directional flight along the look vector, W to fly, S to brake,
 * Sprint for super-speed flight, Space / Sneak straight up / down, a dead hover with no input. The server keeps the
 * abilities flags in line, lands him when he touches the ground, and draws the speed trail and the sonic boom.
 *
 * <p>No stamina: a Kryptonian flies as long as he likes. Kryptonite and a spent Solar Flare drop him out of the sky.
 */
public final class KryptonianFlight {
	/** Game time flight was last engaged (the lift-off grace clock). */
	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	/** Last tick's position, for the server-side speed (a player's velocity is the client's, not the server's). */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** The sonic boom fired and has not re-armed yet. */
	private static final Map<UUID, Boolean> BOOMED = new ConcurrentHashMap<>();

	private KryptonianFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_POS.clear();
		BOOMED.clear();
	}

	/** The double-tap. Server-validated. */
	public static void toggle(ServerPlayer player) {
		if (Kryptonian.isFlying(player)) {
			stop(player, false);
			return;
		}
		if (!Kryptonian.canAct(player)) {
			return;
		}
		if (player.isPassenger() || player.isFallFlying()) {
			return;
		}
		start(player);
	}

	public static void start(ServerPlayer player) {
		KryptonianState n = Kryptonian.state(player).copy();
		n.flying = true;
		Kryptonian.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		BOOMED.remove(player.getUUID());
		grantAbilities(player);
		player.resetFallDistance();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.3f, 1.6f);
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.4, 0.1, 0.4, 0.05);
	}

	/** Re-establishes the abilities flags after a join / dimension change while flying. */
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

	/** Stops flight if it is running; {@code dropped} = knocked out of the sky (kryptonite / burnt out). */
	public static void stop(ServerPlayer player, boolean dropped) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			KryptonianState n = s.copy();
			n.flying = false;
			n.flightBoost = false;
			Kryptonian.save(player, n);
		}
		STARTED.remove(player.getUUID());
		BOOMED.remove(player.getUUID());
		if (was && !player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
		if (was && dropped) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.kryptonian.fall")
					.withStyle(ChatFormatting.RED), true);
		}
	}

	/** Per tick for every Kryptonian. */
	static void tick(ServerPlayer player) {
		KryptonianState s = Kryptonian.state(player);
		Vec3 pos = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), pos);
		if (!s.flying) {
			return;
		}
		if (!Kryptonian.empowered(player) || player.isSpectator() || player.isPassenger() || player.isFallFlying()) {
			stop(player, !Kryptonian.empowered(player));
			return;
		}
		long now = player.level().getGameTime();
		Long start = STARTED.computeIfAbsent(player.getUUID(), k -> now);
		if (player.onGround() && now - start >= KryptonianConfig.FLIGHT_LIFTOFF_GRACE) {
			stop(player, false);
			return;
		}
		if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
			if (now - start >= KryptonianConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				// the client dropped vanilla flight on its own (landed / double-tapped): follow it
				stop(player, false);
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		double speed = last == null ? 0.0 : pos.distanceTo(last);
		if (speed > 20.0) {
			speed = 0.0; // a teleport, not flight
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 travel = last == null ? Vec3.ZERO : pos.subtract(last);
		Vec3 dir = travel.lengthSqr() > 1.0e-6 ? travel.normalize() : player.getLookAngle();
		Vec3 mid = pos.add(0, player.getBbHeight() * 0.5, 0);
		if (speed > 0.5 && player.tickCount % 2 == 0) {
			Vec3 tail = mid.subtract(dir.scale(1.2));
			level.sendParticles(ParticleTypes.CLOUD, tail.x, tail.y, tail.z, speed > 1.2 ? 3 : 1, 0.15, 0.15, 0.15, 0.01);
		}
		if (speed > 1.2) {
			Vec3 tail = mid.subtract(dir.scale(0.8));
			level.sendParticles(ParticleTypes.END_ROD, tail.x, tail.y, tail.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		boolean boomed = BOOMED.getOrDefault(player.getUUID(), false);
		// v0.14.11: the boom only with Flight Boost on, sprint-flying (it used to fire on every break into sprint flight)
		KryptonianState st = Kryptonian.state(player);
		if (!boomed && st.flightBoost && player.isSprinting() && speed >= KryptonianConfig.SONIC_BOOM_SPEED) {
			BOOMED.put(player.getUUID(), true);
			sonicBoom(level, player, mid, dir);
		} else if (boomed && speed < KryptonianConfig.SONIC_BOOM_REARM_SPEED) {
			BOOMED.remove(player.getUUID());
		}
	}

	/**
	 * v0.14.11: X while flying -- Flight Boost on / off. Sprint flight goes from 35 to 55 blocks a second, and breaking
	 * into it is the only thing that fires the sonic boom. Free; ends when the flight does.
	 */
	public static void toggleBoost(ServerPlayer player) {
		if (!Kryptonian.isFlying(player) || !Kryptonian.canAct(player)) {
			return;
		}
		KryptonianState n = Kryptonian.state(player).copy();
		n.flightBoost = !n.flightBoost;
		Kryptonian.save(player, n);
		BOOMED.remove(player.getUUID());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), n.flightBoost ? SoundEvents.FIRECHARGE_USE
				: SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6f, n.flightBoost ? 1.4f : 1.9f);
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(n.flightBoost
				? "message.projecthero.kryptonian.boost_on" : "message.projecthero.kryptonian.boost_off")
				.withStyle(net.minecraft.ChatFormatting.GOLD), true);
	}

	/** Breaking into super-speed flight: a ring of cloud around the flight path and a thunderclap. */
	private static void sonicBoom(ServerLevel level, ServerPlayer player, Vec3 at, Vec3 dir) {
		Vec3 a = dir.cross(new Vec3(0, 1, 0));
		if (a.lengthSqr() < 1.0e-4) {
			a = new Vec3(1, 0, 0);
		}
		a = a.normalize();
		Vec3 b = a.cross(dir).normalize();
		for (int i = 0; i < 24; i++) {
			double t = Math.PI * 2 * i / 24.0;
			Vec3 p = at.add(a.scale(Math.cos(t) * 1.8)).add(b.scale(Math.sin(t) * 1.8));
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.02);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5f, 0.6f);
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
