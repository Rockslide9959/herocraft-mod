package com.projecthero.mod.maxsteel;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.maxsteel.entity.TurboBoltEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 1 -- Turbo Blast. A fast, thick cyan T.U.R.B.O. bolt. v0.9.2: it is now a real flying
 * projectile ({@link TurboBoltEntity}) rather than an instant hitscan -- it still stays
 * server-authoritative (only this class spawns it, only the entity resolves the hit), just with a
 * visible travelling bolt and a slightly-larger-than-a-snowball hitbox. A tap fires the base bolt;
 * holding scales the damage and energy up to a full charge that adds a small entity-only impact burst.
 * Terrain is never damaged.
 *
 * <p>Usable in Base / Strength / Speed / Flight. Firing from Stealth breaks Stealth first.
 */
public final class MaxSteelBlast {
	public static final String ABILITY = "turbo_blast";

	private MaxSteelBlast() {
	}

	/** A quick tap: the base bolt. */
	public static void tap(ServerPlayer player) {
		fire(player, 0f);
	}

	/** Released after a hold: {@code chargeFrac} 0..1 scales damage 10..22 and cost 4..16. */
	public static void charged(ServerPlayer player, float chargeFrac) {
		fire(player, Math.max(0f, Math.min(1f, chargeFrac)));
	}

	private static void fire(ServerPlayer player, float charge) {
		if (!MaxSteel.isTransformed(player)) {
			return;
		}
		if (!MaxSteel.abilityReady(player, ABILITY)) {
			return;
		}
		float damage = lerp(MaxSteelConfig.BLAST_DAMAGE, MaxSteelConfig.CHARGED_BLAST_MAX_DAMAGE, charge);
		float cost = lerp(MaxSteelConfig.BLAST_COST, MaxSteelConfig.CHARGED_BLAST_MAX_COST, charge);
		if (!MaxSteelEnergy.spend(player, cost)) {
			MaxSteelFeedback.noEnergy(player, cost);
			return;
		}

		// firing is an offensive action -> break stealth
		MaxSteelStealth.onOffensiveAction(player);
		MaxSteelEnergy.markCombat(player);

		ServerLevel level = player.serverLevel();
		Vec3 look = player.getLookAngle();
		// v0.10.18: fired from the hand, not the face -- spawning at the eye put the muzzle flash and
		// bolt directly in the player's own view, effectively blinding them on every shot.
		Vec3 right = rightOf(player, look);
		Vec3 spawn = player.getEyePosition().add(look.scale(0.8)).add(right.scale(0.4)).add(0, -0.35, 0);

		TurboBoltEntity bolt = new TurboBoltEntity(level, player, look.scale(MaxSteelConfig.BLAST_PROJECTILE_SPEED))
				.configure(damage, charge);
		bolt.setPos(spawn.x, spawn.y, spawn.z);
		level.addFreshEntity(bolt);

		// muzzle flash on the hand
		level.sendParticles(ParticleTypes.END_ROD, spawn.x, spawn.y, spawn.z, 6 + (int) (charge * 8),
				0.08, 0.08, 0.08, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spawn.x, spawn.y, spawn.z, 8, 0.12, 0.12, 0.12, 0.06);

		// v0.10.19: a real blast report instead of the "ding" NOTE_BLOCK_BIT gave -- a punchy burst layered
		// with a low thump, pitched down further for a charged shot.
		float pitch = charge > 0.4f ? 0.8f : 1.1f;
		AbilityHelpers.sound(player, SoundEvents.WIND_CHARGE_BURST, 0.8f, pitch);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f, pitch + 0.2f);

		MaxSteel.triggerCooldown(player, ABILITY, MaxSteelConfig.BLAST_COOLDOWN_TICKS);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/**
	 * A stable "player's right" unit vector for placing the muzzle -- same fallback-at-the-poles trick
	 * as {@code IronManAbilities.rightOf}: {@code look.cross(UP)} collapses to zero looking straight up
	 * or down, so fall back to body yaw there instead of snapping the spawn point back onto the face.
	 */
	private static Vec3 rightOf(ServerPlayer player, Vec3 look) {
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			double yaw = Math.toRadians(player.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		}
		return right.normalize();
	}
}
