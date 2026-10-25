package com.projecthero.mod.silversurfer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.FlightLanding;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Silver Surfer's board flight -- the fastest in the mod. Double-tap jump (or right-click a board item) and
 * the board flies up under his feet; the movement itself is the shared client-simulated directional flight
 * ({@code client.flight.DirectionalFlight}) with {@link #tune}: 60 b/s cruising, 110 sprinting, a forced 150 b/s along the
 * look during Shift+X Light-Speed (3 s at most, so it never outruns chunk loading for long). The server lends
 * {@code mayfly}, runs the landing rule, the sprint upkeep, Erasure, and Light-Speed's ram damage.
 */
public final class SilverSurferFlight {
	/** Players the board lent flight to (so exactly that can be taken back). */
	private static final Set<UUID> GRANTED = ConcurrentHashMap.newKeySet();
	/** Creatures already rammed by the running Light-Speed burst. */
	private static final Map<UUID, Set<Integer>> RAMMED = new ConcurrentHashMap<>();

	private SilverSurferFlight() {
	}

	static void clearSessionState() {
		GRANTED.clear();
		RAMMED.clear();
	}

	/** The directional-flight numbers while surfing (client and gametests). */
	public static DirectionalFlightModel.Tune tune(boolean sprint, boolean lightSpeed) {
		double bps = sprint ? SilverSurferConfig.SPRINT_BPS : SilverSurferConfig.CRUISE_BPS;
		DirectionalFlightModel.Tune t = new DirectionalFlightModel.Tune(bps / 20.0, SilverSurferConfig.VERTICAL_BPS / 20.0, 0.6,
				SilverSurferConfig.FLIGHT_ACCELERATION, SilverSurferConfig.FLIGHT_REVERSE, SilverSurferConfig.FLIGHT_IDLE, 0.0, 0.0, false,
				DirectionalFlightModel.PUSH_CARRY_TICKS, true);
		if (lightSpeed) {
			return new DirectionalFlightModel.Tune(SilverSurferConfig.LIGHT_SPEED_BPS / 20.0, t.verticalSpeed(), 0.4, 0.35, 0.35, 0.35, 0.0,
					SilverSurferConfig.LIGHT_SPEED_BPS / 20.0, false, 0, false);
		}
		return t;
	}

	/** Double-tap jump: on the board or off it. */
	public static void toggle(ServerPlayer p) {
		if (SilverSurfer.state(p).surfing) {
			stop(p, true);
		} else {
			start(p);
		}
	}

	public static boolean start(ServerPlayer p) {
		SilverSurferState s = SilverSurfer.state(p);
		if (!s.hasPower || s.surfing || !p.isAlive() || p.isSpectator()) {
			return false;
		}
		if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			return false;
		}
		SilverSurferState n = s.copy();
		n.surfing = true;
		n.surfChange = p.level().getGameTime();
		SilverSurfer.save(p, n);
		if (!p.getAbilities().mayfly) {
			GRANTED.add(p.getUUID());
			p.getAbilities().mayfly = true;
		}
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
		if (p.onGround()) {
			com.projecthero.mod.hero.power.AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.6, 0));
		}
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(SilverSurfer.SILVER_BIG, p.getX(), p.getY(), p.getZ(), 20, 0.6, 0.1, 0.6, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 12, 0.5, 0.1, 0.5, 0.05);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f, 1.7f);
		return true;
	}

	/** Off the board, giving back exactly the flight it lent. */
	public static void stop(ServerPlayer p, boolean fx) {
		SilverSurferState s = SilverSurfer.peek(p);
		if (s != null && (s.surfing || s.lightSpeedUntil != 0L)) {
			SilverSurferState n = s.copy();
			n.surfing = false;
			n.lightSpeedUntil = 0L;
			n.surfChange = p.level().getGameTime();
			SilverSurfer.save(p, n);
			if (fx) {
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
			}
		}
		RAMMED.remove(p.getUUID());
		if (GRANTED.remove(p.getUUID())) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			p.resetFallDistance();
		}
	}

	static void tick(ServerPlayer p, long now) {
		SilverSurferState s = SilverSurfer.peek(p);
		if (s == null || !s.surfing) {
			return;
		}
		if (!p.isAlive() || com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			stop(p, true);
			return;
		}
		if (s.lightSpeedUntil == 0L && FlightLanding.landed(p, s.surfChange)) {
			stop(p, true);
			return;
		}
		if (!p.getAbilities().flying && !p.onGround()) {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		ServerLevel level = (ServerLevel) p.level();
		if (s.lightSpeedUntil != 0L) {
			if (s.lightSpeedUntil <= now) {
				SilverSurferState n = s.copy();
				n.lightSpeedUntil = 0L;
				SilverSurfer.save(p, n);
				RAMMED.remove(p.getUUID());
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 2.0f);
			} else {
				ramAlongPath(p, level);
			}
		}
		if (now % 2 == 0) {
			// the silver streak is drawn by every client; the server adds the cosmic sparkle others see far off
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() - 0.1, p.getZ(), 1, 0.3, 0.05, 0.3, 0.0);
		}
		if (now % 40 == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.3f, 2.0f);
		}
	}

	/** Light-Speed: everything he blazes through takes a hit (once per burst), bosses only the capped hit. */
	private static void ramAlongPath(ServerPlayer p, ServerLevel level) {
		Vec3 v = p.getKnownMovement();
		Vec3 to = p.position();
		Vec3 from = to.subtract(v.lengthSqr() > 64.0 ? v.normalize().scale(8.0) : v);
		AABB box = new AABB(from, to).inflate(1.5).expandTowards(0, p.getBbHeight(), 0);
		Set<Integer> hit = RAMMED.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
			if (hit.add(e.getId())) {
				SilverSurferCombat.strike(p, e, from, SilverSurferConfig.LIGHT_SPEED_RAM_DAMAGE, 1.2, 0.3);
			}
		}
	}

	/** Shift+X: the 150 b/s burst. Starts surfing first if he is not on the board. */
	static boolean lightSpeed(ServerPlayer p) {
		if (!SilverSurfer.state(p).surfing && !start(p)) {
			SilverSurfer.say(p, "message.projecthero.silver_surfer.needs_board", ChatFormatting.GRAY);
			return false;
		}
		long now = p.level().getGameTime();
		SilverSurferState n = SilverSurfer.state(p).copy();
		n.lightSpeedUntil = now + SilverSurferConfig.LIGHT_SPEED_TICKS;
		n.surfChange = now;
		SilverSurfer.save(p, n);
		RAMMED.remove(p.getUUID());
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1, 0);
		// the sonic-boom ring as he breaks into Light-Speed
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		Vec3 look = p.getLookAngle();
		Vec3 u = look.cross(new Vec3(0, 1, 0));
		if (u.lengthSqr() < 1.0e-4) {
			u = new Vec3(1, 0, 0);
		}
		u = u.normalize();
		Vec3 w = u.cross(look).normalize();
		for (int i = 0; i < 32; i++) {
			double a = Math.PI * 2 * i / 32;
			Vec3 q = c.add(u.scale(Math.cos(a) * 2.2)).add(w.scale(Math.sin(a) * 2.2));
			level.sendParticles(SilverSurfer.SILVER_BIG, q.x, q.y, q.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0.02);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0f, 0.6f);
		com.projecthero.mod.hero.power.AbilityHelpers.launchSelf(p, look.scale(SilverSurferConfig.LIGHT_SPEED_BPS / 20.0));
		return true;
	}
}
