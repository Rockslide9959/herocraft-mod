package com.projecthero.mod.greenlantern;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Ring Grapple (X, tap) -- v0.11.5. Fires a hard-light line at whatever solid block is under the
 * crosshair and yanks the player toward it, capped at {@link GreenLanternConfig#GRAPPLE_MAX_SPEED}.
 * Replaces the flight toggle that used to live on X (Ring Flight moved to a double-tap of the vanilla
 * jump key -- see {@code ProjectHeroModClient#handleDoubleJump} -- since a hero-tier power point-and-go
 * mobility tool and its own flight toggle no longer needed to share one key).
 */
public final class GreenLanternGrapple {
	private static final String GRAPPLE_CD = "ring_grapple";
	/** Lantern-Corps green, matching every other hard-light effect's dust colour in this power. */
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.5f);

	private GreenLanternGrapple() {
	}

	public static void grapple(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, GRAPPLE_CD)) {
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, GreenLanternConfig.GRAPPLE_RANGE);
		if (hit.getType() == HitResult.Type.MISS) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.invalid_target");
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.GRAPPLE_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, GRAPPLE_CD, GreenLanternConfig.GRAPPLE_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		Vec3 target = hit.getLocation();
		Vec3 eye = player.getEyePosition();
		AbilityHelpers.line(level, eye, target, GREEN_DUST, 4.0);

		Vec3 pull = target.subtract(eye);
		double dist = Math.max(1.0, pull.length() - 1.5); // stop just short of the surface
		Vec3 velocity = pull.normalize().scale(Math.min(GreenLanternConfig.GRAPPLE_MAX_SPEED, dist * 0.3));
		AbilityHelpers.launchSelf(player, velocity);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7f, 1.3f);
	}
}
