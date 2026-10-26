package com.projecthero.mod.falcon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.FlightLanding;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (second pass, user: "flight that works like the other flights in the game -- like the Kryptonian"): the Falcon's
 * jet flight on the mod-wide directional flight. Double-tap jump (on the ground or in the air) snaps the wings open and
 * takes off, double-tap again drops out. Vanilla {@code mayfly} / {@code flying} keep the server from kicking a hovering
 * player; the movement is the client's ({@code client.flight.DirectionalFlight} with {@link #tune}):
 * <ul>
 *   <li>W / S along the full look vector, A / D slide, Space / Sneak straight up / down, a dead hover with no input;</li>
 *   <li>{@value #CRUISE_BPS} b/s cruising, {@value #SPRINT_BPS} sprint-flying;</li>
 *   <li><b>hold X</b>: the jets burn -- {@value #BOOST_BPS} b/s along the look whatever the keys say, 14 Thrust a second
 *       (a tap of X is still the Afterburner burst).</li>
 * </ul>
 * The server keeps the abilities flags in line, lands him when he touches down (the shared {@link FlightLanding} rule --
 * fast is a hero landing), and draws the jet flame and the wingtip contrails.
 */
public final class FalconFlight {
	public static final double CRUISE_BPS = 19.0;
	public static final double SPRINT_BPS = 35.0;
	public static final double BOOST_BPS = 40.0;

	/** Game time flight was last engaged (the lift-off grace clock). */
	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();

	private FalconFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
	}

	/** The flight's numbers this tick (client and gametests): cruise / sprint, and the jet burn drives along the look. */
	public static DirectionalFlightModel.Tune tune(boolean sprint, boolean boosting) {
		DirectionalFlightModel.Tune t = new DirectionalFlightModel.Tune(sprint ? FalconConfig.FLIGHT_SPRINT_SPEED : FalconConfig.FLIGHT_SPEED,
				FalconConfig.FLIGHT_VERTICAL_SPEED, FalconConfig.FLIGHT_STRAFE, FalconConfig.FLIGHT_ACCELERATION, FalconConfig.FLIGHT_BRAKE,
				FalconConfig.FLIGHT_IDLE, 0.0, 0.0, false, DirectionalFlightModel.PUSH_CARRY_TICKS, true);
		return boosting ? t.withForcedForward(FalconConfig.FLIGHT_BOOST_SPEED) : t;
	}

	// ---------------------------------------------------------------- start / stop

	/** The double-tap. Server-validated. */
	public static void toggle(ServerPlayer player) {
		if (Falcon.state(player).flying) {
			stop(player);
			return;
		}
		start(player);
	}

	/** Wings out, jets lit: flight on (from the ground, a jet kick up first). False if he can't fly right now. */
	public static boolean start(ServerPlayer player) {
		FalconState s = Falcon.state(player);
		if (!s.hasPower || s.flying || !player.isAlive() || player.isPassenger() || player.isSpectator() || player.isFallFlying()) {
			return false;
		}
		long now = player.level().getGameTime();
		FalconState n = s.copy();
		n.flying = true;
		n.boosting = false;
		n.animId = FalconState.ANIM_TAKEOFF;
		n.animStart = now;
		Falcon.save(player, n);
		STARTED.put(player.getUUID(), now);
		grantAbilities(player);
		player.resetFallDistance();
		if (player.onGround()) {
			AbilityHelpers.launchSelf(player, new Vec3(player.getDeltaMovement().x, FalconConfig.TAKEOFF_LIFT, player.getDeltaMovement().z));
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 14, 0.5, 0.1, 0.5, 0.06);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.8, c.z, 8, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.8f);
		return true;
	}

	/** Folds the wings away (landed, a double-tap, in water, a command). */
	public static void stop(ServerPlayer player) {
		FalconState s = player.getAttachedOrElse(ModAttachments.FALCON_STATE, null);
		STARTED.remove(player.getUUID());
		if (s == null || !s.flying) {
			return;
		}
		FalconState n = s.copy();
		n.flying = false;
		n.boosting = false;
		Falcon.save(player, n);
		if (!player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS,
				0.7f, 1.3f);
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

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
	}

	// ---------------------------------------------------------------- the jet burn (X held)

	/** X held / let go: the jets burn (only in flight, only with Thrust left). */
	public static void setBoost(ServerPlayer player, boolean on) {
		FalconState s = Falcon.state(player);
		boolean want = on && s.hasPower && s.flying && s.thrust > 0.5f;
		if (s.boosting == want) {
			return;
		}
		FalconState n = s.copy();
		n.boosting = want;
		Falcon.save(player, n);
		Vec3 c = player.position();
		if (want) {
			player.level().playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.9f, 0.6f);
			player.level().playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer player, long now) {
		FalconState s = Falcon.state(player);
		if (!s.flying) {
			return;
		}
		player.resetFallDistance();
		if (player.isInWater() || player.isPassenger() || player.isSpectator() || player.isFallFlying() || !player.isAlive()) {
			stop(player);
			return;
		}
		Long start = STARTED.computeIfAbsent(player.getUUID(), k -> now);
		// the shared landing rule (on the ground, past the take-off grace, not rising)
		if (FlightLanding.landed(player, start)) {
			land(player, Falcon.recentSpeed(player));
			return;
		}
		if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
			if (now - start >= FalconConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				// the client dropped vanilla flight on its own (touched down / double-tapped): follow it
				land(player, Falcon.recentSpeed(player));
				return;
			}
			grantAbilities(player);
		}
		ServerLevel level = (ServerLevel) player.level();
		double speed = Falcon.recentSpeed(player);
		// the jets: flame and smoke out of the twin nozzles while they burn
		if (s.boosting) {
			Vec3 look = player.getLookAngle();
			Vec3 back = player.position().add(0, 0.9, 0).subtract(look.scale(0.7));
			level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 3, 0.08, 0.08, 0.08, 0.01);
			if (now % 2 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, back.x, back.y, back.z, 2, 0.12, 0.12, 0.12, 0.01);
			}
			if (now % 10 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.6f);
			}
		}
		// contrails off the wingtips at speed
		if (speed > 1.2 && now % 2 == 0) {
			Vec3 c = player.position().add(0, 0.9, 0);
			Vec3 look = player.getLookAngle();
			Vec3 side = look.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
			for (int k = -1; k <= 1; k += 2) {
				Vec3 tip = c.add(side.scale(2.4 * k)).subtract(look.scale(0.6));
				level.sendParticles(ParticleTypes.CLOUD, tip.x, tip.y, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	/** Touched down: the flight ends; fast enough, a hero landing (the kneel and a dust ring). */
	static void land(ServerPlayer player, double speed) {
		stop(player);
		player.resetFallDistance();
		if (speed < FalconConfig.HERO_LANDING_SPEED) {
			return;
		}
		Falcon.setAnim(player, FalconState.ANIM_LAND);
		ServerLevel level = (ServerLevel) player.level();
		BlockPos below = player.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		Vec3 c = player.position();
		if (!ground.isAir()) {
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0; i < 28; i++) {
				double a = i * Math.PI * 2 / 28;
				for (double r = 1.0; r <= 2.8; r += 0.9) {
					level.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0.05, 0.02, 0.05, 0.12);
				}
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 16, 1.2, 0.05, 1.2, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SMALL_FALL, SoundSource.PLAYERS, 1.0f, 0.6f);
	}
}
