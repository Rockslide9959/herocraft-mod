package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
		// v0.14.3: a real streak of hard light from the ring (was a line of villager sparkles) + the shot animation
		GreenLanternConstructAttacks.boltStreak(player, handOrigin(player), endPoint, 1.0f, 6);
		GreenLanternVisuals.anim(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_BOLT);
		level.sendParticles(GREEN_DUST, endPoint.x, endPoint.y, endPoint.z, 6, 0.15, 0.15, 0.15, 0.05);
		if (target != null) {
			AbilityHelpers.hurt(player, target, GreenLanternConfig.BOLT_DAMAGE * GreenLanternOath.multiplier(player));
			AbilityHelpers.knockbackFrom(target, player.position(), GreenLanternConfig.BOLT_KNOCKBACK);
		}
		AbilityHelpers.sound(player, SoundEvents.GUARDIAN_ATTACK, 0.5f, 1.8f);
		AbilityHelpers.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
	}

	// ---------------- Blast Wave (Shift+R, v0.15.15) ----------------

	public static final String BLAST_CD = "blast_wave";

	/**
	 * v0.15.15, user request: Shift+R sends an expanding wave of green energy out over a forward cone that throws enemies
	 * back -- {@link GreenLanternConfig#BLAST_WAVE_DAMAGE}, Slowness III for 4 s, a 7 s cooldown and
	 * {@link GreenLanternConfig#BLAST_WAVE_COST} Ring Charge. Squad / HeroTargets rules apply (the wave's hits go through
	 * {@code GreenLanternConstructAttacks#canHit}).
	 */
	public static void blastWave(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, BLAST_CD)) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.BLAST_WAVE_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, BLAST_CD, GreenLanternConfig.BLAST_WAVE_COOLDOWN_TICKS);
		GreenLanternConstructAttacks.blastWave(player, GreenLanternConfig.BLAST_WAVE_DAMAGE * GreenLanternOath.multiplier(player));
		GreenLanternVisuals.anim(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_BLAST);
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
		GreenLanternVisuals.channel(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.CH_BEAM, true);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, movementPenaltyAmplifier(), false, false, false));
		AbilityHelpers.sound(player, SoundEvents.GUARDIAN_ATTACK, 0.5f, 0.7f);
	}

	public static void beamStop(ServerPlayer player) {
		BEAM_CHANNEL.remove(player.getUUID());
		GreenLanternVisuals.channel(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.CH_BEAM, false);
		GreenLanternConstructAttacks.beamVisualEnd(player);
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
				AbilityHelpers.hurt(player, target, GreenLanternConfig.BEAM_DAMAGE_PER_TICK * GreenLanternOath.multiplier(player));
			}
			// A periodic zap on the same cadence as the damage tick -- not a true seamless loop, but
			// enough to read as a sustained energy weapon rather than silent channelling.
			AbilityHelpers.sound(player, SoundEvents.GUARDIAN_ATTACK, 0.35f, 0.9f);
		}
		ServerLevel level = player.serverLevel();
		Vec3 end = AbilityHelpers.aimPoint(player, GreenLanternConfig.BEAM_RANGE);
		// v0.14.3: a solid beam of hard light (was a line of villager sparkles)
		GreenLanternConstructAttacks.beamVisual(player, handOrigin(player), end);
		if (ticks % 2 == 0) {
			level.sendParticles(GREEN_DUST, end.x, end.y, end.z, 2, 0.1, 0.1, 0.1, 0.03);
		}
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

		// v0.14.3: a giant hard-light fist flies from the ring and smashes the first thing in its path (was an
		// instant hit-scan with a particle cube)
		GreenLanternConstructAttacks.launchFist(player, GreenLanternConfig.FIST_DAMAGE * GreenLanternOath.multiplier(player));
		GreenLanternVisuals.anim(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_FIST);
		AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.7f);
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

		// v0.14.3: a giant war hammer forms over your head and comes down on the ground ahead; the blow lands when it
		// hits (GreenLanternConstructAttacks#tickHammer) instead of the instant the key was pressed
		GreenLanternConstructAttacks.swingHammer(player, GreenLanternConfig.HAMMER_CENTER_DAMAGE * GreenLanternOath.multiplier(player));
		GreenLanternVisuals.anim(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_HAMMER);
	}
}
