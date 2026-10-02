package com.projecthero.mod.maxsteel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;
import com.projecthero.mod.maxsteel.entity.TurboCannonBeamEntity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 6 -- Turbo Cannon. v0.14.2 rework: the suit forms a T.U.R.B.O. arm cannon over the right forearm (the
 * client materialises it pixel by pixel), braces, and charges for up to {@link MaxSteelConfig#CANNON_MAX_CHARGE_TICKS}.
 * While charging it <b>locks on</b> to the enemy nearest the crosshair (inside {@link MaxSteelConfig#CANNON_LOCK_CONE_DEGREES},
 * with line of sight; the lock holds out to {@link MaxSteelConfig#CANNON_LOCK_KEEP_DEGREES}). On release it fires a
 * piercing beam from the muzzle -- at the locked target if there is one, otherwise exactly where the crosshair points --
 * that hits everything along it and detonates where it stops. Damage, beam width, blast radius and cost all climb
 * with the charge. Usable in every mode; the old v0.6 "fling yourself as a living projectile" launch is gone.
 *
 * <p>Server-authoritative: the charge clock, the lock, the aim, the hits and the cost are all decided here; the
 * client only reports press and release, and draws what {@link MaxSteelFx} and the beam entity say.
 */
public final class MaxSteelCannon {
	public static final String ABILITY = "turbo_cannon";

	private static final Map<UUID, Long> CHARGE_START = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> LOCK = new ConcurrentHashMap<>();

	private MaxSteelCannon() {
	}

	public static void clearSessionState() {
		CHARGE_START.clear();
		LOCK.clear();
	}

	public static boolean isCharging(ServerPlayer player) {
		return CHARGE_START.containsKey(player.getUUID());
	}

	public static float chargeFraction(ServerPlayer player) {
		Long start = CHARGE_START.get(player.getUUID());
		if (start == null) {
			return 0f;
		}
		return Math.min(1f, (float) (player.level().getGameTime() - start) / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
	}

	/** The locked target, or null. */
	public static LivingEntity lockedTarget(ServerPlayer player) {
		Integer id = LOCK.get(player.getUUID());
		return id != null && player.level().getEntity(id) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	// ---------------- charge ----------------

	public static boolean beginCharge(ServerPlayer player) {
		if (!MaxSteel.isTransformed(player) || isCharging(player)) {
			return false;
		}
		if (!MaxSteel.abilityReady(player, ABILITY)) {
			MaxSteelFeedback.onCooldown(player, ABILITY, MaxSteel.cooldownRemaining(player, ABILITY));
			return false;
		}
		if (MaxSteelEnergy.get(player) < MaxSteelConfig.CANNON_MIN_COST) {
			MaxSteelFeedback.noEnergy(player, MaxSteelConfig.CANNON_MIN_COST);
			return false;
		}
		long now = player.level().getGameTime();
		CHARGE_START.put(player.getUUID(), now);
		LOCK.remove(player.getUUID());
		MaxSteelVisuals.cannonCharge(player, now, -1);
		MaxSteelStealth.onOffensiveAction(player);
		AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.4f);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 0.7f);
		return true;
	}

	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		Long start = CHARGE_START.get(id);
		if (start == null) {
			return;
		}
		long held = player.level().getGameTime() - start;
		float frac = Math.min(1f, (float) held / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
		ServerLevel level = player.serverLevel();

		// brace: planted, but still able to shuffle and turn
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false, false));

		if (held % 2 == 0) {
			updateLock(player);
		}
		LivingEntity target = lockedTarget(player);
		MaxSteelVisuals.cannonCharge(player, start, target != null ? target.getId() : -1);

		// energy spiralling in to the muzzle, tighter and brighter as it builds
		Vec3 muzzle = muzzle(player);
		DustParticleOptions ember = new DustParticleOptions(new org.joml.Vector3f(0.25f, 0.66f, 1.0f), 0.8f + frac);
		for (int k = 0; k < 2; k++) {
			double a = (held * 0.7 + k * Math.PI);
			double r = 1.1 - 0.8 * frac;
			Vec3 p = muzzle.add(Math.cos(a) * r, Math.sin(a * 1.3) * r * 0.6, Math.sin(a) * r);
			Vec3 v = muzzle.subtract(p).scale(0.25);
			// everyone but the pilot: from behind the muzzle it only clutters their own aim
			for (ServerPlayer viewer : level.players()) {
				if (viewer != player && viewer.distanceToSqr(player) < 64 * 64) {
					level.sendParticles(viewer, ember, false, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
				}
			}
		}
		if (held % 3 == 0) {
			for (ServerPlayer viewer : level.players()) {
				if (viewer != player && viewer.distanceToSqr(player) < 64 * 64) {
					level.sendParticles(viewer, ParticleTypes.ELECTRIC_SPARK, false, muzzle.x, muzzle.y, muzzle.z,
							1 + (int) (frac * 3), 0.08, 0.08, 0.08, 0.05);
				}
			}
		}
		// a rising cue at a third, two thirds and full
		if (held == MaxSteelConfig.CANNON_MAX_CHARGE_TICKS / 3 || held == MaxSteelConfig.CANNON_MAX_CHARGE_TICKS * 2 / 3) {
			AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.6f, held < MaxSteelConfig.CANNON_MAX_CHARGE_TICKS / 2 ? 1.6f : 1.9f);
		} else if (held == MaxSteelConfig.CANNON_MAX_CHARGE_TICKS) {
			AbilityHelpers.sound(player, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9f, 1.3f);
			player.displayClientMessage(Component.translatable("message.projecthero.max_steel.cannon_full")
					.withStyle(net.minecraft.ChatFormatting.AQUA), true);
		}
		if (held == 4 || held == MaxSteelConfig.CANNON_MAX_CHARGE_TICKS / 2) {
			// the hum of the cannon forming / spinning up
			AbilityHelpers.sound(player, SoundEvents.CONDUIT_AMBIENT_SHORT, 0.8f, 1.2f + frac * 0.5f);
		}
		if (held >= MaxSteelConfig.CANNON_MAX_CHARGE_TICKS + MaxSteelConfig.CANNON_OVERHOLD_TICKS) {
			release(player, 1f); // held well past full: it fires on its own
		}
	}

	/**
	 * Picks / keeps the lock: the living enemy with the smallest angle off the look vector inside the lock cone (or
	 * the keep cone for the current lock), in range, with an unobstructed line from the eyes.
	 */
	private static void updateLock(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		LivingEntity current = lockedTarget(player);
		if (current != null && angleTo(eye, look, current) <= MaxSteelConfig.CANNON_LOCK_KEEP_DEGREES
				&& current.distanceTo(player) <= MaxSteelConfig.CANNON_RANGE && hasLineOfSight(player, current)) {
			return;
		}
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.hostilesAround(player, eye, MaxSteelConfig.CANNON_RANGE)) { // v0.14.20: lock-on, rule 2
			if (!e.isAlive() || e.isInvisible() || e.isSpectator()) {
				continue;
			}
			double angle = angleTo(eye, look, e);
			if (angle > MaxSteelConfig.CANNON_LOCK_CONE_DEGREES || !hasLineOfSight(player, e)) {
				continue;
			}
			double score = angle + e.distanceTo(player) * 0.05; // nearer the crosshair first, then nearer the pilot
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		if (best != null) {
			if (current == null || current != best) {
				AbilityHelpers.sound(player, SoundEvents.NOTE_BLOCK_BIT.value(), 0.5f, 2.0f);
			}
			LOCK.put(player.getUUID(), best.getId());
		} else {
			LOCK.remove(player.getUUID());
		}
	}

	private static double angleTo(Vec3 eye, Vec3 look, Entity e) {
		Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
		double len = to.length();
		if (len < 1.0e-4) {
			return 0.0;
		}
		double dot = Math.max(-1.0, Math.min(1.0, to.scale(1.0 / len).dot(look)));
		return Math.toDegrees(Math.acos(dot));
	}

	private static boolean hasLineOfSight(ServerPlayer player, Entity e) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, e.getBoundingBox().getCenter(),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	// ---------------- release / fire ----------------

	public static void release(ServerPlayer player, float requestedFrac) {
		UUID id = player.getUUID();
		Long start = CHARGE_START.remove(id);
		if (start == null) {
			return;
		}
		LivingEntity target = lockedTarget(player);
		LOCK.remove(id);
		MaxSteelVisuals.cannonCharge(player, 0L, -1);
		long held = player.level().getGameTime() - start;
		if (held < MaxSteelConfig.CANNON_MIN_CHARGE_TICKS) {
			// let go before it formed: the cannon folds away again, nothing fired, nothing spent
			AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.4f, 1.8f);
			player.displayClientMessage(Component.translatable("message.projecthero.max_steel.cannon_too_short"), true);
			return;
		}
		float frac = Math.max(0f, Math.min(1f, Math.min(requestedFrac,
				(float) held / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS)));
		float available = MaxSteelEnergy.get(player);
		if (available < MaxSteelConfig.CANNON_MIN_COST) {
			MaxSteelFeedback.noEnergy(player, MaxSteelConfig.CANNON_MIN_COST);
			return;
		}
		// the pool has to be able to pay for the charge it fires at
		float affordable = available >= MaxSteelConfig.CANNON_FULL_COST ? 1f
				: (available - MaxSteelConfig.CANNON_MIN_COST) / (MaxSteelConfig.CANNON_FULL_COST - MaxSteelConfig.CANNON_MIN_COST);
		frac = Math.min(frac, Math.max(0f, affordable));
		MaxSteelEnergy.spend(player, lerp(MaxSteelConfig.CANNON_MIN_COST, MaxSteelConfig.CANNON_FULL_COST, frac));
		MaxSteelEnergy.markCombat(player);
		MaxSteel.triggerCooldown(player, ABILITY, MaxSteelConfig.CANNON_COOLDOWN_TICKS);
		fire(player, frac, target);
	}

	/** The cannon's muzzle: just past the right hand, which the brace pose holds out along the look direction. */
	public static Vec3 muzzle(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-6) {
			double yaw = Math.toRadians(player.getYRot());
			right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		}
		right = right.normalize();
		return player.getEyePosition().add(look.scale(1.1)).add(right.scale(0.36)).add(0, -0.38, 0);
	}

	private static void fire(ServerPlayer player, float frac, LivingEntity target) {
		ServerLevel level = player.serverLevel();
		Vec3 muzzle = muzzle(player);
		Vec3 aim;
		if (target != null && target.isAlive() && hasLineOfSight(player, target)) {
			aim = target.getBoundingBox().getCenter();
		} else {
			Vec3 eye = player.getEyePosition();
			Vec3 far = eye.add(player.getLookAngle().scale(MaxSteelConfig.CANNON_RANGE));
			BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			aim = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
		}
		Vec3 dir = aim.subtract(muzzle);
		if (dir.lengthSqr() < 1.0e-6) {
			dir = player.getLookAngle();
		}
		dir = dir.normalize();
		Vec3 far = muzzle.add(dir.scale(MaxSteelConfig.CANNON_RANGE));
		BlockHitResult wall = level.clip(new ClipContext(muzzle, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation();
		double width = lerp((float) MaxSteelConfig.CANNON_BEAM_WIDTH_MIN, (float) MaxSteelConfig.CANNON_BEAM_WIDTH_MAX, frac);
		float damage = lerp(MaxSteelConfig.CANNON_MIN_DAMAGE, MaxSteelConfig.CANNON_FULL_DAMAGE, frac);

		// everything the beam passes through, nearest first; the beam stops early only at a wall
		List<LivingEntity> struck = new ArrayList<>();
		AABB sweep = new AABB(muzzle, end).inflate(width + 1.0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, muzzle, MaxSteelConfig.CANNON_RANGE + 2)) {
			if (!e.getBoundingBox().intersects(sweep)) {
				continue;
			}
			if (e.getBoundingBox().inflate(width).clip(muzzle, end).isPresent() || e.getBoundingBox().inflate(width).contains(muzzle)) {
				struck.add(e);
			}
		}
		struck.sort((a, b) -> Double.compare(a.distanceToSqr(muzzle), b.distanceToSqr(muzzle)));
		for (LivingEntity e : struck) {
			if (AbilityHelpers.hurtBurst(player, e, damage)) {
				AbilityHelpers.push(e, dir.scale(0.6 + 0.9 * frac).add(0, 0.25, 0));
				level.sendParticles(ParticleTypes.FLASH, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0, 0, 0, 0);
			}
		}

		// the detonation where it stops: entity-only, no terrain damage
		double radius = lerp((float) MaxSteelConfig.CANNON_AOE_RADIUS_MIN, (float) MaxSteelConfig.CANNON_AOE_RADIUS_MAX, frac);
		float aoe = damage * MaxSteelConfig.CANNON_AOE_FRACTION;
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, end, radius)) {
			if (struck.contains(e)) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(player, e, aoe)) {
				AbilityHelpers.knockbackFrom(e, end, 0.8 + frac);
			}
		}
		level.sendParticles(frac > 0.6f ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 24 + (int) (frac * 30), radius * 0.4, radius * 0.4, radius * 0.4, 0.3);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, end.x, end.y, end.z, 10 + (int) (frac * 20), radius * 0.3, radius * 0.3, radius * 0.3, 0.08);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f + frac * 0.6f, 1.1f - frac * 0.3f);

		// the shot itself
		TurboCannonBeamEntity.spawn(level, muzzle, end, width, frac);
		level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2f, 0.6f);
		if (frac >= 0.95f) {
			level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.3f);
		} else {
			level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
		}

		// recoil: shoves the pilot back -- Strength Mode plants its feet
		if (MaxSteel.mode(player) != MaxSteelMode.STRENGTH) {
			AbilityHelpers.addImpulse(player, dir.scale(-MaxSteelConfig.CANNON_RECOIL * (0.35 + 0.65 * frac)).add(0, 0.08, 0));
		}
		MaxSteelVisuals.play(player, MaxSteelFx.ANIM_CANNON);
	}

	/** Suit-down / death / logout / power loss: drop a charge in progress. */
	public static void cancel(ServerPlayer player) {
		CHARGE_START.remove(player.getUUID());
		LOCK.remove(player.getUUID());
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
