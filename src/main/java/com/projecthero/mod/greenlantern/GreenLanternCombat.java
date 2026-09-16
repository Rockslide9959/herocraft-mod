package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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

	private GreenLanternCombat() {
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
		GreenLanternEnergy.markAbilityUsed(player);
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, BOLT_CD, GreenLanternConfig.BOLT_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.BOLT_RANGE);
		Vec3 endPoint = AbilityHelpers.aimPoint(player, GreenLanternConfig.BOLT_RANGE);
		AbilityHelpers.line(level, player.getEyePosition(), endPoint, ParticleTypes.HAPPY_VILLAGER, 4.0);
		if (target != null) {
			AbilityHelpers.hurt(player, target, GreenLanternConfig.BOLT_DAMAGE);
			AbilityHelpers.knockbackFrom(target, player.position(), GreenLanternConfig.BOLT_KNOCKBACK);
		}
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.6f, 1.6f);
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
			GreenLanternEnergy.markAbilityUsed(player);
			GreenLanternBattery.onAbilityUsed(player);
			LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.BEAM_RANGE);
			if (target != null) {
				AbilityHelpers.hurt(player, target, GreenLanternConfig.BEAM_DAMAGE_PER_TICK);
			}
		}
		ServerLevel level = player.serverLevel();
		Vec3 end = AbilityHelpers.aimPoint(player, GreenLanternConfig.BEAM_RANGE);
		AbilityHelpers.line(level, player.getEyePosition(), end, ParticleTypes.HAPPY_VILLAGER, 3.0);
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
		GreenLanternEnergy.markAbilityUsed(player);
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, FIST_CD, GreenLanternConfig.FIST_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		Vec3 aim = AbilityHelpers.aimPoint(player, GreenLanternConfig.FIST_RANGE);
		AbilityHelpers.line(level, player.getEyePosition(), aim, ParticleTypes.END_ROD, 5.0);
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.FIST_RANGE);
		if (target != null) {
			AbilityHelpers.hurt(player, target, GreenLanternConfig.FIST_DAMAGE);
			AbilityHelpers.knockbackFrom(target, player.position(), GreenLanternConfig.FIST_KNOCKBACK);
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.END_ROD, 16, 0.5);
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
		GreenLanternEnergy.markAbilityUsed(player);
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, HAMMER_CD, GreenLanternConfig.HAMMER_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		Vec3 center = player.position().add(player.getLookAngle().scale(2.0));
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, center, GreenLanternConfig.HAMMER_RADIUS)) {
			double dist = e.position().distanceTo(center);
			boolean isBoss = e.getMaxHealth() >= GreenLanternMastery.BOSS_MAX_HEALTH_THRESHOLD;
			float damage = dist <= 1.5 ? GreenLanternConfig.HAMMER_CENTER_DAMAGE : GreenLanternConfig.HAMMER_OUTER_DAMAGE;
			AbilityHelpers.hurt(player, e, damage);
			double knockback = isBoss ? 0.25 : 1.0; // bosses: full damage, only 25% normal knockback
			AbilityHelpers.knockbackFrom(e, center, knockback);
			e.setDeltaMovement(e.getDeltaMovement().x, GreenLanternConfig.HAMMER_KNOCKUP, e.getDeltaMovement().z);
			e.hurtMarked = true;
		}
		AbilityHelpers.burst(level, center, ParticleTypes.END_ROD, 40, GreenLanternConfig.HAMMER_RADIUS * 0.6);
		level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 30, GreenLanternConfig.HAMMER_RADIUS,
				0.2, GreenLanternConfig.HAMMER_RADIUS, 0.05);
		AbilityHelpers.sound(player, SoundEvents.ANVIL_LAND, 1.0f, 0.6f);
	}
}
