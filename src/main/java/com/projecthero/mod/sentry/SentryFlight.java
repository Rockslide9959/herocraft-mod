package com.projecthero.mod.sentry;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.sentry.data.SentryAttachments;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Sentry: flight. v0.17.5 rework (user: "Make double tap space bar his flight ability and make it the same for the void power.
 * Make X just a dash ability for regular sentry"): <b>double-tap Space</b> takes off / lands ({@link #toggle}, sent by the client as a
 * {@code SentryActionPayload}; vanilla {@code mayfly}/{@code flying} so the server never kicks a hovering player, the movement itself the
 * client's shared directional flight: 22 b/s, 44 sprinting, Space up / Shift down). The same merged with the Void. <b>X</b> is only the
 * sonic-boom dash ({@link #press}): 18 blocks along his look in 8 ticks, hitting everything he passes once for 14 (ground or air; it never
 * starts flight). Flight ends on landing or another double-tap (the takeover flies the body itself).
 */
public final class SentryFlight {
	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	/** When flight last ended (a take-off right after is ignored: vanilla double-tap can drop flight the same tick). */
	private static final Map<UUID, Long> LAST_STOP = new ConcurrentHashMap<>();
	/** The dash: until when, and who it already hit (entity ids). */
	private static final Map<UUID, Long> DASH_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<Integer>> DASH_HIT = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> DASH_DIR = new ConcurrentHashMap<>();

	private SentryFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_STOP.clear();
		DASH_UNTIL.clear();
		DASH_HIT.clear();
		DASH_DIR.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
		LAST_STOP.remove(id);
		DASH_UNTIL.remove(id);
		DASH_HIT.remove(id);
		DASH_DIR.remove(id);
	}

	public static boolean dashing(ServerPlayer p) {
		return DASH_UNTIL.containsKey(p.getUUID());
	}

	/** X (regular Sentry): the dash on the ground; round 3 (user: "if sonic boom is used while flying it does something different"): the Flight Boost while flying. */
	static void press(ServerPlayer p) {
		if (Sentry.selfFlying(p)) {
			boost(p);
		} else {
			dash(p);
		}
	}

	/** The Flight Boost is running for him (client-safe: the state is synced; the client's directional flight reads it). */
	public static boolean boosting(Player p) {
		SentryState s = Sentry.peek(p);
		return s != null && s.hasPower && s.boostUntil > p.level().getGameTime();
	}

	/** Ticks of Flight Boost left (0 none). Client-safe. */
	public static int boostLeft(Player p) {
		SentryState s = Sentry.peek(p);
		return s == null ? 0 : (int) Math.max(0L, s.boostUntil - p.level().getGameTime());
	}

	/**
	 * Round 3: X while flying -- flight speed x1.5 for 30 s ({@link SentryConfig#BOOST_TICKS}; 33 b/s cruising, 66 sprinting), a sonic boom
	 * as it starts, a golden contrail meanwhile. Its own cooldown (45 s from the start). True if it started (tests).
	 */
	public static boolean boost(ServerPlayer p) {
		if (Sentry.cooldownRemaining(p, SentryAbilityManager.FLIGHT_BOOST) > 0 || !Sentry.spendSun(p, SentryConfig.BOOST_COST)) {
			return false;
		}
		long now = p.level().getGameTime();
		SentryState n = Sentry.state(p).copy();
		n.boostUntil = now + SentryConfig.BOOST_TICKS;
		Sentry.save(p, n);
		Sentry.startCooldown(p, SentryAbilityManager.FLIGHT_BOOST, SentryConfig.BOOST_COOLDOWN);
		Sentry.pose(p, SentryState.POSE_BOOST, 12);
		Vec3 dir = p.getLookAngle();
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		SentryFx.send(level, SentryFxPayload.BOOST, c, 3.5f, 16, c.add(dir), p.getId());
		SentryFx.send(level, SentryFxPayload.BOOM, c, 3f, 10, c.add(dir), p.getId());
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 2.0f), c.x, c.y, c.z, 30, 0.8, 0.8, 0.8, 0.0);
		Sentry.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
		Sentry.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f);
		AbilityHelpers.launchSelf(p, dir.scale(1.6));
		return true;
	}

	/** Double-tap Space: take off, or land. Never in the takeover (the Void flies the body). */
	public static void toggle(ServerPlayer p) {
		if (!Sentry.hasPower(p) || Sentry.inTakeover(p) || !p.isAlive()) {
			return;
		}
		if (Sentry.isFlying(p)) {
			stop(p);
			return;
		}
		Long stopped = LAST_STOP.get(p.getUUID());
		long now = p.level().getGameTime();
		if (stopped != null && now - stopped < SentryConfig.FLIGHT_TOGGLE_DEBOUNCE && now >= stopped) {
			return;
		}
		start(p);
	}

	static void start(ServerPlayer p) {
		if (p.isPassenger() || p.isFallFlying() || p.isSpectator() || Sentry.inTakeover(p)) {
			return;
		}
		SentryState n = Sentry.state(p).copy();
		n.flying = true;
		Sentry.save(p, n);
		STARTED.put(p.getUUID(), p.level().getGameTime());
		grantAbilities(p);
		p.resetFallDistance();
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(0, 0.6, 0));
		}
		ServerLevel level = p.serverLevel();
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.4f), p.getX(), p.getY() + 0.3, p.getZ(), 16, 0.4, 0.2, 0.4, 0.0);
		Sentry.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.4f, 1.8f);
	}

	static void grantAbilities(ServerPlayer p) {
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
	}

	public static void stop(ServerPlayer p) {
		SentryState s = p.getAttachedOrElse(SentryAttachments.STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			SentryState n = s.copy();
			n.flying = false;
			Sentry.save(p, n);
		}
		STARTED.remove(p.getUUID());
		if (was) {
			LAST_STOP.put(p.getUUID(), p.level().getGameTime());
		}
		if (was && !p.getAbilities().instabuild && !p.isSpectator() && !anyOtherFlightWants(p)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
	}

	/** The sonic-boom dash. */
	static void dash(ServerPlayer p) {
		if (Sentry.cooldownRemaining(p, SentryAbilityManager.DASH) > 0 || !Sentry.spendSun(p, SentryConfig.DASH_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		Vec3 dir = p.getLookAngle();
		DASH_UNTIL.put(p.getUUID(), now + 8);
		DASH_DIR.put(p.getUUID(), dir);
		DASH_HIT.put(p.getUUID(), new HashSet<>());
		Sentry.startCooldown(p, SentryAbilityManager.DASH, SentryConfig.DASH_COOLDOWN);
		ServerLevel level = p.serverLevel();
		SentryFx.send(level, SentryFxPayload.BOOM, p.position().add(0, 1.0, 0), 3f, 10, p.position().add(dir), p.getId());
		level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
		Sentry.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
	}

	static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		SentryState bs = Sentry.state(p);
		if (bs.boostUntil != 0L && bs.boostUntil <= now) {
			SentryState n = bs.copy();
			n.boostUntil = 0L;
			Sentry.save(p, n);
		} else if (bs.boostUntil > now && Sentry.selfFlying(p) && p.getDeltaMovement().lengthSqr() > 0.2) {
			// the golden contrail (the client draws the long ribbon; these are the sparks in it)
			Vec3 v = p.getDeltaMovement().normalize();
			Vec3 tail = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(v.scale(1.4));
			p.serverLevel().sendParticles(Sentry.dust(Sentry.GOLD, 1.6f), tail.x, tail.y, tail.z, 3, 0.15, 0.15, 0.15, 0.0);
			if (now % 3 == 0) {
				p.serverLevel().sendParticles(ParticleTypes.END_ROD, tail.x, tail.y, tail.z, 1, 0.1, 0.1, 0.1, 0.01);
			}
		}
		Long dashEnd = DASH_UNTIL.get(p.getUUID());
		if (dashEnd != null) {
			if (now >= dashEnd) {
				DASH_UNTIL.remove(p.getUUID());
				DASH_HIT.remove(p.getUUID());
				Vec3 d = DASH_DIR.remove(p.getUUID());
				if (d != null) {
					AbilityHelpers.launchSelf(p, d.scale(0.4));
				}
			} else {
				Vec3 d = DASH_DIR.getOrDefault(p.getUUID(), p.getLookAngle());
				AbilityHelpers.launchSelf(p, d.scale(SentryConfig.DASH_BLOCKS / 8.0));
				Set<Integer> hit = DASH_HIT.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
				for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.5),
						e -> Sentry.areaVictim(p, e) && !hit.contains(e.getId()))) {
					hit.add(e.getId());
					Sentry.hit(p, e, SentryConfig.DASH_DAMAGE, false);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
				}
				p.serverLevel().sendParticles(Sentry.dust(Sentry.GOLD, 1.6f), p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
			}
		}
		if (!Sentry.isFlying(p) || Sentry.inTakeover(p)) {
			return;
		}
		if (p.isSpectator() || p.isPassenger() || p.isFallFlying() || !p.isAlive()) {
			stop(p);
			return;
		}
		Long start = STARTED.computeIfAbsent(p.getUUID(), k -> now);
		if (com.projecthero.mod.flight.FlightLanding.landed(p, start) && dashEnd == null) {
			stop(p);
			return;
		}
		if (!p.getAbilities().flying || !p.getAbilities().mayfly) {
			if (now - start >= SentryConfig.FLIGHT_LIFTOFF_GRACE && !p.getAbilities().flying && p.getAbilities().mayfly) {
				stop(p); // the client dropped vanilla flight on its own: follow it
				return;
			}
			grantAbilities(p);
		}
		p.resetFallDistance();
		if (p.tickCount % 3 == 0 && p.getDeltaMovement().lengthSqr() > 0.3) {
			Vec3 v = p.getDeltaMovement().normalize();
			Vec3 tail = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(v.scale(1.2));
			p.serverLevel().sendParticles(Sentry.dust(Sentry.GOLD, 1.0f), tail.x, tail.y, tail.z, 2, 0.2, 0.2, 0.2, 0.0);
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
