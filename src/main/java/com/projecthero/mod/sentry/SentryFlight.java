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
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Sentry: flight. <b>X</b> toggles it (vanilla {@code mayfly}/{@code flying} so the server never kicks a hovering player, the
 * movement itself the client's shared directional flight: 22 b/s, 44 sprinting). <b>Double-tap X</b> is the sonic-boom dash: 18 blocks
 * along his look in 8 ticks, hitting everything he passes once for 14; the second tap puts flight back if the first one dropped it.
 * Flight ends on landing, on X, or in Void Form (the Void walks; the takeover flies the body itself).
 */
public final class SentryFlight {
	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_PRESS = new ConcurrentHashMap<>();
	/** The dash: until when, and who it already hit (entity ids). */
	private static final Map<UUID, Long> DASH_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<Integer>> DASH_HIT = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> DASH_DIR = new ConcurrentHashMap<>();

	private SentryFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_PRESS.clear();
		DASH_UNTIL.clear();
		DASH_HIT.clear();
		DASH_DIR.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
		LAST_PRESS.remove(id);
		DASH_UNTIL.remove(id);
		DASH_HIT.remove(id);
		DASH_DIR.remove(id);
	}

	public static boolean dashing(ServerPlayer p) {
		return DASH_UNTIL.containsKey(p.getUUID());
	}

	/** X: toggle, or (second tap within 7 ticks) the dash. */
	static void press(ServerPlayer p) {
		long now = p.level().getGameTime();
		Long last = LAST_PRESS.put(p.getUUID(), now);
		if (last != null && now - last <= SentryConfig.DOUBLE_TAP_TICKS && now >= last) {
			LAST_PRESS.remove(p.getUUID());
			if (!Sentry.isFlying(p)) {
				start(p);
			}
			dash(p);
			return;
		}
		if (Sentry.isFlying(p)) {
			stop(p);
		} else {
			start(p);
		}
	}

	static void start(ServerPlayer p) {
		if (p.isPassenger() || p.isFallFlying() || p.isSpectator() || Sentry.voidForm(p)) {
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
		if (p.isSpectator() || p.isPassenger() || p.isFallFlying() || !p.isAlive() || Sentry.voidForm(p)) {
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
