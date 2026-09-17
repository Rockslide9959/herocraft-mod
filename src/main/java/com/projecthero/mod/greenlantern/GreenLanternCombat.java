package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Ring Bolt (R) / Continuous Beam (Shift+R) and Construct Fist (G) / War Hammer Slam (Shift+G).
 *
 * <p>Ring Bolt and Construct Fist are server-authoritative hit-scans (raycast + instant damage) with a
 * particle trail, rather than travelling projectile entities -- the same pattern most of the mod's
 * ranged abilities already use (e.g. Shadow Bolt). This is a deliberate simplification: it forgoes a
 * dodgeable travel time in exchange for not needing a new {@code EntityType} + client renderer, and the
 * spec explicitly allows a functional equivalent where a bespoke asset/engine feature is not essential.
 */
public final class GreenLanternCombat {
	private static final String BOLT_CD = "ring_bolt";
	private static final String FIST_CD = "construct_fist";
	private static final String HAMMER_CD = "war_hammer_slam";
	private static final String BEAM_CD = "continuous_beam";

	/** Player UUID -> ticks the beam has been channelling this activation (absent = not channelling). */
	private static final Map<UUID, Integer> BEAM_CHANNEL = new ConcurrentHashMap<>();

	/** Lantern-Corps green, matching {@code GreenLanternHud}'s bar colour (0x35F075). */
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.6f);

	private GreenLanternCombat() {
	}

	/**
	 * The player's live right-hand vector (perpendicular to both look direction and world-up),
	 * falling back to a body-yaw-derived right vector when looking straight up/down (where
	 * {@code look x up} degenerates to zero).
	 */
	private static Vec3 rightVector(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-4) {
			double yawRad = Math.toRadians(player.getYRot());
			right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
		}
		return right.normalize();
	}

	/**
	 * Where Ring Bolt/Continuous Beam's drawn line starts -- offset down and to the side from the eye,
	 * roughly at hand height, so the beam's own particles don't render right in front of the camera
	 * (v0.11.2: "so they don't get blinded"). Hit detection is unaffected -- {@link #ringBolt} and
	 * {@link #beamTick} still raycast from the eye via {@link AbilityHelpers#raycastEntity}, so the bolt
	 * still lands exactly on the crosshair; only the cosmetic line's start point moves.
	 */
	private static Vec3 handOrigin(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		return eye.add(player.getLookAngle().scale(0.5)).add(rightVector(player).scale(0.35)).add(0.0, -0.45, 0.0);
	}

	/**
	 * A wireframe cube of particles at {@code center} -- Construct Fist's hard-light "model" (v0.11.2).
	 * Cheap and asset-free (reuses {@link AbilityHelpers#line}'s existing particle-line drawing) but
	 * reads as a fist-sized solid shape rather than a shapeless burst, consistent with every other
	 * Green Lantern effect being particle-driven rather than a spawned entity/model (see
	 * docs/GREENLANTERN_REFERENCE.md's deliberate-simplifications list).
	 */
	private static void drawFistShape(ServerLevel level, Vec3 center) {
		double h = 0.35;
		Vec3[] c = {
				center.add(-h, -h, -h), center.add(h, -h, -h), center.add(h, -h, h), center.add(-h, -h, h),
				center.add(-h, h, -h), center.add(h, h, -h), center.add(h, h, h), center.add(-h, h, h),
		};
		int[][] edges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0},
				{4, 5}, {5, 6}, {6, 7}, {7, 4},
				{0, 4}, {1, 5}, {2, 6}, {3, 7},
		};
		for (int[] e : edges) {
			AbilityHelpers.line(level, c[e[0]], c[e[1]], GREEN_DUST, 6.0);
		}
	}

	/**
	 * A hammer silhouette (a mallet-head bar crossing the swing, plus a handle rising from the impact
	 * point) in particles -- War Hammer Slam's equivalent of {@link #drawFistShape}.
	 */
	private static void drawHammerShape(ServerLevel level, Vec3 center, Vec3 right) {
		double headHalf = 0.7;
		Vec3 headA = center.add(right.scale(-headHalf)).add(0, 0.55, 0);
		Vec3 headB = center.add(right.scale(headHalf)).add(0, 0.55, 0);
		AbilityHelpers.line(level, headA, headB, GREEN_DUST, 8.0);
		AbilityHelpers.line(level, headA.add(0, 0.3, 0), headB.add(0, 0.3, 0), GREEN_DUST, 8.0);
		AbilityHelpers.line(level, center.add(0, 0.55, 0), center.add(0, 1.7, 0), GREEN_DUST, 6.0);
	}

	public static void clearSessionState() {
		BEAM_CHANNEL.clear();
	}

	public static void onCleanup(UUID playerId) {
		BEAM_CHANNEL.remove(playerId);
	}

	// ---------------- Ring Bolt ----------------

	public static void ringBolt(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, BOLT_CD)) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.BOLT_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, BOLT_CD, GreenLanternConfig.BOLT_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.BOLT_RANGE);
		Vec3 endPoint = AbilityHelpers.aimPoint(player, GreenLanternConfig.BOLT_RANGE);
		AbilityHelpers.line(level, handOrigin(player), endPoint, ParticleTypes.HAPPY_VILLAGER, 4.0);
		if (target != null) {
			AbilityHelpers.hurt(player, target, GreenLanternConfig.BOLT_DAMAGE);
			AbilityHelpers.knockbackFrom(target, player.position(), GreenLanternConfig.BOLT_KNOCKBACK);
		}
		AbilityHelpers.sound(player, SoundEvents.GUARDIAN_ATTACK, 0.5f, 1.8f);
	}

	// ---------------- Continuous Beam ----------------

	public static boolean isChannellingBeam(ServerPlayer player) {
		return BEAM_CHANNEL.containsKey(player.getUUID());
	}

	public static void beamStart(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, BEAM_CD) || isChannellingBeam(player)) {
			return;
		}
		if (!GreenLanternEnergy.canSpend(player, GreenLanternConfig.BEAM_COST_PER_TICK)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		BEAM_CHANNEL.put(player.getUUID(), 0);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, movementPenaltyAmplifier(), false, false, false));
		AbilityHelpers.sound(player, SoundEvents.GUARDIAN_ATTACK, 0.5f, 0.7f);
	}

	public static void beamStop(ServerPlayer player) {
		BEAM_CHANNEL.remove(player.getUUID());
	}

	/** Called every server tick for a channelling player. */
	public static void beamTick(ServerPlayer player) {
		Integer ticks = BEAM_CHANNEL.get(player.getUUID());
		if (ticks == null) {
			return;
		}
		if (ticks >= GreenLanternConfig.BEAM_MAX_CHANNEL_TICKS) {
			beamStop(player);
			GreenLantern.triggerCooldown(player, BEAM_CD, GreenLanternConfig.BEAM_FORCED_COOLDOWN_TICKS);
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, movementPenaltyAmplifier(), false, false, false));
		if (ticks % GreenLanternConfig.BEAM_TICK_INTERVAL == 0) {
			if (!GreenLanternEnergy.drainTick(player, GreenLanternConfig.BEAM_COST_PER_TICK)) {
				beamStop(player);
				GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
				return;
			}
			GreenLanternBattery.onAbilityUsed(player);
			LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.BEAM_RANGE);
			if (target != null) {
				AbilityHelpers.hurt(player, target, GreenLanternConfig.BEAM_DAMAGE_PER_TICK);
			}
			// A periodic zap on the same cadence as the damage tick -- not a true seamless loop, but
			// enough to read as a sustained energy weapon rather than silent channelling.
			AbilityHelpers.sound(player, SoundEvents.GUARDIAN_ATTACK, 0.35f, 0.9f);
		}
		ServerLevel level = player.serverLevel();
		Vec3 end = AbilityHelpers.aimPoint(player, GreenLanternConfig.BEAM_RANGE);
		AbilityHelpers.line(level, handOrigin(player), end, ParticleTypes.HAPPY_VILLAGER, 3.0);
		BEAM_CHANNEL.put(player.getUUID(), ticks + 1);
	}

	/** ~20% movement slowdown while channelling -- Slowness amplifier that lands near a 20% speed cut. */
	private static int movementPenaltyAmplifier() {
		return 0; // Slowness I (~15%) is the closest whole-amplifier approximation to a flat 20% multiplier.
	}

	// ---------------- Construct Fist / War Hammer Slam ----------------

	public static void constructFist(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, FIST_CD)) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.FIST_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, FIST_CD, GreenLanternConfig.FIST_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		Vec3 aim = AbilityHelpers.aimPoint(player, GreenLanternConfig.FIST_RANGE);
		AbilityHelpers.line(level, handOrigin(player), aim, GREEN_DUST, 5.0);
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.FIST_RANGE);
		Vec3 impact = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : aim;
		drawFistShape(level, impact);
		if (target != null) {
			AbilityHelpers.hurt(player, target, GreenLanternConfig.FIST_DAMAGE);
			AbilityHelpers.knockbackFrom(target, player.position(), GreenLanternConfig.FIST_KNOCKBACK);
		}
		AbilityHelpers.sound(player, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 1.1f);
	}

	public static void warHammerSlam(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, HAMMER_CD)) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.HAMMER_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, HAMMER_CD, GreenLanternConfig.HAMMER_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		Vec3 center = player.position().add(player.getLookAngle().scale(2.0));
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, center, GreenLanternConfig.HAMMER_RADIUS)) {
			double dist = e.position().distanceTo(center);
			boolean isBoss = e.getMaxHealth() >= GreenLanternConfig.HAMMER_BOSS_MAX_HEALTH_THRESHOLD;
			float damage = dist <= 1.5 ? GreenLanternConfig.HAMMER_CENTER_DAMAGE : GreenLanternConfig.HAMMER_OUTER_DAMAGE;
			AbilityHelpers.hurt(player, e, damage);
			double knockback = isBoss ? 0.25 : 1.0; // bosses: full damage, only 25% normal knockback
			AbilityHelpers.knockbackFrom(e, center, knockback);
			e.setDeltaMovement(e.getDeltaMovement().x, GreenLanternConfig.HAMMER_KNOCKUP, e.getDeltaMovement().z);
			e.hurtMarked = true;
		}
		drawHammerShape(level, center, rightVector(player));
		AbilityHelpers.burst(level, center, GREEN_DUST, 40, GreenLanternConfig.HAMMER_RADIUS * 0.6);
		level.sendParticles(GREEN_DUST, center.x, center.y, center.z, 30, GreenLanternConfig.HAMMER_RADIUS,
				0.2, GreenLanternConfig.HAMMER_RADIUS, 0.05);
		AbilityHelpers.sound(player, SoundEvents.ANVIL_LAND, 1.0f, 0.6f);
	}
}
