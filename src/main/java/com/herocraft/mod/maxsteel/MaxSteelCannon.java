package com.herocraft.mod.maxsteel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 6 -- Turbo Cannon Mode. The signature finisher: brace and charge for up to
 * {@link MaxSteelConfig#CANNON_MAX_CHARGE_TICKS} (v0.9.2: a full 5 seconds), then launch the player
 * forward as a living projectile. Instant-cast hits for {@link MaxSteelConfig#CANNON_MIN_DAMAGE};
 * every extra second held adds {@link MaxSteelConfig#CANNON_PER_SECOND_DAMAGE} up to a 5-second cap,
 * and the energy cost scales with the charge the same way. On impact (a solid wall or an entity) the
 * flight ends with direct + AoE damage. The pilot never takes their own impact damage. Terrain is not
 * destroyed.
 *
 * <p>All server-authoritative: the charge, the cost, the launch, and every collision/damage decision
 * are made here -- the client only asks to charge and to release.
 */
public final class MaxSteelCannon {
	private static final Map<UUID, Long> CHARGE_START = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> FLIGHT_TICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FLIGHT_DAMAGE = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

	private MaxSteelCannon() {
	}

	public static void clearSessionState() {
		CHARGE_START.clear();
		FLIGHT_TICKS.clear();
		FLIGHT_DAMAGE.clear();
		IN_FLIGHT.clear();
	}

	public static boolean isCharging(ServerPlayer player) {
		return CHARGE_START.containsKey(player.getUUID());
	}

	public static boolean isInFlight(Player player) {
		return IN_FLIGHT.contains(player.getUUID());
	}

	public static float chargeFraction(ServerPlayer player) {
		Long start = CHARGE_START.get(player.getUUID());
		if (start == null) {
			return 0f;
		}
		return Math.min(1f, (float) (player.level().getGameTime() - start) / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
	}

	// ---------------- charge ----------------

	public static boolean beginCharge(ServerPlayer player) {
		if (!MaxSteel.isTransformed(player) || isCharging(player) || isInFlight(player)) {
			return false;
		}
		if (!MaxSteel.abilityReady(player, "turbo_cannon")) {
			return false;
		}
		if (MaxSteelEnergy.get(player) < MaxSteelConfig.CANNON_MIN_COST) {
			MaxSteelFeedback.noEnergy(player, MaxSteelConfig.CANNON_MIN_COST);
			return false;
		}
		CHARGE_START.put(player.getUUID(), player.level().getGameTime());
		player.setAttached(ModAttachments.MAX_STEEL_CANNON_CHARGE, 1);
		MaxSteelStealth.onOffensiveAction(player);
		AbilityHelpers.sound(player, SoundEvents.CONDUIT_ACTIVATE, 0.7f, 0.8f);
		return true;
	}

	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();

		if (CHARGE_START.containsKey(id)) {
			float frac = chargeFraction(player);
			// keep the client HUD charge bar in step with how much of the 5 s window is left
			player.setAttached(ModAttachments.MAX_STEEL_CANNON_CHARGE,
					(int) (player.level().getGameTime() - CHARGE_START.get(id)));
			// brace: heavy slowness while charging
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 3, false, false, false));
			if (player.tickCount % 2 == 0) {
				ServerLevel level = player.serverLevel();
				double r = 0.6 + frac * 1.2;
				double a = player.tickCount * 0.6;
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
						player.getX() + Math.cos(a) * r, player.getY() + 0.6 + frac, player.getZ() + Math.sin(a) * r,
						1, 0.02, 0.02, 0.02, 0.0);
			}
			if (frac >= 1f && (player.level().getGameTime() - CHARGE_START.get(id)) > MaxSteelConfig.CANNON_MAX_CHARGE_TICKS + 10) {
				release(player, 1f); // auto-fire if held well past full
			}
			return;
		}

		if (IN_FLIGHT.contains(id)) {
			tickFlight(player);
		}
	}

	// ---------------- release / flight ----------------

	public static void release(ServerPlayer player, float chargeFrac) {
		if (CHARGE_START.remove(player.getUUID()) == null) {
			return;
		}
		player.setAttached(ModAttachments.MAX_STEEL_CANNON_CHARGE, 0);
		chargeFrac = Math.max(0f, Math.min(1f, chargeFrac));
		float available = MaxSteelEnergy.get(player);
		if (available < MaxSteelConfig.CANNON_MIN_COST) {
			MaxSteelFeedback.noEnergy(player, MaxSteelConfig.CANNON_MIN_COST);
			return;
		}
		// v0.9.2: the longer the charge the more of the pool it burns -- cap the effective charge to
		// what the pool can actually pay for so a nuke can never fire for free.
		float affordableFrac = available >= MaxSteelConfig.CANNON_FULL_COST ? 1f
				: (available - MaxSteelConfig.CANNON_MIN_COST)
						/ (MaxSteelConfig.CANNON_FULL_COST - MaxSteelConfig.CANNON_MIN_COST);
		chargeFrac = Math.min(chargeFrac, Math.max(0f, affordableFrac));
		float cost = lerp(MaxSteelConfig.CANNON_MIN_COST, MaxSteelConfig.CANNON_FULL_COST, chargeFrac);
		MaxSteelEnergy.spend(player, cost);
		MaxSteelEnergy.markCombat(player);
		MaxSteel.triggerCooldown(player, "turbo_cannon", MaxSteelConfig.CANNON_COOLDOWN_TICKS);

		// Cannon overrides the active mode; the suit returns to Base after impact.
		MaxSteelModes.exitToBase(player, false);

		UUID id = player.getUUID();
		IN_FLIGHT.add(id);
		FLIGHT_TICKS.put(id, MaxSteelConfig.CANNON_MAX_FLIGHT_TICKS);
		FLIGHT_DAMAGE.put(id, lerp(MaxSteelConfig.CANNON_MIN_DAMAGE, MaxSteelConfig.CANNON_FULL_DAMAGE, chargeFrac));

		Vec3 dir = player.getLookAngle().normalize();
		AbilityHelpers.launchSelf(player, dir.scale(MaxSteelConfig.CANNON_LAUNCH_SPEED));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MaxSteelConfig.CANNON_MAX_FLIGHT_TICKS + 5,
				4, false, false, false));

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.8, player.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	private static void tickFlight(ServerPlayer player) {
		UUID id = player.getUUID();
		int left = FLIGHT_TICKS.merge(id, -1, Integer::sum);
		ServerLevel level = player.serverLevel();

		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.9, player.getZ(),
				4, 0.2, 0.3, 0.2, 0.01);
		// keep driving forward at speed (defeat drag) unless we hit something
		Vec3 v = player.getDeltaMovement();
		boolean hitWall = player.horizontalCollision || player.verticalCollision;

		LivingEntity struck = null;
		for (LivingEntity e : AbilityHelpers.living(level, player.position().add(0, player.getBbHeight() * 0.5, 0),
				1.4, e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
			struck = e;
			break;
		}

		if (hitWall || struck != null || left <= 0) {
			impact(player, struck);
			return;
		}
		if (v.lengthSqr() < MaxSteelConfig.CANNON_LAUNCH_SPEED * MaxSteelConfig.CANNON_LAUNCH_SPEED * 0.4) {
			// re-assert speed so drag doesn't stall the cannonball
			player.setDeltaMovement(v.normalize().scale(MaxSteelConfig.CANNON_LAUNCH_SPEED * 0.9));
			player.hurtMarked = true;
		}
	}

	private static void impact(ServerPlayer player, LivingEntity directHit) {
		UUID id = player.getUUID();
		IN_FLIGHT.remove(id);
		FLIGHT_TICKS.remove(id);
		Float stored = FLIGHT_DAMAGE.remove(id);
		float direct = stored != null ? stored : MaxSteelConfig.CANNON_MIN_DAMAGE;

		ServerLevel level = player.serverLevel();
		Vec3 centre = player.position().add(0, player.getBbHeight() * 0.5, 0);
		DamageSource src = level.damageSources().mobProjectile(player, player);

		if (directHit != null) {
			directHit.hurt(src, direct);
			AbilityHelpers.knockbackFrom(directHit, player.position(), 2.0);
		}
		float aoe = MaxSteelConfig.CANNON_AOE_DAMAGE * (direct / MaxSteelConfig.CANNON_FULL_DAMAGE);
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, centre, MaxSteelConfig.CANNON_AOE_RADIUS)) {
			if (e == directHit) {
				continue;
			}
			e.hurt(src, aoe);
			AbilityHelpers.knockbackFrom(e, centre, 1.4);
		}

		player.setDeltaMovement(player.getDeltaMovement().scale(0.1));
		player.hurtMarked = true;
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
		level.playSound(null, centre.x, centre.y, centre.z,
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	// ---------------- self-damage immunity ----------------

	static void beginFlight(ServerPlayer player) {
		IN_FLIGHT.add(player.getUUID());
	}

	static void endFlight(ServerPlayer player) {
		UUID id = player.getUUID();
		IN_FLIGHT.remove(id);
		FLIGHT_TICKS.remove(id);
		FLIGHT_DAMAGE.remove(id);
		CHARGE_START.remove(id);
		player.setAttached(ModAttachments.MAX_STEEL_CANNON_CHARGE, 0);
	}

	/** True when {@code source} is this player's own Turbo Cannon impact -- no self damage. */
	public static boolean isOwnCannonDamage(Player player, DamageSource source) {
		if (!IN_FLIGHT.contains(player.getUUID())) {
			return false;
		}
		return source.getEntity() == null || source.getEntity() == player || source.getDirectEntity() == player;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
